package it.schedina.resource.admin;

import it.schedina.dto.GiornataDto;
import it.schedina.entity.Giornata;
import it.schedina.entity.Match;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Path("/admin/giornate")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminGiornataResource {

    @Inject AuthService auth;
    @Inject SchedinaScoringEngine scoring;
    @Inject NotificationService notifications;

    private GiornataDto.GiornataResponse resp(Giornata g) {
        return GiornataDto.GiornataResponse.from(g,
                Match.count("giornataId", g.id), Schedina.count("giornataId", g.id));
    }

    @GET
    @Transactional
    public List<GiornataDto.GiornataResponse> list(@HeaderParam("Authorization") String token) {
        auth.requireAdminOrMod(token);
        return Giornata.<Giornata>find("order by number desc, id desc").list().stream().map(this::resp).toList();
    }

    @GET
    @Path("/{id}")
    @Transactional
    public GiornataDto.GiornataResponse get(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        auth.requireAdminOrMod(token);
        Giornata g = Giornata.findById(id);
        if (g == null) throw new NotFoundException();
        return resp(g);
    }

    @POST
    @Transactional
    public Response create(@HeaderParam("Authorization") String token, @Valid GiornataDto.GiornataRequest req) {
        auth.requireAdminOrMod(token);
        Giornata g = new Giornata();
        g.name = req.name();
        g.number = req.number() != null ? req.number() : (int) (Giornata.count() + 1);
        g.seasonId = req.seasonId();
        g.openAt = req.openAt();
        g.closeAt = req.closeAt();
        if (req.winningThresholds() != null) g.winningThresholds = new ArrayList<>(req.winningThresholds());
        g.persist();
        return Response.status(201).entity(resp(g)).build();
    }

    @PATCH
    @Path("/{id}")
    @Transactional
    public GiornataDto.GiornataResponse update(@HeaderParam("Authorization") String token,
            @PathParam("id") Long id, GiornataDto.GiornataRequest req) {
        auth.requireAdminOrMod(token);
        Giornata g = Giornata.findById(id);
        if (g == null) throw new NotFoundException();
        if (req.name() != null) g.name = req.name();
        if (req.number() != null) g.number = req.number();
        if (req.seasonId() != null) g.seasonId = req.seasonId();
        if (req.openAt() != null) g.openAt = req.openAt();
        if (req.closeAt() != null) g.closeAt = req.closeAt();
        if (req.winningThresholds() != null) g.winningThresholds = new ArrayList<>(req.winningThresholds());
        g.persist();
        return resp(g);
    }

    @POST
    @Path("/{id}/open")
    @Transactional
    public Response open(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        auth.requireAdminOrMod(token);
        Giornata g = Giornata.findById(id);
        if (g == null) throw new NotFoundException();
        if (Match.count("giornataId", g.id) == 0) {
            return Response.status(400).entity(Map.of("error", "La giornata non ha partite")).build();
        }
        g.status = Giornata.Status.OPEN;
        g.persist();
        return Response.ok(resp(g)).build();
    }

    @POST
    @Path("/{id}/close")
    @Transactional
    public Response close(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        auth.requireAdminOrMod(token);
        Giornata g = Giornata.findById(id);
        if (g == null) throw new NotFoundException();
        g.status = Giornata.Status.CLOSED;
        g.persist();
        return Response.ok(resp(g)).build();
    }

    @POST
    @Path("/{id}/process")
    @Transactional
    public Response process(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        auth.requireAdminOrMod(token);
        Giornata g = Giornata.findById(id);
        if (g == null) throw new NotFoundException();
        if (g.status != Giornata.Status.CLOSED && g.status != Giornata.Status.PROCESSED) {
            return Response.status(400).entity(Map.of("error", "La giornata deve essere chiusa prima dell'elaborazione")).build();
        }
        Map<String, Object> result = new java.util.HashMap<>(scoring.process(g));
        if (g.status == Giornata.Status.PROCESSED) {
            result.put("notifications", notifications.sendGiornataNotifications(g.id));
        }
        return Response.ok(result).build();
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response delete(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        auth.requireAdmin(token);
        Giornata g = Giornata.findById(id);
        if (g == null) throw new NotFoundException();
        if (Schedina.count("giornataId", g.id) > 0) {
            return Response.status(409).entity(Map.of("error", "Giornata con schedine, impossibile eliminare")).build();
        }
        Scommessa.delete("giornataId", g.id);
        Match.delete("giornataId", g.id);
        g.delete();
        return Response.noContent().build();
    }
}
