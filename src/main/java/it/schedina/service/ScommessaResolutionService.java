package it.schedina.service;

import it.schedina.dto.ScommessaDto;
import it.schedina.entity.BetOption;
import it.schedina.entity.Giocata;
import it.schedina.entity.Match;
import it.schedina.entity.Scommessa;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Catalogo Scommesse extra (fine stagione / di giornata) + giocate degli utenti.
 * Creazione con opzioni, risoluzione (manuale o AUTO da punteggio) con scoring delle Giocate.
 */
@ApplicationScoped
public class ScommessaResolutionService {

    @Transactional
    public Scommessa create(ScommessaDto.ScommessaRequest req) {
        Scommessa b = new Scommessa();
        b.scope = req.scope();
        b.label = req.label();
        b.market = req.market();
        b.seasonId = req.seasonId();
        b.giornataId = req.giornataId();
        b.matchId = req.matchId();
        b.leagueId = req.leagueId();
        b.tournamentId = req.tournamentId();
        b.resolutionMode = Scommessa.defaultResolution(req.market());
        b.persist();

        List<ScommessaDto.OptionInput> opts = standardOrProvided(b, req.options());
        if (opts.isEmpty()) throw bad("La scommessa richiede almeno un'opzione selezionabile");
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

    private List<ScommessaDto.OptionInput> standardOrProvided(Scommessa b, List<ScommessaDto.OptionInput> provided) {
        if (b.market == Scommessa.Market.GOAL_NOGOAL && (provided == null || provided.isEmpty())) {
            return List.of(new ScommessaDto.OptionInput("GOAL", "Gol", null),
                    new ScommessaDto.OptionInput("NOGOAL", "No gol", null));
        }
        return provided != null ? provided : new ArrayList<>();
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
        scoreGiocate(b);
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

    /** Risolve automaticamente le scommesse AUTO (Gol/No gol) legate alla partita, dal punteggio. */
    @Transactional
    public int resolveFromMatch(Match m) {
        if (!m.hasScore()) return 0;
        int resolved = 0;
        for (Scommessa b : Scommessa.findByMatch(m.id)) {
            if (b.resolutionMode != Scommessa.ResolutionMode.AUTO || b.status != Scommessa.Status.OPEN) continue;
            String ref = b.market == Scommessa.Market.GOAL_NOGOAL
                    ? ((m.homeScore > 0 && m.awayScore > 0) ? "GOAL" : "NOGOAL")
                    : null;
            if (ref == null) continue;
            b.officialResultRef = ref;
            b.status = Scommessa.Status.RESOLVED;
            b.resolvedAt = LocalDateTime.now();
            b.persist();
            scoreGiocate(b);
            resolved++;
        }
        return resolved;
    }

    /** Piazza (o aggiorna) la giocata di un utente su una scommessa aperta. */
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

    private void scoreGiocate(Scommessa b) {
        if (b.officialResultRef == null) return;
        for (Giocata g : Giocata.findByScommessa(b.id)) {
            g.isCorrect = b.officialResultRef.equals(g.choiceRef);
            g.persist();
        }
    }

    private WebApplicationException bad(String msg) {
        return new WebApplicationException(Response.status(400).entity(Map.of("error", msg)).build());
    }

    private WebApplicationException notFound() {
        return new WebApplicationException(Response.status(404).entity(Map.of("error", "Scommessa non trovata")).build());
    }
}
