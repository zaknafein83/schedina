package it.schedina.resource.admin;

import it.schedina.dto.MatchDto;
import it.schedina.entity.Contest;
import it.schedina.dto.MatchSideBetDto;
import it.schedina.entity.Match;
import it.schedina.entity.MatchSidePrediction;
import it.schedina.entity.Team;
import it.schedina.service.AuthService;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Path("/admin/matches")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminMatchResource {

    // kept for legacy validation (e.g. import)
    private static final Set<String> VALID_RESULTS = Set.of("1", "X", "2");

    @Inject AuthService auth;

    private MatchDto.MatchResponse enrich(Match m) {
        Team home = Team.findById(m.homeTeamId);
        Team away = Team.findById(m.awayTeamId);
        return MatchDto.MatchResponse.from(m,
                home != null ? home.name : "?",
                away != null ? away.name : "?");
    }

    @GET
    @Transactional
    public List<MatchDto.MatchResponse> list(
            @HeaderParam("Authorization") String token,
            @QueryParam("contestId") Long contestId,
            @QueryParam("leagueId") Long leagueId) {
        auth.requireAdminOrMod(token);
        var all = contestId != null
                ? Match.findByContest(contestId)
                : Match.<Match>listAll();
        return all.stream()
                .filter(m -> leagueId == null || leagueId.equals(m.leagueId))
                .map(this::enrich).toList();
    }

    @POST
    @Transactional
    public Response create(@HeaderParam("Authorization") String token, @Valid MatchDto.MatchRequest req) {
        auth.requireAdmin(token);
        if (req.homeTeamId().equals(req.awayTeamId())) {
            return Response.status(400).entity(Map.of("error", "Casa e ospite non possono essere la stessa squadra")).build();
        }
        Team home = Team.findById(req.homeTeamId());
        Team away = Team.findById(req.awayTeamId());
        if (home == null || away == null) {
            return Response.status(404).entity(Map.of("error", "Squadra non trovata")).build();
        }
        if (!home.leagueId.equals(away.leagueId)) {
            return Response.status(400).entity(Map.of("error", "Le due squadre devono appartenere alla stessa lega")).build();
        }
        if (req.contestId() != null) {
            Contest c = Contest.findById(req.contestId());
            if (c != null && (c.status == Contest.Status.CLOSED
                    || c.status == Contest.Status.PROCESSING
                    || c.status == Contest.Status.PROCESSED)) {
                return Response.status(400).entity(Map.of(
                        "error", "Impossibile aggiungere partite a un concorso in stato " + c.status)).build();
            }
        }
        Match m = new Match();
        m.homeTeamId = req.homeTeamId();
        m.awayTeamId = req.awayTeamId();
        m.leagueId = home.leagueId;
        m.contestId = req.contestId();
        m.scheduledAt = req.scheduledAt();
        if (req.betType() != null) m.betType = req.betType();
        if (req.overUnderLine() != null) m.overUnderLine = req.overUnderLine();
        m.persist();
        return Response.status(201).entity(enrich(m)).build();
    }

    @GET
    @Path("/{id}")
    @Transactional
    public MatchDto.MatchResponse get(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        auth.requireAdminOrMod(token);
        Match m = Match.findById(id);
        if (m == null) throw new NotFoundException();
        return enrich(m);
    }

    @PATCH
    @Path("/{id}")
    @Transactional
    public MatchDto.MatchResponse update(
            @HeaderParam("Authorization") String token,
            @PathParam("id") Long id,
            MatchDto.MatchRequest req) {
        auth.requireAdmin(token);
        Match m = Match.findById(id);
        if (m == null) throw new NotFoundException();
        if (req.homeTeamId() != null) m.homeTeamId = req.homeTeamId();
        if (req.awayTeamId() != null) m.awayTeamId = req.awayTeamId();
        if (req.contestId() != null) m.contestId = req.contestId();
        if (req.scheduledAt() != null) m.scheduledAt = req.scheduledAt();
        if (req.betType() != null) m.betType = req.betType();
        if (req.overUnderLine() != null) m.overUnderLine = req.overUnderLine();
        m.persist();
        return enrich(m);
    }

    @PUT
    @Path("/{id}/result")
    @Transactional
    public Response setResult(
            @HeaderParam("Authorization") String token,
            @PathParam("id") Long id,
            @Valid MatchDto.MatchResultRequest req) {
        auth.requireAdminOrMod(token);
        if (req.homeScore() < 0 || req.awayScore() < 0) {
            return Response.status(400)
                    .entity(Map.of("error", "I punteggi non possono essere negativi"))
                    .build();
        }
        Match m = Match.findById(id);
        if (m == null) throw new NotFoundException();
        if (m.status == Match.Status.VALIDATED) {
            return Response.status(400)
                    .entity(Map.of("error", "Risultato già validato, non modificabile"))
                    .build();
        }
        m.homeScore = req.homeScore();
        m.awayScore = req.awayScore();
        m.officialResult = m.computeResult();   // auto-calcola 1 / X / 2
        m.status = Match.Status.RESULT_ENTERED;
        m.persist();
        // Auto-risoluzione side bet GOAL_NOGOAL (calcolabile da homeScore/awayScore)
        autoResolveGoalNogoal(m);
        return Response.ok(enrich(m)).build();
    }

    private void autoResolveGoalNogoal(Match m) {
        if (m.homeScore == null || m.awayScore == null) return;
        for (MatchSidePrediction sp : MatchSidePrediction.findByMatch(m.id)) {
            if (sp.betType == MatchSidePrediction.BetType.GOAL_NOGOAL
                    && sp.status == MatchSidePrediction.Status.PENDING) {
                sp.officialResult = (m.homeScore > 0 && m.awayScore > 0) ? "GOAL" : "NOGOAL";
                sp.status = MatchSidePrediction.Status.RESOLVED;
                sp.resolvedAt = java.time.LocalDateTime.now();
                sp.persist();
            }
        }
    }

    // ─── Side bet CRUD ──────────────────────────────────────────────────────

    @GET
    @Path("/{id}/side-bets")
    @Transactional
    public List<MatchSideBetDto.SideBetResponse> listSideBets(
            @HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        auth.requireAdminOrMod(token);
        Match m = Match.findById(id);
        if (m == null) throw new NotFoundException();
        return MatchSidePrediction.findByMatch(id).stream()
                .map(MatchSideBetDto.SideBetResponse::from).toList();
    }

    @POST
    @Path("/{id}/side-bets")
    @Transactional
    public Response createSideBet(
            @HeaderParam("Authorization") String token,
            @PathParam("id") Long id,
            @Valid MatchSideBetDto.SideBetRequest req) {
        auth.requireAdminOrMod(token);
        Match m = Match.findById(id);
        if (m == null) throw new NotFoundException();
        MatchSidePrediction.BetType bt;
        try { bt = MatchSidePrediction.BetType.valueOf(req.betType().toUpperCase()); }
        catch (IllegalArgumentException e) {
            return Response.status(400).entity(Map.of("error", "BetType non valido: " + req.betType())).build();
        }
        long dup = MatchSidePrediction.count("matchId = ?1 and betType = ?2", id, bt);
        if (dup > 0) {
            return Response.status(409).entity(Map.of("error", "Side bet già configurato per questa partita")).build();
        }
        MatchSidePrediction sp = new MatchSidePrediction();
        sp.matchId = id;
        sp.betType = bt;
        sp.label = (req.label() != null && !req.label().isBlank())
                ? req.label()
                : (bt == MatchSidePrediction.BetType.GOAL_NOGOAL ? "Gol/No gol" : "Primo marcatore");
        sp.persist();
        return Response.status(201).entity(MatchSideBetDto.SideBetResponse.from(sp)).build();
    }

    @DELETE
    @Path("/{id}/side-bets/{sid}")
    @Transactional
    public Response deleteSideBet(
            @HeaderParam("Authorization") String token,
            @PathParam("id") Long id, @PathParam("sid") Long sid) {
        auth.requireAdminOrMod(token);
        MatchSidePrediction sp = MatchSidePrediction.findById(sid);
        if (sp == null || !sp.matchId.equals(id)) throw new NotFoundException();
        sp.delete();
        return Response.noContent().build();
    }

    @PATCH
    @Path("/{id}/side-bets/{sid}/resolve")
    @Transactional
    public Response resolveSideBet(
            @HeaderParam("Authorization") String token,
            @PathParam("id") Long id, @PathParam("sid") Long sid,
            @Valid MatchSideBetDto.ResolveRequest req) {
        auth.requireAdminOrMod(token);
        MatchSidePrediction sp = MatchSidePrediction.findById(sid);
        if (sp == null || !sp.matchId.equals(id)) throw new NotFoundException();
        if (sp.betType == MatchSidePrediction.BetType.GOAL_NOGOAL) {
            String v = req.officialResult().toUpperCase();
            if (!v.equals("GOAL") && !v.equals("NOGOAL")) {
                return Response.status(400).entity(Map.of("error", "Valori ammessi: GOAL, NOGOAL")).build();
            }
            sp.officialResult = v;
        } else {
            sp.officialResult = req.officialResult();
        }
        sp.status = MatchSidePrediction.Status.RESOLVED;
        sp.resolvedAt = java.time.LocalDateTime.now();
        sp.persist();
        return Response.ok(MatchSideBetDto.SideBetResponse.from(sp)).build();
    }

    @POST
    @Path("/{id}/validate")
    @Transactional
    public Response validate(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        auth.requireAdminOrMod(token);
        Match m = Match.findById(id);
        if (m == null) throw new NotFoundException();
        if (m.officialResult == null) {
            return Response.status(400).entity(Map.of("error", "Nessun risultato impostato")).build();
        }
        m.status = Match.Status.VALIDATED;
        m.persist();
        return Response.ok(enrich(m)).build();
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response delete(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        auth.requireAdmin(token);
        Match m = Match.findById(id);
        if (m == null) throw new NotFoundException();
        m.delete();
        return Response.noContent().build();
    }
}
