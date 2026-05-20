package it.schedina.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.util.List;

@Entity
@Table(name = "season_coupon_predictions")
public class SeasonCouponPrediction extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "season_coupon_id", nullable = false)
    public Long seasonCouponId;

    @Column(name = "season_bet_id", nullable = false)
    public Long seasonBetId;

    /** ID di Team o Player (a seconda del targetKind del SeasonBet), salvato come stringa. */
    @Column(name = "choice_ref", nullable = false, length = 50)
    public String choiceRef;

    @Column(name = "is_correct")
    public Boolean isCorrect;

    public static List<SeasonCouponPrediction> findByCoupon(Long couponId) {
        return find("seasonCouponId", couponId).list();
    }
}
