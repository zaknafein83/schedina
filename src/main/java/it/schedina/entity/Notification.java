package it.schedina.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "notifications")
public class Notification extends PanacheEntityBase {

    public enum Status { PENDING, SENT, FAILED, READ }

    /** Gioco a cui si riferisce la notifica (post split V22). null = notifica legacy (combinato). */
    public enum Game { TOTOCALCIO, UNDER_OVER }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "user_id", nullable = false)
    public Long userId;

    @Column(name = "schedina_id", nullable = false)
    public Long schedinaId;

    /** Risultati esatti del gioco vinto (post split). Legacy: punteggio combinato. */
    @Column(nullable = false)
    public int threshold;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    public Game game;

    @Column(nullable = false, columnDefinition = "text")
    public String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public Status status = Status.PENDING;

    @Column(name = "sent_at")
    public LocalDateTime sentAt;

    @Column(name = "read_at")
    public LocalDateTime readAt;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt = LocalDateTime.now();

    public static List<Notification> findByUser(Long userId) {
        return find("userId", userId).list();
    }

    public static boolean alreadyNotifiedForGame(Long schedinaId, Game game) {
        return count("schedinaId = ?1 and game = ?2 and status != ?3",
                schedinaId, game, Status.FAILED) > 0;
    }

    public static List<Notification> findBySchedina(Long schedinaId) {
        return find("schedinaId", schedinaId).list();
    }
}
