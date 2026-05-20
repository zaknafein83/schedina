package it.schedina.resource.user;

import it.schedina.dto.PlayerDto;
import it.schedina.dto.TeamDto;
import it.schedina.entity.Player;
import it.schedina.entity.Team;
import it.schedina.service.AuthService;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

/**
 * Listini informativi (squadre, giocatori) accessibili a qualsiasi utente loggato.
 * Read-only.
 */
@Path("/listini")
@Produces(MediaType.APPLICATION_JSON)
public class PublicListResource {

    @Inject AuthService auth;

    @GET
    @Path("/teams")
    @Transactional
    public List<TeamDto.TeamResponse> teams(
            @HeaderParam("Authorization") String token,
            @QueryParam("leagueId") Long leagueId) {
        auth.requireAuth(token);
        List<Team> list = leagueId != null
                ? Team.<Team>find("leagueId = ?1 and isActive = true order by name", leagueId).list()
                : Team.<Team>find("isActive = true order by name").list();
        return list.stream().map(TeamDto.TeamResponse::from).toList();
    }

    @GET
    @Path("/players")
    @Transactional
    public List<PlayerDto.PlayerResponse> players(
            @HeaderParam("Authorization") String token,
            @QueryParam("teamId") Long teamId,
            @QueryParam("leagueId") Long leagueId,
            @QueryParam("role") String role) {
        auth.requireAuth(token);
        List<Player> list;
        if (teamId != null) {
            list = Player.<Player>find("teamId = ?1 and isActive = true order by lastName, firstName", teamId).list();
        } else if (leagueId != null) {
            List<Long> teamIds = Team.<Team>find("leagueId", leagueId).list().stream().map(t -> t.id).toList();
            list = teamIds.isEmpty()
                    ? List.of()
                    : Player.<Player>find("teamId in ?1 and isActive = true order by lastName, firstName", teamIds).list();
        } else {
            list = Player.<Player>find("isActive = true order by lastName, firstName").list();
        }
        return list.stream()
                .filter(p -> role == null || (p.role != null && p.role.name().equalsIgnoreCase(role)))
                .map(PlayerDto.PlayerResponse::from)
                .toList();
    }
}
