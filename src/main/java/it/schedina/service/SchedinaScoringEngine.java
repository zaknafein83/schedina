package it.schedina.service;

import it.schedina.dto.SchedinaDto;
import it.schedina.entity.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Motore unico di punteggio delle schedine (sostituisce CouponEngine + SeasonCouponEngine).
 * Punteggio uniforme (1 punto per selezione corretta), scelta singola, vincita a match esatto
 * sulle soglie. Il process() è idempotente e multi-step: usa solo le scommesse risolte.
 */
@ApplicationScoped
public class SchedinaScoringEngine {

    @Transactional
    public Schedina createSchedina(Long userId, SchedinaDto.CreateRequest req) {
        LocalDateTime now = LocalDateTime.now();

        Concorso concorso = Concorso.findById(req.concorsoId());
        if (concorso == null) throw bad("Concorso non trovato");
        if (concorso.status != Concorso.Status.OPEN) throw bad("Il concorso non è aperto alle giocate");
        if (now.isAfter(concorso.closeAt)) throw bad("Termine di chiusura del concorso superato");

        Rule rule = concorso.rule();
        if (rule == null) throw bad("Regola del concorso non trovata");

        if (rule.maxSchedinePerUser != null) {
            long existing = Schedina.countActiveByUserAndConcorso(userId, req.concorsoId());
            if (existing >= rule.maxSchedinePerUser) {
                throw bad("Numero massimo di schedine per utente raggiunto (" + rule.maxSchedinePerUser + ")");
            }
        }

        List<Scommessa> bets = Scommessa.findByConcorso(req.concorsoId());
        Map<Long, Scommessa> betById = new HashMap<>();
        for (Scommessa b : bets) betById.put(b.id, b);

        Set<Long> seen = new HashSet<>();
        for (var sel : req.selezioni()) {
            Scommessa b = betById.get(sel.betId());
            if (b == null) throw bad("La scommessa " + sel.betId() + " non appartiene a questo concorso");
            if (!seen.add(sel.betId())) throw bad("Scommessa duplicata: " + sel.betId());
            validateChoice(b, sel.choiceRef());
        }

        if (rule.fullCompletionRequired) {
            if (seen.size() != bets.size()) {
                List<Long> missing = bets.stream().map(b -> b.id).filter(id -> !seen.contains(id)).toList();
                throw bad("Schedina incompleta, mancano le scommesse: " + missing);
            }
        } else if (req.selezioni().size() != rule.requiredBets) {
            throw bad("La schedina deve coprire esattamente " + rule.requiredBets +
                    " scommesse, trovate " + req.selezioni().size());
        }

        Schedina s = new Schedina();
        s.userId = userId;
        s.concorsoId = req.concorsoId();
        s.persist();

        for (var sel : req.selezioni()) {
            Selezione se = new Selezione();
            se.schedinaId = s.id;
            se.betId = sel.betId();
            se.choiceRef = sel.choiceRef();
            se.persist();
        }
        return s;
    }

    @Transactional
    public Schedina confirm(Schedina s) {
        if (s.status != Schedina.Status.DRAFT) {
            throw bad("Solo le schedine in bozza possono essere confermate");
        }
        Concorso c = Concorso.findById(s.concorsoId);
        if (LocalDateTime.now().isAfter(c.closeAt)) {
            throw bad("Termine di chiusura del concorso superato");
        }
        s.status = Schedina.Status.CONFIRMED;
        s.confirmedAt = LocalDateTime.now();
        s.persist();
        return s;
    }

    /**
     * Idempotente. Ricalcola correctCount/isCorrect usando solo le scommesse RESOLVED;
     * le selezioni di scommesse ancora OPEN restano isCorrect=null. Promuove il concorso a
     * PROCESSED solo quando TUTTE le scommesse sono risolte o annullate.
     */
    @Transactional
    public Map<String, Object> process(Concorso concorso) {
        Rule rule = concorso.rule();
        List<Scommessa> bets = Scommessa.findByConcorso(concorso.id);
        long settled = bets.stream()
                .filter(b -> b.status == Scommessa.Status.RESOLVED || b.status == Scommessa.Status.VOID)
                .count();
        boolean allSettled = settled == bets.size() && !bets.isEmpty();

        Map<Long, String> officialByBet = new HashMap<>();
        for (Scommessa b : bets) {
            if (b.status == Scommessa.Status.RESOLVED && b.officialResultRef != null) {
                officialByBet.put(b.id, b.officialResultRef);
            }
        }

        List<Schedina> schedine = Schedina.<Schedina>find(
                "concorsoId = ?1 and status in ?2",
                concorso.id,
                List.of(Schedina.Status.CONFIRMED, Schedina.Status.PROCESSED,
                        Schedina.Status.WINNING, Schedina.Status.NOT_WINNING)
        ).list();

        int winners = 0;
        for (Schedina s : schedine) {
            int correct = 0;
            for (Selezione sel : Selezione.findBySchedina(s.id)) {
                String official = officialByBet.get(sel.betId);
                if (official == null) {
                    sel.isCorrect = null;
                } else {
                    sel.isCorrect = official.equals(sel.choiceRef);
                    if (Boolean.TRUE.equals(sel.isCorrect)) correct++;
                }
                sel.persist();
            }
            s.correctCount = correct;
            if (allSettled) {
                s.isWinner = rule.winningThresholds.contains(correct);
                s.status = s.isWinner ? Schedina.Status.WINNING : Schedina.Status.NOT_WINNING;
                if (Boolean.TRUE.equals(s.isWinner)) winners++;
            } else {
                s.status = Schedina.Status.PROCESSED;
            }
            s.persist();
        }

        if (allSettled) {
            concorso.status = Concorso.Status.PROCESSED;
            concorso.persist();
        }

        return Map.ofEntries(
                Map.entry("concorsoId", concorso.id),
                Map.entry("totalBets", bets.size()),
                Map.entry("settledBets", (int) settled),
                Map.entry("allSettled", allSettled),
                Map.entry("schedineProcessed", schedine.size()),
                Map.entry("winners", winners),
                Map.entry("status", concorso.status.name())
        );
    }

    private void validateChoice(Scommessa bet, String choiceRef) {
        if (choiceRef == null || choiceRef.isBlank()) throw bad("Scelta vuota per la scommessa " + bet.id);
        if (!BetOption.existsForBet(bet.id, choiceRef)) {
            throw bad("Scelta non valida per la scommessa " + bet.id + ": " + choiceRef);
        }
    }

    private WebApplicationException bad(String msg) {
        return new WebApplicationException(
                Response.status(400).entity(Map.of("error", msg)).build());
    }
}
