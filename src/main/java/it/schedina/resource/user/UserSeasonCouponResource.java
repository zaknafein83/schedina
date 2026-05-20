package it.schedina.resource.user;

import it.schedina.dto.SeasonCouponDto;
import it.schedina.dto.SeasonPoolDto;
import it.schedina.entity.SeasonBet;
import it.schedina.entity.SeasonCoupon;
import it.schedina.entity.SeasonPool;
import it.schedina.entity.User;
import it.schedina.service.AuthService;
import it.schedina.service.SeasonCouponEngine;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;

@Path("/season-coupons")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UserSeasonCouponResource {

    @Inject AuthService auth;
    @Inject SeasonCouponEngine engine;

    // Lista delle schedine stagionali dell'utente
    @GET
    @Transactional
    public List<SeasonCouponDto.CouponSummary> mine(@HeaderParam("Authorization") String token) {
        User user = auth.requireAuth(token);
        return SeasonCoupon.findByUser(user.id).stream()
                .map(SeasonCouponDto.CouponSummary::from).toList();
    }

    @GET
    @Path("/{id}")
    @Transactional
    public Response get(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        User user = auth.requireAuth(token);
        SeasonCoupon c = SeasonCoupon.findById(id);
        if (c == null || !c.userId.equals(user.id)) throw new NotFoundException();
        return Response.ok(SeasonCouponDto.CouponDetail.from(c)).build();
    }

    @POST
    @Transactional
    public Response create(@HeaderParam("Authorization") String token, @Valid SeasonCouponDto.CreateRequest req) {
        User user = auth.requireAuth(token);
        SeasonCoupon c = engine.createCoupon(user.id, req);
        return Response.status(201).entity(SeasonCouponDto.CouponDetail.from(c)).build();
    }

    @POST
    @Path("/{id}/confirm")
    @Transactional
    public Response confirm(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        User user = auth.requireAuth(token);
        SeasonCoupon c = SeasonCoupon.findById(id);
        if (c == null || !c.userId.equals(user.id)) throw new NotFoundException();
        SeasonCoupon confirmed = engine.confirmCoupon(c);
        return Response.ok(SeasonCouponDto.CouponDetail.from(confirmed)).build();
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response cancel(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        User user = auth.requireAuth(token);
        SeasonCoupon c = SeasonCoupon.findById(id);
        if (c == null || !c.userId.equals(user.id)) throw new NotFoundException();
        if (c.status != SeasonCoupon.Status.DRAFT) {
            return Response.status(400).entity(Map.of("error", "Solo le schedine in bozza possono essere annullate")).build();
        }
        c.status = SeasonCoupon.Status.CANCELLED;
        c.persist();
        return Response.noContent().build();
    }
}
