package it.schedina.resource.admin;

import it.schedina.dto.ContestDto;
import it.schedina.entity.*;
import it.schedina.service.AuthService;
import it.schedina.service.CouponEngine;
import it.schedina.service.NotificationService;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;

@Path("/admin/contests")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminContestResource {

    @Inject AuthService auth;
    @Inject CouponEngine couponEngine;
    @Inject NotificationService notificationService;

    private ContestDto.ContestResponse withCounts(Contest c) {
        long matches = Match.count("contestId", c.id);
        long coupons = Coupon.count("contestId", c.id);
        return ContestDto.ContestResponse.from(c, matches, coupons);
    }

    @GET
    @Transactional
    public List<ContestDto.ContestResponse> list(@HeaderParam("Authorization") String token) {
        auth.requireAdmin(token);
        return Contest.<Contest>listAll().stream().map(this::withCounts).toList();
    }

    @POST
    @Transactional
    public Response create(@HeaderParam("Authorization") String token, @Valid ContestDto.ContestRequest req) {
        auth.requireAdmin(token);
        if (req.closeAt().isBefore(req.openAt()) || req.closeAt().isEqual(req.openAt())) {
            return Response.status(400).entity(Map.of("error", "closeAt deve essere dopo openAt")).build();
        }
        if (League.findById(req.leagueId()) == null) {
            return Response.status(404).entity(Map.of("error", "Lega non trovata")).build();
        }
        if (Rule.findById(req.ruleId()) == null) {
            return Response.status(404).entity(Map.of("error", "Regola non trovata")).build();
        }
        Contest c = new Contest();
        c.name = req.name();
        c.description = req.description();
        c.leagueId = req.leagueId();
        c.ruleId = req.ruleId();
        c.openAt = req.openAt();
        c.closeAt = req.closeAt();
        c.persist();
        return Response.status(201).entity(withCounts(c)).build();
    }

    @GET
    @Path("/{id}")
    @Transactional
    public ContestDto.ContestResponse get(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        auth.requireAdmin(token);
        Contest c = Contest.findById(id);
        if (c == null) throw new NotFoundException();
        return withCounts(c);
    }

    @PATCH
    @Path("/{id}")
    @Transactional
    public Response update(
            @HeaderParam("Authorization") String token,
            @PathParam("id") Long id,
            ContestDto.ContestRequest req) {
        auth.requireAdmin(token);
        Contest c = Contest.findById(id);
        if (c == null) throw new NotFoundException();
        if (c.status != Contest.Status.DRAFT && c.status != Contest.Status.SCHEDULED) {
            return Response.status(400).entity(Map.of("error", "Non è possibile modificare un concorso già aperto o elaborato")).build();
        }
        if (req.name() != null) c.name = req.name();
        if (req.description() != null) c.description = req.description();
        if (req.openAt() != null) c.openAt = req.openAt();
        if (req.closeAt() != null) c.closeAt = req.closeAt();
        if (req.ruleId() != null) c.ruleId = req.ruleId();
        c.persist();
        return Response.ok(withCounts(c)).build();
    }

    @POST
    @Path("/{id}/open")
    @Transactional
    public Response open(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        auth.requireAdmin(token);
        Contest c = Contest.findById(id);
        if (c == null) throw new NotFoundException();
        if (c.status != Contest.Status.DRAFT && c.status != Contest.Status.SCHEDULED) {
            return Response.status(400).entity(Map.of("error", "Impossibile aprire un concorso in stato: " + c.status)).build();
        }
        c.status = Contest.Status.OPEN;
        c.persist();
        return Response.ok(withCounts(c)).build();
    }

    @POST
    @Path("/{id}/close")
    @Transactional
    public Response close(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        auth.requireAdmin(token);
        Contest c = Contest.findById(id);
        if (c == null) throw new NotFoundException();
        if (c.status != Contest.Status.OPEN) {
            return Response.status(400).entity(Map.of("error", "Il concorso deve essere aperto per poterlo chiudere")).build();
        }
        c.status = Contest.Status.CLOSED;
        c.persist();
        return Response.ok(withCounts(c)).build();
    }

    @POST
    @Path("/{id}/process")
    public Response process(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        auth.requireAdmin(token);
        Map<String, Object> result = couponEngine.processContest(id);
        Map<String, Integer> notifications = notificationService.sendContestNotifications(id);
        return Response.ok(Map.of("processing", result, "notifications", notifications)).build();
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response delete(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        auth.requireAdmin(token);
        Contest c = Contest.findById(id);
        if (c == null) throw new NotFoundException();
        if (c.status != Contest.Status.DRAFT && c.status != Contest.Status.CANCELLED) {
            return Response.status(400).entity(Map.of("error", "Solo i concorsi in bozza o annullati possono essere eliminati")).build();
        }
        long matchCount = Match.count("contestId", id);
        if (matchCount > 0) {
            return Response.status(409).entity(Map.of(
                "error", "Impossibile eliminare: il concorso contiene " + matchCount + " partite. Rimuovile prima."
            )).build();
        }
        c.delete();
        return Response.noContent().build();
    }
}
