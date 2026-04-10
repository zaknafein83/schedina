package it.schedina.resource.admin;

import it.schedina.dto.NotificationDto;
import it.schedina.entity.Notification;
import it.schedina.service.AuthService;
import it.schedina.service.NotificationService;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

@Path("/admin/notifications")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminNotificationResource {

    @Inject AuthService auth;
    @Inject NotificationService notificationService;

    @GET
    @Transactional
    public List<NotificationDto.NotificationResponse> list(
            @HeaderParam("Authorization") String token,
            @QueryParam("status") Notification.Status status,
            @QueryParam("userId") Long userId) {
        auth.requireAdmin(token);
        List<Notification> results;
        if (status != null && userId != null) {
            results = Notification.find("status = ?1 and userId = ?2", status, userId).list();
        } else if (status != null) {
            results = Notification.find("status", status).list();
        } else if (userId != null) {
            results = Notification.find("userId", userId).list();
        } else {
            results = Notification.listAll();
        }
        return results.stream().map(NotificationDto.NotificationResponse::from).toList();
    }

    @POST
    @Path("/{id}/resend")
    public Response resend(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        auth.requireAdmin(token);
        Notification n = notificationService.resend(id);
        return Response.ok(NotificationDto.NotificationResponse.from(n)).build();
    }
}
