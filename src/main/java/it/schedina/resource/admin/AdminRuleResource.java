package it.schedina.resource.admin;

import it.schedina.dto.RuleDto;
import it.schedina.entity.Giornata;
import it.schedina.entity.Rule;
import it.schedina.service.AuthService;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.ArrayList;
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
        auth.requireAdminOrMod(token);
        return Rule.allOrdered().stream().map(RuleDto.RuleResponse::from).toList();
    }

    @GET
    @Path("/{id}")
    @Transactional
    public RuleDto.RuleResponse get(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        auth.requireAdminOrMod(token);
        Rule r = Rule.findById(id);
        if (r == null) throw new NotFoundException();
        return RuleDto.RuleResponse.from(r);
    }

    @POST
    @Transactional
    public Response create(@HeaderParam("Authorization") String token, @Valid RuleDto.RuleRequest req) {
        auth.requireAdminOrMod(token);
        Rule r = new Rule();
        r.name = req.name();
        if (req.winningThresholds() != null) r.winningThresholds = new ArrayList<>(req.winningThresholds());
        if (req.isActive() != null) r.isActive = req.isActive();
        r.persist();
        return Response.status(201).entity(RuleDto.RuleResponse.from(r)).build();
    }

    @PATCH
    @Path("/{id}")
    @Transactional
    public RuleDto.RuleResponse update(@HeaderParam("Authorization") String token,
            @PathParam("id") Long id, RuleDto.RuleRequest req) {
        auth.requireAdminOrMod(token);
        Rule r = Rule.findById(id);
        if (r == null) throw new NotFoundException();
        if (req.name() != null) r.name = req.name();
        if (req.winningThresholds() != null) r.winningThresholds = new ArrayList<>(req.winningThresholds());
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
        if (Giornata.count("ruleId", id) > 0) {
            return Response.status(409).entity(Map.of("error", "Regola usata da una o più giornate, impossibile eliminare")).build();
        }
        r.delete();
        return Response.noContent().build();
    }
}
