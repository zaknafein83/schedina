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
 * Motore della schedina del Concorso: per ogni partita selezionata 1X2 + Under/Over
 * (1 punto ciascuno), vincita a soglia esatta. process() idempotente, calcola gli esiti
 * dal punteggio delle partite. Le soglie vengono dalla regola del concorso (fallback locale).
 */
@ApplicationScoped
public class SchedinaScoringEngine {

    private static final Set<String> VALID_1X2 = Set.of("1", "X", "2");
    private static final Set<String> VALID_UO = Set.of("U", "O");

    @Transactional
    public Schedina createSchedina(Long userId, SchedinaDto.CreateRequest req) {
        LocalDateTime now = LocalDateTime.now();
        Concorso c = Concorso.findById(req.concorsoId());
        if (c == null) throw bad("Concorso non trovato");
        if (c.status != Concorso.Status.OPEN) throw bad("Il concorso non è aperto alle giocate");
        if (now.isAfter(c.closeAt)) throw bad("Termine di chiusura del concorso superato");
        if (Schedina.findActiveByUserAndConcorso(userId, c.id) != null) {
            throw bad("Hai già una schedina per questo concorso");
        }

        List<Match> matches = Match.findByConcorso(c.id);
        if (matches.isEmpty()) throw bad("Il concorso non ha partite selezionate");
        Map<Long, Match> byId = new HashMap<>();
        for (Match m : matches) byId.put(m.id, m);

        Set<Long> seen = new HashSet<>();
        for (var p : req.pronostici()) {
            Match m = byId.get(p.matchId());
            if (m == null) throw bad("La partita " + p.matchId() + " non appartiene a questo concorso");
            if (!seen.add(p.matchId())) throw bad("Partita duplicata: " + p.matchId());
            if (!VALID_1X2.contains(p.choice1x2())) throw bad("Esito 1X2 non valido per la partita " + p.matchId());
            if (!VALID_UO.contains(p.choiceUo().toUpperCase())) throw bad("Under/Over non valido per la partita " + p.matchId());
        }
        if (seen.size() != matches.size()) {
            List<Long> missing = matches.stream().map(m -> m.id).filter(x -> !seen.contains(x)).toList();
            throw bad("Schedina incompleta, mancano le partite: " + missing);
        }

        Schedina s = new Schedina();
        s.userId = userId;
        s.concorsoId = c.id;
        s.persist();
        for (var p : req.pronostici()) {
            Selezione sel = new Selezione();
            sel.schedinaId = s.id;
            sel.matchId = p.matchId();
            sel.choice1x2 = p.choice1x2().toUpperCase();
            sel.choiceUo = p.choiceUo().toUpperCase();
            sel.persist();
        }
        return s;
    }

    @Transactional
    public Schedina confirm(Schedina s) {
        if (s.status != Schedina.Status.DRAFT) throw bad("Solo le schedine in bozza possono essere confermate");
        Concorso c = Concorso.findById(s.concorsoId);
        if (LocalDateTime.now().isAfter(c.closeAt)) throw bad("Termine di chiusura del concorso superato");
        s.status = Schedina.Status.CONFIRMED;
        s.confirmedAt = LocalDateTime.now();
        s.persist();
        return s;
    }

    /** Idempotente. Calcola 1X2 e U/O dal punteggio delle partite; vincita quando tutte hanno il risultato. */
    @Transactional
    public Map<String, Object> process(Concorso c) {
        List<Match> matches = Match.findByConcorso(c.id);
        long scored = matches.stream().filter(Match::hasScore).count();
        boolean allScored = scored == matches.size() && !matches.isEmpty();
        Map<Long, Match> byId = new HashMap<>();
        for (Match m : matches) byId.put(m.id, m);

        List<Schedina> schedine = Schedina.<Schedina>find(
                "concorsoId = ?1 and status in ?2", c.id,
                List.of(Schedina.Status.CONFIRMED, Schedina.Status.PROCESSED,
                        Schedina.Status.WINNING, Schedina.Status.NOT_WINNING)).list();

        // Soglie vincenti: dalla regola se assegnata, altrimenti quelle locali (legacy/fallback).
        Rule rule = c.ruleId != null ? Rule.findById(c.ruleId) : null;
        List<Integer> thresholds = rule != null ? rule.winningThresholds : c.winningThresholds;

        int winners = 0;
        for (Schedina s : schedine) {
            int correct1x2 = 0;
            int correctUo = 0;
            for (Selezione sel : Selezione.findBySchedina(s.id)) {
                Match m = byId.get(sel.matchId);
                String r1 = m != null ? m.result1x2() : null;
                String ru = m != null ? m.resultUO() : null;
                sel.correct1x2 = r1 == null ? null : r1.equals(sel.choice1x2);
                sel.correctUo = ru == null ? null : ru.equals(sel.choiceUo);
                if (Boolean.TRUE.equals(sel.correct1x2)) correct1x2++;
                if (Boolean.TRUE.equals(sel.correctUo)) correctUo++;
                sel.persist();
            }
            // Due giochi separati: Totocalcio (1X2) e Under/Over, stesse soglie valutate a parte.
            s.correct1x2Count = correct1x2;
            s.correctUoCount = correctUo;
            s.correctCount = correct1x2 + correctUo; // legacy/aggregato
            if (allScored) {
                s.isWinner1x2 = thresholds.contains(correct1x2);
                s.isWinnerUo = thresholds.contains(correctUo);
                // Premio della soglia centrata in ciascun gioco (dal concorso, separati per gioco).
                s.prize1x2 = Boolean.TRUE.equals(s.isWinner1x2) ? c.prize1x2For(correct1x2) : 0L;
                s.prizeUo = Boolean.TRUE.equals(s.isWinnerUo) ? c.prizeUoFor(correctUo) : 0L;
                s.isWinner = Boolean.TRUE.equals(s.isWinner1x2) || Boolean.TRUE.equals(s.isWinnerUo);
                s.status = s.isWinner ? Schedina.Status.WINNING : Schedina.Status.NOT_WINNING;
                if (Boolean.TRUE.equals(s.isWinner)) winners++;
            } else {
                s.status = Schedina.Status.PROCESSED;
            }
            s.persist();
        }
        if (allScored) {
            c.status = Concorso.Status.PROCESSED;
            c.persist();
        }
        return Map.ofEntries(
                Map.entry("concorsoId", c.id),
                Map.entry("matches", matches.size()),
                Map.entry("matchesScored", (int) scored),
                Map.entry("allScored", allScored),
                Map.entry("schedineProcessed", schedine.size()),
                Map.entry("winners", winners),
                Map.entry("status", c.status.name())
        );
    }

    private WebApplicationException bad(String msg) {
        return new WebApplicationException(Response.status(400).entity(Map.of("error", msg)).build());
    }
}
