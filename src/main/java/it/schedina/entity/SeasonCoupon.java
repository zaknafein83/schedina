package it.schedina.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "season_coupons")
public class SeasonCoupon extends PanacheEntityBase {

    public enum Status {
        DRAFT, CONFIRMED, PROCESSED, WINNING, NOT_WINNING, CANCELLED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "user_id", nullable = false)
    public Long userId;

    @Column(name = "season_pool_id", nullable = false)
    public Long seasonPoolId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public Status status = Status.DRAFT;

    @Column(name = "correct_count")
    public Integer correctCount;

    @Column(name = "is_winner")
    public Boolean isWinner;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "confirmed_at")
    public LocalDateTime confirmedAt;

    public static List<SeasonCoupon> findByUser(Long userId) {
        return find("userId", userId).list();
    }

    public static SeasonCoupon findByUserAndPool(Long userId, Long poolId) {
        return find("userId = ?1 and seasonPoolId = ?2", userId, poolId).firstResult();
    }
}
