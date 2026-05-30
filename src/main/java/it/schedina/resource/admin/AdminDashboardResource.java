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

        long totalUsers = User.count();
        long activeUsers = User.count("isActive", true);
        long totalSchedine = Schedina.count();
        long winningSchedine = Schedina.count("status", Schedina.Status.WINNING);
        long openConcorsi = Concorso.count("status", Concorso.Status.OPEN);
        long processedConcorsi = Concorso.count("status", Concorso.Status.PROCESSED);
        long openBets = Scommessa.count("status", Scommessa.Status.OPEN);
        long giocate = Giocata.count() + GiocataPartita.count();
        long notifSent = Notification.count("status", Notification.Status.SENT);
        long notifFailed = Notification.count("status", Notification.Status.FAILED);

        return Response.ok(Map.of(
                "users",     Map.of("total", totalUsers, "active", activeUsers),
                "schedine",  Map.of("total", totalSchedine, "winning", winningSchedine),
                "concorsi",  Map.of("open", openConcorsi, "processed", processedConcorsi),
                "scommesse", Map.of("open", openBets, "giocate", giocate),
                "notifications", Map.of("sent", notifSent, "failed", notifFailed)
        )).build();
    }
}
