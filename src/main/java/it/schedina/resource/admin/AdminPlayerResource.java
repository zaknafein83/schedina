package it.schedina.resource.admin;

import it.schedina.dto.PlayerDto;
import it.schedina.entity.Player;
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

@Path("/admin/players")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminPlayerResource {

    @Inject AuthService auth;

    @GET
    @Transactional
    public List<PlayerDto.PlayerResponse> list(
            @HeaderParam("Authorization") String token,
            @QueryParam("teamId") Long teamId,
            @QueryParam("leagueId") Long leagueId,
            @QueryParam("role") String role,
            @QueryParam("isActive") Boolean isActive) {
        auth.requireAdminOrMod(token);

        List<Player> players;
        if (teamId != null) {
            players = Player.<Player>find("teamId", teamId).list();
        } else if (leagueId != null) {
            // join via teams.league_id
            List<Long> teamIds = Team.<Team>find("leagueId", leagueId).list().stream().map(t -> t.id).toList();
            players = teamIds.isEmpty() ? List.of() : Player.<Player>find("teamId in ?1", teamIds).list();
        } else {
            players = Player.listAll();
        }

        return players.stream()
                .filter(p -> isActive == null || p.isActive == isActive)
                .filter(p -> role == null || (p.role != null && p.role.name().equalsIgnoreCase(role)))
                .map(PlayerDto.PlayerResponse::from)
                .toList();
    }

    @POST
    @Transactional
    public Response create(@HeaderParam("Authorization") String token, @Valid PlayerDto.PlayerRequest req) {
        auth.requireAdminOrMod(token);
        if (req.teamId() != null && Team.findById(req.teamId()) == null) {
            return Response.status(404).entity(Map.of("error", "Squadra non trovata")).build();
        }
        Player p = new Player();
        p.firstName = req.firstName();
        p.lastName  = req.lastName();
        p.teamId    = req.teamId();
        p.role      = parseRole(req.role());
        if (req.isActive() != null) p.isActive = req.isActive();
        p.persist();
        return Response.status(201).entity(PlayerDto.PlayerResponse.from(p)).build();
    }

    @GET
    @Path("/{id}")
    @Transactional
    public PlayerDto.PlayerResponse get(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        auth.requireAdminOrMod(token);
        Player p = Player.findById(id);
        if (p == null) throw new NotFoundException();
        return PlayerDto.PlayerResponse.from(p);
    }

    @PATCH
    @Path("/{id}")
    @Transactional
    public Response update(
            @HeaderParam("Authorization") String token,
            @PathParam("id") Long id,
            PlayerDto.PlayerRequest req) {
        auth.requireAdminOrMod(token);
        Player p = Player.findById(id);
        if (p == null) throw new NotFoundException();
        if (req.firstName() != null) p.firstName = req.firstName();
        if (req.lastName() != null)  p.lastName  = req.lastName();
        if (req.teamId() != null) {
            if (Team.findById(req.teamId()) == null) {
                return Response.status(404).entity(Map.of("error", "Squadra non trovata")).build();
            }
            p.teamId = req.teamId();
        }
        if (req.role() != null) p.role = parseRole(req.role());
        if (req.isActive() != null) p.isActive = req.isActive();
        p.persist();
        return Response.ok(PlayerDto.PlayerResponse.from(p)).build();
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response delete(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        auth.requireAdminOrMod(token);
        Player p = Player.findById(id);
        if (p == null) throw new NotFoundException();
        p.delete();
        return Response.noContent().build();
    }

    private Player.Role parseRole(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Player.Role.valueOf(s.toUpperCase()); }
        catch (IllegalArgumentException e) {
            throw new BadRequestException("Ruolo non valido: " + s + " (ammessi: GK, DEF, MID, FWD)");
        }
    }
}
