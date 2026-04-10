package it.schedina.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "coupon_predictions")
public class CouponPrediction extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "coupon_id", nullable = false)
    public Long couponId;

    @Column(name = "match_id", nullable = false)
    public Long matchId;

    /** Sorted list of choices from {"1","X","2"}, e.g. ["1"] or ["1","X"] (doppia) */
    @Convert(converter = JsonListConverters.StringList.class)
    @Column(nullable = false, columnDefinition = "text")
    public List<String> choices = new ArrayList<>();

    @Column(name = "is_correct")
    public Boolean isCorrect;

    public static List<CouponPrediction> findByCoupon(Long couponId) {
        return find("couponId", couponId).list();
    }

    public static void deleteByCoupon(Long couponId) {
        delete("couponId", couponId);
    }
}
