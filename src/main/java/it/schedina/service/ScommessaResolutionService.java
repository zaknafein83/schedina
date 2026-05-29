package it.schedina.service;

import it.schedina.dto.ScommessaDto;
import it.schedina.entity.BetOption;
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
 * Gestione del ciclo di vita della Scommessa: creazione (con opzioni), risoluzione MANUAL,
 * annullamento, e risoluzione AUTO derivata dal punteggio della partita.
 */
@ApplicationScoped
public class ScommessaResolutionService {

    @Transactional
    public Scommessa create(ScommessaDto.ScommessaRequest req) {
        Scommessa b = new Scommessa();
        b.concorsoId = req.concorsoId();
        b.label = req.label();
        b.market = req.market();
        b.matchId = req.matchId();
        b.tournamentId = req.tournamentId();
        b.seasonId = req.seasonId();
        b.leagueId = req.leagueId();
        b.overUnderLine = req.overUnderLine();
        b.resolutionMode = Scommessa.defaultResolution(req.market());
        if (b.market == Scommessa.Market.UNDER_OVER && b.overUnderLine == null) {
            b.overUnderLine = 3.5;
        }
        b.persist();

        List<ScommessaDto.OptionInput> opts = standardOrProvidedOptions(b, req.options());
        if (opts.isEmpty()) {
            throw bad("La scommessa richiede almeno un'opzione selezionabile");
        }
        int order = 0;
        for (var o : opts) {
            BetOption opt = new BetOption();
            opt.betId = b.id;
            opt.ref = o.ref();
            opt.label = (o.label() != null && !o.label().isBlank()) ? o.label() : o.ref();
            opt.displayOrder = o.displayOrder() != null ? o.displayOrder() : order;
            opt.persist();
            order++;
        }
        return b;
    }

    private List<ScommessaDto.OptionInput> standardOrProvidedOptions(
            Scommessa b, List<ScommessaDto.OptionInput> provided) {
        if (b.targetKind() == Scommessa.TargetKind.TOKEN && (provided == null || provided.isEmpty())) {
            return switch (b.market) {
                case RESULT_1X2 -> List.of(opt("1", "1 (Casa)"), opt("X", "X (Pareggio)"), opt("2", "2 (Trasferta)"));
                case UNDER_OVER -> List.of(opt("U", "Under " + b.overUnderLine), opt("O", "Over " + b.overUnderLine));
                case GOAL_NOGOAL -> List.of(opt("GOAL", "Gol"), opt("NOGOAL", "No gol"));
                default -> List.of();
            };
        }
        return provided != null ? provided : new ArrayList<>();
    }

    private ScommessaDto.OptionInput opt(String ref, String label) {
        return new ScommessaDto.OptionInput(ref, label, null);
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
        return b;
    }

    /** Risolve automaticamente le scommesse AUTO (1X2, U/O, Gol-Nogol) collegate alla partita. */
    @Transactional
    public int resolveFromMatch(Match m) {
        if (!m.hasScore()) return 0;
        int resolved = 0;
        for (Scommessa b : Scommessa.findByMatch(m.id)) {
            if (b.resolutionMode != Scommessa.ResolutionMode.AUTO
                    || b.status == Scommessa.Status.RESOLVED
                    || b.status == Scommessa.Status.VOID) {
                continue;
            }
            String ref = computeAuto(b, m);
            if (ref == null) continue;
            b.officialResultRef = ref;
            b.status = Scommessa.Status.RESOLVED;
            b.resolvedAt = LocalDateTime.now();
            b.persist();
            resolved++;
        }
        return resolved;
    }

    private String computeAuto(Scommessa b, Match m) {
        int h = m.homeScore, a = m.awayScore;
        return switch (b.market) {
            case RESULT_1X2 -> h > a ? "1" : (h == a ? "X" : "2");
            case UNDER_OVER -> {
                double line = b.overUnderLine != null ? b.overUnderLine : 3.5;
                yield (h + a) > line ? "O" : "U";
            }
            case GOAL_NOGOAL -> (h > 0 && a > 0) ? "GOAL" : "NOGOAL";
            default -> null;
        };
    }

    private WebApplicationException bad(String msg) {
        return new WebApplicationException(
                Response.status(400).entity(Map.of("error", msg)).build());
    }

    private WebApplicationException notFound() {
        return new WebApplicationException(
                Response.status(404).entity(Map.of("error", "Scommessa non trovata")).build());
    }
}
