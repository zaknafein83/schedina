package it.schedina.dto;

import it.schedina.entity.Notification;
import it.schedina.entity.User;

import java.time.LocalDateTime;

public final class NotificationDto {

    private NotificationDto() {}

    public record NotificationResponse(
            Long id, Long userId, String userEmail, String userUsername,
            Long schedinaId, int threshold, Notification.Game game,
            String message, Notification.Status status,
            LocalDateTime sentAt, LocalDateTime readAt, LocalDateTime createdAt
    ) {
        public static NotificationResponse from(Notification n) {
            User u = User.findById(n.userId);
            return new NotificationResponse(n.id, n.userId,
                    u != null ? u.email : null, u != null ? u.username : null,
                    n.schedinaId, n.threshold, n.game,
                    n.message, n.status, n.sentAt, n.readAt, n.createdAt);
        }
    }
}
