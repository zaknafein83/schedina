package it.schedina.resource.admin;

import it.schedina.dto.TeamDto;
import it.schedina.entity.League;
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

@Path("/admin/teams")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminTeamResource {

    @Inject AuthService auth;

    @GET
    @Transactional
    public List<TeamDto.TeamResponse> list(
            @HeaderParam("Authorization") String token,
            @QueryParam("leagueId") Long leagueId,
            @QueryParam("isActive") Boolean isActive) {
        auth.requireAdmin(token);
        var query = leagueId != null
                ? Team.<Team>find("leagueId", leagueId).list()
                : Team.<Team>listAll();
        return query.stream()
                .filter(t -> isActive == null || t.isActive == isActive)
                .map(TeamDto.TeamResponse::from).toList();
    }

    @POST
    @Transactional
    public Response create(@HeaderParam("Authorization") String token, @Valid TeamDto.TeamRequest req) {
        auth.requireAdmin(token);
        if (League.findById(req.leagueId()) == null) {
            return Response.status(404).entity(Map.of("error", "Lega non trovata")).build();
        }
        Team t = new Team();
        t.name = req.name();
        t.shortName = req.shortName();
        t.leagueId = req.leagueId();
        if (req.isActive() != null) t.isActive = req.isActive();
        t.persist();
        return Response.status(201).entity(TeamDto.TeamResponse.from(t)).build();
    }

    @GET
    @Path("/{id}")
    @Transactional
    public TeamDto.TeamResponse get(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        auth.requireAdmin(token);
        Team t = Team.findById(id);
        if (t == null) throw new NotFoundException();
        return TeamDto.TeamResponse.from(t);
    }

    @PATCH
    @Path("/{id}")
    @Transactional
    public TeamDto.TeamResponse update(
            @HeaderParam("Authorization") String token,
            @PathParam("id") Long id,
            TeamDto.TeamRequest req) {
        auth.requireAdmin(token);
        Team t = Team.findById(id);
        if (t == null) throw new NotFoundException();
        if (req.name() != null) t.name = req.name();
        if (req.shortName() != null) t.shortName = req.shortName();
        if (req.leagueId() != null) t.leagueId = req.leagueId();
        if (req.isActive() != null) t.isActive = req.isActive();
        t.persist();
        return TeamDto.TeamResponse.from(t);
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response delete(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        auth.requireAdmin(token);
        Team t = Team.findById(id);
        if (t == null) throw new NotFoundException();
        t.delete();
        return Response.noContent().build();
    }
}
