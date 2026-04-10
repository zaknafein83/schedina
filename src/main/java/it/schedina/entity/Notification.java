package it.schedina.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "notifications")
public class Notification extends PanacheEntityBase {

    public enum Status { PENDING, SENT, FAILED, READ }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "user_id", nullable = false)
    public Long userId;

    @Column(name = "coupon_id", nullable = false)
    public Long couponId;

    @Column(nullable = false)
    public int threshold;

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

    public static boolean alreadyNotified(Long couponId, int threshold) {
        return count("couponId = ?1 and threshold = ?2 and status != ?3",
                couponId, threshold, Status.FAILED) > 0;
    }
}
