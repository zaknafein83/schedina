package it.schedina.resource.user;

import it.schedina.dto.CouponDto;
import it.schedina.entity.Coupon;
import it.schedina.entity.CouponPrediction;
import it.schedina.entity.User;
import it.schedina.service.AuthService;
import it.schedina.service.CouponEngine;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;

@Path("/coupons")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UserCouponResource {

    @Inject AuthService auth;
    @Inject CouponEngine couponEngine;

    private CouponDto.CouponResponse toResponse(Coupon c) {
        return CouponDto.CouponResponse.from(c, CouponPrediction.findByCoupon(c.id));
    }

    @GET
    @Transactional
    public List<CouponDto.CouponSummary> myCoupons(@HeaderParam("Authorization") String token) {
        User user = auth.requireAuth(token);
        return Coupon.findByUser(user.id).stream().map(CouponDto.CouponSummary::from).toList();
    }

    @POST
    @Transactional
    public Response create(@HeaderParam("Authorization") String token, @Valid CouponDto.CouponRequest req) {
        User user = auth.requireAuth(token);
        Coupon coupon = couponEngine.createCoupon(user.id, req);
        return Response.status(201).entity(toResponse(coupon)).build();
    }

    @GET
    @Path("/{id}")
    @Transactional
    public Response get(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        User user = auth.requireAuth(token);
        Coupon c = Coupon.findById(id);
        if (c == null || !c.userId.equals(user.id)) throw new NotFoundException();
        return Response.ok(toResponse(c)).build();
    }

    @POST
    @Path("/{id}/confirm")
    @Transactional
    public Response confirm(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        User user = auth.requireAuth(token);
        Coupon c = Coupon.findById(id);
        if (c == null || !c.userId.equals(user.id)) throw new NotFoundException();
        Coupon confirmed = couponEngine.confirmCoupon(c);
        return Response.ok(toResponse(confirmed)).build();
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response cancel(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        User user = auth.requireAuth(token);
        Coupon c = Coupon.findById(id);
        if (c == null || !c.userId.equals(user.id)) throw new NotFoundException();
        if (c.status != Coupon.Status.DRAFT) {
            return Response.status(400).entity(Map.of("error", "Solo le schedine in bozza possono essere annullate")).build();
        }
        c.status = Coupon.Status.CANCELLED;
        c.persist();
        return Response.noContent().build();
    }
}
