package it.schedina.resource.admin;

import it.schedina.dto.ConcorsoDto;
import it.schedina.entity.Concorso;
import it.schedina.entity.Rule;
import it.schedina.entity.Schedina;
import it.schedina.entity.Scommessa;
import it.schedina.service.AuthService;
import it.schedina.service.NotificationService;
import it.schedina.service.SchedinaScoringEngine;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Path("/admin/concorsi")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminConcorsoResource {

    @Inject AuthService auth;
    @Inject SchedinaScoringEngine scoring;
    @Inject NotificationService notifications;

    private ConcorsoDto.ConcorsoResponse resp(Concorso c) {
        long bets = Scommessa.count("concorsoId", c.id);
        long sched = Schedina.count("concorsoId", c.id);
        return ConcorsoDto.ConcorsoResponse.from(c, bets, sched);
    }

    @GET
    @Transactional
    public List<ConcorsoDto.ConcorsoResponse> list(@HeaderParam("Authorization") String token) {
        auth.requireAdminOrMod(token);
        return Concorso.<Concorso>listAll().stream().map(this::resp).toList();
    }

    @GET
    @Path("/{id}")
    @Transactional
    public ConcorsoDto.ConcorsoResponse get(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        auth.requireAdminOrMod(token);
        Concorso c = Concorso.findById(id);
        if (c == null) throw new NotFoundException();
        return resp(c);
    }

    @POST
    @Transactional
    public Response create(@HeaderParam("Authorization") String token, @Valid ConcorsoDto.ConcorsoRequest req) {
        auth.requireAdminOrMod(token);
        if (Rule.findById(req.ruleId()) == null) {
            return Response.status(404).entity(Map.of("error", "Regola non trovata")).build();
        }
        Concorso c = new Concorso();
        c.name = req.name();
        c.description = req.description();
        if (req.kind() != null) c.kind = req.kind();
        c.ruleId = req.ruleId();
        c.openAt = req.openAt();
        c.closeAt = req.closeAt();
        c.persist();
        return Response.status(201).entity(resp(c)).build();
    }

    @PATCH
    @Path("/{id}")
    @Transactional
    public ConcorsoDto.ConcorsoResponse update(
            @HeaderParam("Authorization") String token,
            @PathParam("id") Long id,
            ConcorsoDto.ConcorsoRequest req) {
        auth.requireAdminOrMod(token);
        Concorso c = Concorso.findById(id);
        if (c == null) throw new NotFoundException();
        if (req.name() != null) c.name = req.name();
        if (req.description() != null) c.description = req.description();
        if (req.kind() != null) c.kind = req.kind();
        if (req.ruleId() != null) c.ruleId = req.ruleId();
        if (req.openAt() != null) c.openAt = req.openAt();
        if (req.closeAt() != null) c.closeAt = req.closeAt();
        c.persist();
        return resp(c);
    }

    @POST
    @Path("/{id}/open")
    @Transactional
    public Response open(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        auth.requireAdminOrMod(token);
        Concorso c = Concorso.findById(id);
        if (c == null) throw new NotFoundException();
        if (Scommessa.count("concorsoId", c.id) == 0) {
            return Response.status(400).entity(Map.of("error", "Il concorso non ha scommesse")).build();
        }
        c.status = Concorso.Status.OPEN;
        c.persist();
        return Response.ok(resp(c)).build();
    }

    @POST
    @Path("/{id}/close")
    @Transactional
    public Response close(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        auth.requireAdminOrMod(token);
        Concorso c = Concorso.findById(id);
        if (c == null) throw new NotFoundException();
        c.status = Concorso.Status.CLOSED;
        c.persist();
        return Response.ok(resp(c)).build();
    }

    @POST
    @Path("/{id}/process")
    @Transactional
    public Response process(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        auth.requireAdminOrMod(token);
        Concorso c = Concorso.findById(id);
        if (c == null) throw new NotFoundException();
        if (c.status != Concorso.Status.CLOSED && c.status != Concorso.Status.PROCESSED) {
            return Response.status(400).entity(Map.of("error", "Il concorso deve essere chiuso prima dell'elaborazione")).build();
        }
        Map<String, Object> result = new HashMap<>(scoring.process(c));
        if (c.status == Concorso.Status.PROCESSED) {
            result.put("notifications", notifications.sendConcorsoNotifications(c.id));
        }
        return Response.ok(result).build();
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response delete(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        auth.requireAdmin(token);
        Concorso c = Concorso.findById(id);
        if (c == null) throw new NotFoundException();
        if (Schedina.count("concorsoId", c.id) > 0) {
            return Response.status(409).entity(Map.of("error", "Concorso con schedine, impossibile eliminare")).build();
        }
        Scommessa.delete("concorsoId", c.id);
        c.delete();
        return Response.noContent().build();
    }
}
