package it.schedina.resource.admin;

import it.schedina.entity.*;
import it.schedina.service.AuthService;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

@Path("/admin/dashboard")
@Produces(MediaType.APPLICATION_JSON)
public class AdminDashboardResource {

    @Inject AuthService auth;

    @GET
    @Transactional
    public Response dashboard(@HeaderParam("Authorization") String token) {
        auth.requireAdmin(token);

        long totalUsers  = User.count();
        long activeUsers = User.count("isActive", true);

        long totalCoupons   = Coupon.count();
        long winningCoupons = Coupon.count("status", Coupon.Status.WINNING);

        long openContests      = Contest.count("status", Contest.Status.OPEN);
        long processedContests = Contest.count("status", Contest.Status.PROCESSED);

        long matchesWithoutResult = Match.count("officialResult is null");

        long notifSent   = Notification.count("status", Notification.Status.SENT);
        long notifFailed = Notification.count("status", Notification.Status.FAILED);

        return Response.ok(Map.of(
                "users",    Map.of("total", totalUsers, "active", activeUsers),
                "coupons",  Map.of("total", totalCoupons, "winning", winningCoupons),
                "contests", Map.of("open", openContests, "processed", processedContests),
                "matchesWithoutResult", matchesWithoutResult,
                "notifications", Map.of("sent", notifSent, "failed", notifFailed)
        )).build();
    }
}
