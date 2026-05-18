package it.schedina.resource.admin;

import it.schedina.dto.CouponDto;
import it.schedina.entity.Contest;
import it.schedina.entity.Coupon;
import it.schedina.entity.CouponPrediction;
import it.schedina.entity.User;
import it.schedina.service.AuthService;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Path("/admin/coupons")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminCouponResource {

    @Inject AuthService auth;

    private CouponDto.AdminCouponSummary toAdminSummary(Coupon c, Map<Long, User> userCache) {
        User u = userCache.computeIfAbsent(c.userId, id -> User.findById(id));
        String name = u != null ? (u.firstName + " " + u.lastName).trim() : "(utente #" + c.userId + ")";
        String email = u != null ? u.email : null;
        return new CouponDto.AdminCouponSummary(
                c.id, c.userId, name, email,
                c.contestId, c.createdAt, c.confirmedAt,
                c.status, c.correctCount, c.isWinner);
    }

    /** Tutte le schedine di tutti gli utenti, raggruppate per concorso */
    @GET
    @Path("/by-contest")
    @Transactional
    public List<CouponDto.ContestCoupons> couponsByContest(@HeaderParam("Authorization") String token) {
        auth.requireAdminOrMod(token);
        Map<Long, List<CouponDto.AdminCouponSummary>> grouped = new HashMap<>();
        Map<Long, User> userCache = new HashMap<>();
        for (Coupon c : Coupon.<Coupon>listAll()) {
            grouped.computeIfAbsent(c.contestId, k -> new ArrayList<>())
                    .add(toAdminSummary(c, userCache));
        }
        List<CouponDto.ContestCoupons> result = new ArrayList<>();
        for (Map.Entry<Long, List<CouponDto.AdminCouponSummary>> e : grouped.entrySet()) {
            Contest contest = Contest.findById(e.getKey());
            String name = contest != null ? contest.name : "(concorso #" + e.getKey() + ")";
            String status = contest != null ? contest.status.name() : "UNKNOWN";
            result.add(new CouponDto.ContestCoupons(e.getKey(), name, status, e.getValue().size(), e.getValue()));
        }
        result.sort(Comparator.comparing(CouponDto.ContestCoupons::contestId).reversed());
        return result;
    }

    /** Dettaglio schedina (qualsiasi utente) */
    @GET
    @Path("/{id}")
    @Transactional
    public Response get(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        auth.requireAdminOrMod(token);
        Coupon c = Coupon.findById(id);
        if (c == null) throw new NotFoundException();
        return Response.ok(CouponDto.CouponResponse.from(c, CouponPrediction.findByCoupon(c.id))).build();
    }
}
