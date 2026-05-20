package it.schedina.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.util.List;

@Entity
@Table(name = "coupon_side_predictions")
public class CouponSidePrediction extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "coupon_id", nullable = false)
    public Long couponId;

    @Column(name = "match_side_prediction_id", nullable = false)
    public Long matchSidePredictionId;

    /** Stringa: "GOAL"/"NOGOAL" o playerId */
    @Column(nullable = false, length = 50)
    public String choice;

    @Column(name = "is_correct")
    public Boolean isCorrect;

    public static List<CouponSidePrediction> findByCoupon(Long couponId) {
        return find("couponId", couponId).list();
    }
}
