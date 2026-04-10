package it.schedina.resource.user;

import it.schedina.dto.NotificationDto;
import it.schedina.entity.Notification;
import it.schedina.entity.User;
import it.schedina.service.AuthService;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Path("/notifications")
@Produces(MediaType.APPLICATION_JSON)
public class UserNotificationResource {

    @Inject AuthService auth;

    @GET
    @Transactional
    public List<NotificationDto.NotificationResponse> mine(@HeaderParam("Authorization") String token) {
        User user = auth.requireAuth(token);
        return Notification.findByUser(user.id).stream()
                .map(NotificationDto.NotificationResponse::from).toList();
    }

    @POST
    @Path("/{id}/read")
    @Transactional
    public Response markRead(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        User user = auth.requireAuth(token);
        Notification n = Notification.findById(id);
        if (n == null || !n.userId.equals(user.id)) throw new NotFoundException();
        if (n.status != Notification.Status.READ) {
            n.status = Notification.Status.READ;
            n.readAt = LocalDateTime.now();
            n.persist();
        }
        return Response.ok(NotificationDto.NotificationResponse.from(n)).build();
    }
}
