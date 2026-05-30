package it.schedina.resource.user;

import it.schedina.dto.GiornataDto;
import it.schedina.dto.MatchDto;
import it.schedina.entity.Giornata;
import it.schedina.entity.League;
import it.schedina.entity.Match;
import it.schedina.entity.Team;
import it.schedina.service.AuthService;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

/** Calendario (giornate di campionato per-lega) consultabile dagli utenti, usato per le scommesse di partita. */
@Path("/giornate")
@Produces(MediaType.APPLICATION_JSON)
public class UserGiornataResource {

    @Inject AuthService auth;

    private GiornataDto.GiornataResponse resp(Giornata g) {
        League l = League.findById(g.leagueId);
        return GiornataDto.GiornataResponse.from(g, l != null ? l.name : "?", Match.count("giornataId", g.id));
    }

    @GET
    @Transactional
    public List<GiornataDto.GiornataResponse> list(
            @HeaderParam("Authorization") String token,
            @QueryParam("leagueId") Long leagueId) {
        auth.requireAuth(token);
        List<Giornata> all = leagueId != null ? Giornata.findByLeague(leagueId) : Giornata.allOrdered();
        return all.stream().map(this::resp).toList();
    }

    @GET
    @Path("/{id}/partite")
    @Transactional
    public List<MatchDto.MatchResponse> partite(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        auth.requireAuth(token);
        return Match.findByGiornata(id).stream().map(m -> {
            Team h = Team.findById(m.homeTeamId);
            Team a = Team.findById(m.awayTeamId);
            return MatchDto.MatchResponse.from(m, h != null ? h.name : "?", a != null ? a.name : "?");
        }).toList();
    }
}
