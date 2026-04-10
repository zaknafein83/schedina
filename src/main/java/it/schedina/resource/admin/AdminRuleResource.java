package it.schedina.resource.admin;

import it.schedina.dto.RuleDto;
import it.schedina.entity.League;
import it.schedina.entity.Rule;
import it.schedina.service.AuthService;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;

@Path("/admin/rules")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminRuleResource {

    @Inject AuthService auth;

    @GET
    @Transactional
    public List<RuleDto.RuleResponse> list(@HeaderParam("Authorization") String token) {
        auth.requireAdmin(token);
        return Rule.<Rule>listAll().stream().map(RuleDto.RuleResponse::from).toList();
    }

    @POST
    @Transactional
    public Response create(@HeaderParam("Authorization") String token, @Valid RuleDto.RuleRequest req) {
        auth.requireAdmin(token);
        if (League.findById(req.leagueId()) == null) {
            return Response.status(404).entity(Map.of("error", "Lega non trovata")).build();
        }
        for (int t : req.winningThresholds()) {
            if (t > req.requiredMatches()) {
                return Response.status(400)
                        .entity(Map.of("error", "Soglia " + t + " supera il numero di partite richieste"))
                        .build();
            }
        }
        Rule r = new Rule();
        r.name = req.name();
        r.description = req.description();
        r.leagueId = req.leagueId();
        r.requiredMatches = req.requiredMatches();
        r.winningThresholds = req.winningThresholds();
        r.maxCouponsPerUser = req.maxCouponsPerUser();
        r.maxDoubles = req.maxDoubles();
        r.maxTriples = req.maxTriples();
        if (req.fullCompletionRequired() != null) r.fullCompletionRequired = req.fullCompletionRequired();
        if (req.isActive() != null) r.isActive = req.isActive();
        r.persist();
        return Response.status(201).entity(RuleDto.RuleResponse.from(r)).build();
    }

    @GET
    @Path("/{id}")
    @Transactional
    public RuleDto.RuleResponse get(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        auth.requireAdmin(token);
        Rule r = Rule.findById(id);
        if (r == null) throw new NotFoundException();
        return RuleDto.RuleResponse.from(r);
    }

    @PATCH
    @Path("/{id}")
    @Transactional
    public RuleDto.RuleResponse update(
            @HeaderParam("Authorization") String token,
            @PathParam("id") Long id,
            RuleDto.RuleRequest req) {
        auth.requireAdmin(token);
        Rule r = Rule.findById(id);
        if (r == null) throw new NotFoundException();
        if (req.name() != null) r.name = req.name();
        if (req.description() != null) r.description = req.description();
        if (req.requiredMatches() > 0) r.requiredMatches = req.requiredMatches();
        if (req.winningThresholds() != null) r.winningThresholds = req.winningThresholds();
        if (req.maxCouponsPerUser() != null) r.maxCouponsPerUser = req.maxCouponsPerUser();
        r.maxDoubles = req.maxDoubles();
        r.maxTriples = req.maxTriples();
        if (req.fullCompletionRequired() != null) r.fullCompletionRequired = req.fullCompletionRequired();
        if (req.isActive() != null) r.isActive = req.isActive();
        r.persist();
        return RuleDto.RuleResponse.from(r);
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response delete(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        auth.requireAdmin(token);
        Rule r = Rule.findById(id);
        if (r == null) throw new NotFoundException();
        r.delete();
        return Response.noContent().build();
    }
}
