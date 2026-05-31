package it.schedina.service;

import it.schedina.dto.ScommessaDto;
import it.schedina.entity.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Scommesse di FINE CAMPIONATO (catalogo + risoluzione manuale, scoring delle Giocate)
 * e scommesse DI PARTITA (GiocataPartita: piazzamento + risoluzione automatica dal punteggio,
 * Primo marcatore manuale).
 */
@ApplicationScoped
public class ScommessaResolutionService {

    // ---- Fine campionato (catalogo) ----

    @Transactional
    public Scommessa create(ScommessaDto.ScommessaRequest req) {
        if (!Scommessa.isSeasonMarket(req.market())) {
            throw bad("Mercato non valido per una scommessa di fine campionato");
        }
        List<ScommessaDto.OptionInput> opts = req.options() != null ? req.options() : new ArrayList<>();
        if (opts.isEmpty()) throw bad("La scommessa richiede almeno 1 opzione");

        Scommessa b = new Scommessa();
        b.label = req.label();
        b.market = req.market();
        b.seasonId = req.seasonId();
        b.tournamentId = req.tournamentId();
        b.leagueId = req.leagueId();
        b.persist();

        int order = 0;
        for (var o : opts) {
            BetOption op = new BetOption();
            op.betId = b.id;
            op.ref = o.ref();
            op.label = (o.label() != null && !o.label().isBlank()) ? o.label() : o.ref();
            op.displayOrder = o.displayOrder() != null ? o.displayOrder() : order;
            op.persist();
            order++;
        }
        return b;
    }

    @Transactional
    public Scommessa resolveManual(Long betId, String winningRef) {
        Scommessa b = Scommessa.findById(betId);
        if (b == null) throw notFound();
        if (!BetOption.existsForBet(betId, winningRef)) {
            throw bad("Il risultato '" + winningRef + "' non è un'opzione di questa scommessa");
        }
        b.officialResultRef = winningRef;
        b.status = Scommessa.Status.RESOLVED;
        b.resolvedAt = LocalDateTime.now();
        b.persist();
        for (Giocata g : Giocata.findByScommessa(b.id)) {
            g.isCorrect = winningRef.equals(g.choiceRef);
            g.persist();
        }
        return b;
    }

    @Transactional
    public Scommessa unresolve(Long betId) {
        Scommessa b = Scommessa.findById(betId);
        if (b == null) throw notFound();
        b.officialResultRef = null;
        b.status = Scommessa.Status.OPEN;
        b.resolvedAt = null;
        b.persist();
        for (Giocata g : Giocata.findByScommessa(betId)) { g.isCorrect = null; g.persist(); }
        return b;
    }

    @Transactional
    public Scommessa voidBet(Long betId) {
        Scommessa b = Scommessa.findById(betId);
        if (b == null) throw notFound();
        b.status = Scommessa.Status.VOID;
        b.officialResultRef = null;
        b.resolvedAt = LocalDateTime.now();
        b.persist();
        for (Giocata g : Giocata.findByScommessa(betId)) { g.isCorrect = null; g.persist(); }
        return b;
    }

    @Transactional
    public Giocata placeGiocata(Long userId, Long scommessaId, String choiceRef) {
        Scommessa b = Scommessa.findById(scommessaId);
        if (b == null) throw notFound();
        if (b.status != Scommessa.Status.OPEN) throw bad("Scommessa non più aperta alle giocate");
        if (!BetOption.existsForBet(scommessaId, choiceRef)) throw bad("Scelta non valida: " + choiceRef);
        Giocata g = Giocata.findByUserAndScommessa(userId, scommessaId);
        if (g == null) { g = new Giocata(); g.userId = userId; g.scommessaId = scommessaId; }
        g.choiceRef = choiceRef;
        g.persist();
        return g;
    }

    // ---- Di partita (GiocataPartita) ----

    @Transactional
    public GiocataPartita placeGiocataPartita(Long userId, Long matchId, Scommessa.Market market, String prediction) {
        Match m = Match.findById(matchId);
        if (m == null) throw notFound();
        if (!GiocataPartita.isMatchMarket(market)) throw bad("Mercato non valido per una scommessa di partita");
        if (m.hasScore() || m.status == Match.Status.VALIDATED) throw bad("Partita non più disponibile alle scommesse");
        if (prediction == null || prediction.isBlank()) throw bad("Previsione mancante");
        prediction = prediction.trim();

        switch (market) {
            case GOAL_NOGOAL -> {
                if (!prediction.equals("GOAL") && !prediction.equals("NOGOAL")) throw bad("Previsione non valida (GOAL/NOGOAL)");
            }
            case WINNER -> {
                if (!prediction.equals(String.valueOf(m.homeTeamId)) && !prediction.equals(String.valueOf(m.awayTeamId)))
                    throw bad("Il vincitore deve essere una delle due squadre");
            }
            case EXACT_SCORE -> {
                if (!prediction.matches("\\d{1,2}-\\d{1,2}")) throw bad("Risultato esatto non valido (es. 2-1)");
            }
            case FIRST_SCORER -> {
                if (!prediction.equals(Match.OWN_GOAL_REF)) { // "OWN_GOAL" = autogol, ammesso
                    Player p = Player.findById(parseLong(prediction));
                    if (p == null || (!m.homeTeamId.equals(p.teamId) && !m.awayTeamId.equals(p.teamId)))
                        throw bad("Il marcatore deve essere un giocatore delle due squadre o l'autogol");
                }
            }
            default -> throw bad("Mercato non valido");
        }

        GiocataPartita g = GiocataPartita.findByUserMatchMarket(userId, matchId, market);
        if (g == null) { g = new GiocataPartita(); g.userId = userId; g.matchId = matchId; g.market = market; }
        g.prediction = prediction;
        g.isCorrect = null;
        g.persist();
        return g;
    }

    /** Risolve le giocate di partita risolvibili dal punteggio (GOAL_NOGOAL, WINNER, EXACT_SCORE)
     *  e — se impostato il primo marcatore — anche FIRST_SCORER. Idempotente. */
    @Transactional
    public int resolveMatchBets(Match m) {
        if (!m.hasScore()) return 0;
        String gng = (m.homeScore > 0 && m.awayScore > 0) ? "GOAL" : "NOGOAL";
        Long winner = m.winnerTeamId();
        String exact = m.homeScore + "-" + m.awayScore;

        int n = 0;
        for (GiocataPartita g : GiocataPartita.findByMatch(m.id)) {
            Boolean correct = switch (g.market) {
                case GOAL_NOGOAL -> g.prediction.equals(gng);
                case WINNER -> winner != null && g.prediction.equals(String.valueOf(winner));
                case EXACT_SCORE -> g.prediction.equals(exact);
                case FIRST_SCORER -> {
                    String scorerRef = m.firstScorerOwnGoal ? Match.OWN_GOAL_REF
                            : (m.firstScorerPlayerId != null ? String.valueOf(m.firstScorerPlayerId) : null);
                    yield scorerRef != null ? g.prediction.equals(scorerRef) : null;
                }
                default -> null;
            };
            if (correct != null) { g.isCorrect = correct; g.persist(); n++; }
        }
        return n;
    }

    private long parseLong(String s) {
        try { return Long.parseLong(s); } catch (Exception e) { return -1L; }
    }

    private WebApplicationException bad(String msg) {
        return new WebApplicationException(Response.status(400).entity(Map.of("error", msg)).build());
    }

    private WebApplicationException notFound() {
        return new WebApplicationException(Response.status(404).entity(Map.of("error", "Risorsa non trovata")).build());
    }
}
