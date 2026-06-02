package it.schedina.dto;

import it.schedina.entity.Notification;

import java.time.LocalDateTime;

public final class NotificationDto {

    private NotificationDto() {}

    public record NotificationResponse(
            Long id, Long userId, Long schedinaId, int threshold, Notification.Game game,
            String message, Notification.Status status,
            LocalDateTime sentAt, LocalDateTime readAt, LocalDateTime createdAt
    ) {
        public static NotificationResponse from(Notification n) {
            return new NotificationResponse(n.id, n.userId, n.schedinaId, n.threshold, n.game,
                    n.message, n.status, n.sentAt, n.readAt, n.createdAt);
        }
    }
}
