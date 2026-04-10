package it.schedina.resource.admin;

import it.schedina.dto.MatchDto;
import it.schedina.entity.Match;
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
        auth.requireAdmin(token);
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
        Match m = new Match();
        m.homeTeamId = req.homeTeamId();
        m.awayTeamId = req.awayTeamId();
        m.leagueId = home.leagueId;
        m.contestId = req.contestId();
        m.scheduledAt = req.scheduledAt();
        m.persist();
        return Response.status(201).entity(enrich(m)).build();
    }

    @GET
    @Path("/{id}")
    @Transactional
    public MatchDto.MatchResponse get(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        auth.requireAdmin(token);
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
        auth.requireAdmin(token);
        if (!VALID_RESULTS.contains(req.officialResult())) {
            return Response.status(400).entity(Map.of("error", "Risultato non valido. Valori ammessi: 1, X, 2")).build();
        }
        Match m = Match.findById(id);
        if (m == null) throw new NotFoundException();
        if (m.status == Match.Status.VALIDATED) {
            return Response.status(400).entity(Map.of("error", "Risultato già validato, non modificabile")).build();
        }
        m.officialResult = req.officialResult();
        m.status = Match.Status.RESULT_ENTERED;
        m.persist();
        return Response.ok(enrich(m)).build();
    }

    @POST
    @Path("/{id}/validate")
    @Transactional
    public Response validate(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        auth.requireAdmin(token);
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
