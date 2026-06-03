package it.schedina.resource.user;

import it.schedina.dto.ConcorsoDto;
import it.schedina.dto.MatchDto;
import it.schedina.entity.Concorso;
import it.schedina.entity.Match;
import it.schedina.entity.Rule;
import it.schedina.entity.Schedina;
import it.schedina.entity.Team;
import it.schedina.service.AuthService;
import it.schedina.service.MontepremiService;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

/** Concorsi del Totocalcio aperti agli utenti (la schedina si compila qui). */
@Path("/concorsi")
@Produces(MediaType.APPLICATION_JSON)
public class UserConcorsoResource {

    @Inject AuthService auth;
    @Inject MontepremiService montepremi;

    private ConcorsoDto.ConcorsoResponse resp(Concorso c) {
        Rule rule = c.ruleId != null ? Rule.findById(c.ruleId) : null;
        return ConcorsoDto.ConcorsoResponse.from(c, rule,
                Match.count("concorsoId", c.id), Schedina.count("concorsoId", c.id));
    }

    @GET
    @Transactional
    public List<ConcorsoDto.ConcorsoResponse> listOpen(@HeaderParam("Authorization") String token) {
        auth.requireAuth(token);
        return Concorso.findOpen().stream().map(this::resp).toList();
    }

    @GET
    @Path("/{id}")
    @Transactional
    public ConcorsoDto.ConcorsoResponse get(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        auth.requireAuth(token);
        Concorso c = Concorso.findById(id);
        if (c == null) throw new NotFoundException();
        return resp(c);
    }

    /** Montepremi del concorso + potenziali vincite per soglia (visibile mentre si compila). */
    @GET
    @Path("/{id}/montepremi")
    @Transactional
    public MontepremiService.Projection montepremiProjection(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        auth.requireAuth(token);
        Concorso c = Concorso.findById(id);
        if (c == null) throw new NotFoundException();
        return montepremi.projectionFor(c);
    }

    @GET
    @Path("/{id}/partite")
    @Transactional
    public List<MatchDto.MatchResponse> partite(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        auth.requireAuth(token);
        return Match.findByConcorso(id).stream().map(m -> {
            Team h = Team.findById(m.homeTeamId);
            Team a = Team.findById(m.awayTeamId);
            return MatchDto.MatchResponse.from(m, h != null ? h.name : "?", a != null ? a.name : "?");
        }).toList();
    }
}
