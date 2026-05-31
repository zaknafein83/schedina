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

    /** Etichetta italiana del mercato di fine campionato. */
    public static String marketLabelIt(Scommessa.Market m) {
        return switch (m) {
            case TOP_SCORER -> "Capocannoniere";
            case TOP_ASSIST -> "Miglior assist";
            case BEST_GOALKEEPER -> "Miglior portiere";
            case CLEAN_SHEET -> "Più clean sheet";
            case MOST_GOALS_FOR -> "Più gol fatti";
            case LEAST_GOALS_AGAINST -> "Meno gol subiti";
            default -> m.name();
        };
    }

    /** Trova (o crea) la scommessa di fine campionato per stagione+lega+mercato. */
    @Transactional
    public Scommessa findOrCreateSeasonBet(Long seasonId, Long leagueId, Scommessa.Market market) {
        Scommessa b = Scommessa.<Scommessa>find("seasonId = ?1 and leagueId = ?2 and market = ?3", seasonId, leagueId, market).firstResult();
        if (b == null) {
            League l = League.findById(leagueId);
            b = new Scommessa();
            b.market = market;
            b.seasonId = seasonId;
            b.leagueId = leagueId;
            b.label = marketLabelIt(market) + " — " + (l != null ? l.name : "?");
            b.persist();
        }
        return b;
    }

    /** Giocata di fine campionato self-service: l'utente sceglie lega+mercato+bersaglio. */
    @Transactional
    public Giocata placeGiocataStagione(Long userId, Long seasonId, Long leagueId, Scommessa.Market market, String prediction) {
        if (!Scommessa.isSeasonMarket(market)) throw bad("Mercato non valido per una scommessa di fine campionato");
        if (leagueId == null || League.findById(leagueId) == null) throw bad("Lega non valida");
        Long sid = seasonId != null ? seasonId : currentSeasonId();
        if (sid == null) throw bad("Nessuna stagione disponibile");
        if (prediction == null || prediction.isBlank()) throw bad("Previsione mancante");
        String ref = prediction.trim();
        validateSeasonTarget(leagueId, market, ref);

        Scommessa b = findOrCreateSeasonBet(sid, leagueId, market);
        if (b.status == Scommessa.Status.VOID) throw bad("Scommessa annullata");
        Giocata g = Giocata.findByUserAndScommessa(userId, b.id);
        if (g == null) { g = new Giocata(); g.userId = userId; g.scommessaId = b.id; }
        g.choiceRef = ref;
        g.isCorrect = (b.status == Scommessa.Status.RESOLVED && b.officialResultRef != null)
                ? b.officialResultRef.equals(ref) : null;
        g.persist();
        return g;
    }

    /** L'admin dichiara il risultato ufficiale per stagione+lega+mercato (crea la scommessa se serve) e risolve le giocate. */
    @Transactional
    public Scommessa setSeasonResult(Long seasonId, Long leagueId, Scommessa.Market market, String officialRef) {
        if (!Scommessa.isSeasonMarket(market)) throw bad("Mercato non valido per una scommessa di fine campionato");
        Long sid = seasonId != null ? seasonId : currentSeasonId();
        if (sid == null) throw bad("Nessuna stagione disponibile");
        Scommessa b = findOrCreateSeasonBet(sid, leagueId, market);
        return resolveManual(b.id, officialRef);
    }

    private Long currentSeasonId() {
        Season s = Season.findCurrent();
        return s != null ? s.id : null;
    }

    /** Verifica che il ref sia un bersaglio valido (giocatore/squadra della lega; portiere per i mercati GK). */
    private void validateSeasonTarget(Long leagueId, Scommessa.Market market, String ref) {
        Scommessa.TargetKind kind = Scommessa.targetKindOf(market);
        if (kind == Scommessa.TargetKind.PLAYER) {
            Player p = Player.findById(parseLongOrNull(ref));
            if (p == null) throw bad("Giocatore non trovato");
            Team t = p.teamId != null ? Team.findById(p.teamId) : null;
            if (t == null || !leagueId.equals(t.leagueId)) throw bad("Il giocatore non appartiene alla lega scelta");
            if (Scommessa.isGoalkeeperMarket(market) && p.role != Player.Role.GK) throw bad("Per questo mercato serve un portiere");
        } else if (kind == Scommessa.TargetKind.TEAM) {
            Team t = Team.findById(parseLongOrNull(ref));
            if (t == null || !leagueId.equals(t.leagueId)) throw bad("La squadra non appartiene alla lega scelta");
        }
    }

    private Long parseLongOrNull(String s) {
        try { return Long.parseLong(s); } catch (Exception e) { return null; }
    }

    @Transactional
    public Scommessa resolveManual(Long betId, String winningRef) {
        Scommessa b = Scommessa.findById(betId);
        if (b == null) throw notFound();
        if (winningRef == null || winningRef.isBlank()) throw bad("Risultato mancante");
        if (b.leagueId != null) validateSeasonTarget(b.leagueId, b.market, winningRef.trim());
        b.officialResultRef = winningRef.trim();
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
