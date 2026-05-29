package it.schedina.resource.admin;

import it.schedina.dto.MatchDto;
import it.schedina.entity.Match;
import it.schedina.entity.Team;
import it.schedina.service.AuthService;
import it.schedina.service.ScommessaResolutionService;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;

@Path("/admin/matches")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminMatchResource {

    @Inject AuthService auth;
    @Inject ScommessaResolutionService resolution;

    private MatchDto.MatchResponse enrich(Match m) {
        Team home = Team.findById(m.homeTeamId);
        Team away = Team.findById(m.awayTeamId);
        return MatchDto.MatchResponse.from(m,
                home != null ? home.name : "?", away != null ? away.name : "?");
    }

    @GET
    @Transactional
    public List<MatchDto.MatchResponse> list(
            @HeaderParam("Authorization") String token,
            @QueryParam("giornataId") Long giornataId,
            @QueryParam("leagueId") Long leagueId) {
        auth.requireAdminOrMod(token);
        List<Match> all;
        if (giornataId != null) all = Match.findByGiornata(giornataId);
        else if (leagueId != null) all = Match.findByLeague(leagueId);
        else all = Match.listAll();
        return all.stream().map(this::enrich).toList();
    }

    @POST
    @Transactional
    public Response create(@HeaderParam("Authorization") String token, @Valid MatchDto.MatchRequest req) {
        auth.requireAdminOrMod(token);
        if (req.homeTeamId().equals(req.awayTeamId())) {
            return Response.status(400).entity(Map.of("error", "Casa e ospite non possono essere la stessa squadra")).build();
        }
        Team home = Team.findById(req.homeTeamId());
        Team away = Team.findById(req.awayTeamId());
        if (home == null || away == null) {
            return Response.status(404).entity(Map.of("error", "Squadra non trovata")).build();
        }
        if (!home.leagueId.equals(away.leagueId)) {
            return Response.status(400).entity(Map.of("error", "Le due squadre devono appartenere alla stessa divisione")).build();
        }
        Match m = new Match();
        m.homeTeamId = req.homeTeamId();
        m.awayTeamId = req.awayTeamId();
        m.leagueId = home.leagueId;
        m.giornataId = req.giornataId();
        m.scheduledAt = req.scheduledAt();
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
    public MatchDto.MatchResponse update(@HeaderParam("Authorization") String token,
            @PathParam("id") Long id, MatchDto.MatchRequest req) {
        auth.requireAdminOrMod(token);
        Match m = Match.findById(id);
        if (m == null) throw new NotFoundException();
        if (req.homeTeamId() != null) m.homeTeamId = req.homeTeamId();
        if (req.awayTeamId() != null) m.awayTeamId = req.awayTeamId();
        if (req.giornataId() != null) m.giornataId = req.giornataId();
        if (req.scheduledAt() != null) m.scheduledAt = req.scheduledAt();
        if (req.overUnderLine() != null) m.overUnderLine = req.overUnderLine();
        m.persist();
        return enrich(m);
    }

    /** Inserisce il punteggio. Gli esiti 1X2/U-O della schedina si calcolano all'elaborazione della giornata; qui si risolvono le eventuali scommesse AUTO (gol/no gol) della partita. */
    @PUT
    @Path("/{id}/result")
    @Transactional
    public Response setResult(@HeaderParam("Authorization") String token,
            @PathParam("id") Long id, @Valid MatchDto.MatchResultRequest req) {
        auth.requireAdminOrMod(token);
        if (req.homeScore() < 0 || req.awayScore() < 0) {
            return Response.status(400).entity(Map.of("error", "I punteggi non possono essere negativi")).build();
        }
        Match m = Match.findById(id);
        if (m == null) throw new NotFoundException();
        if (m.status == Match.Status.VALIDATED) {
            return Response.status(400).entity(Map.of("error", "Risultato già validato")).build();
        }
        m.homeScore = req.homeScore();
        m.awayScore = req.awayScore();
        m.status = Match.Status.RESULT_ENTERED;
        m.persist();
        int resolved = resolution.resolveFromMatch(m);
        return Response.ok(Map.of("match", enrich(m), "betsResolved", resolved)).build();
    }

    @POST
    @Path("/{id}/validate")
    @Transactional
    public Response validate(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        auth.requireAdminOrMod(token);
        Match m = Match.findById(id);
        if (m == null) throw new NotFoundException();
        if (!m.hasScore()) {
            return Response.status(400).entity(Map.of("error", "Nessun punteggio impostato")).build();
        }
        m.status = Match.Status.VALIDATED;
        m.persist();
        return Response.ok(enrich(m)).build();
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response delete(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        auth.requireAdminOrMod(token);
        Match m = Match.findById(id);
        if (m == null) throw new NotFoundException();
        m.delete();
        return Response.noContent().build();
    }
}
