package it.schedina.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "coupons")
public class Coupon extends PanacheEntityBase {

    public enum Status {
        DRAFT, CONFIRMED, PENDING_RESULT, PROCESSED, WINNING, NOT_WINNING, CANCELLED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "user_id", nullable = false)
    public Long userId;

    @Column(name = "contest_id", nullable = false)
    public Long contestId;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "confirmed_at")
    public LocalDateTime confirmedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public Status status = Status.DRAFT;

    @Column(name = "correct_count")
    public Integer correctCount;

    @Column(name = "is_winner")
    public Boolean isWinner;

    // --- Queries ---

    public static List<Coupon> findByUser(Long userId) {
        return find("userId", userId).list();
    }

    public static List<Coupon> findConfirmedByContest(Long contestId) {
        return find("contestId = ?1 and (status = ?2 or status = ?3)",
                contestId, Status.CONFIRMED, Status.PENDING_RESULT).list();
    }

    public static long countActiveByUserAndContest(Long userId, Long contestId) {
        return count("userId = ?1 and contestId = ?2 and status != ?3",
                userId, contestId, Status.CANCELLED);
    }
}
