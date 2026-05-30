package it.schedina.resource.user;

import it.schedina.dto.GiornataDto;
import it.schedina.dto.MatchDto;
import it.schedina.entity.Giornata;
import it.schedina.entity.Match;
import it.schedina.entity.Rule;
import it.schedina.entity.Schedina;
import it.schedina.entity.Team;
import it.schedina.service.AuthService;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/giornate")
@Produces(MediaType.APPLICATION_JSON)
public class UserGiornataResource {

    @Inject AuthService auth;

    private GiornataDto.GiornataResponse resp(Giornata g) {
        Rule rule = g.ruleId != null ? Rule.findById(g.ruleId) : null;
        return GiornataDto.GiornataResponse.from(g, rule,
                Match.count("giornataId", g.id), Schedina.count("giornataId", g.id));
    }

    @GET
    @Transactional
    public List<GiornataDto.GiornataResponse> listOpen(@HeaderParam("Authorization") String token) {
        auth.requireAuth(token);
        return Giornata.findOpen().stream().map(this::resp).toList();
    }

    @GET
    @Path("/{id}")
    @Transactional
    public GiornataDto.GiornataResponse get(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        auth.requireAuth(token);
        Giornata g = Giornata.findById(id);
        if (g == null) throw new NotFoundException();
        return resp(g);
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
