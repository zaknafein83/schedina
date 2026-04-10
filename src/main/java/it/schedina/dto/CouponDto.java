package it.schedina.dto;

import it.schedina.entity.Coupon;
import it.schedina.entity.CouponPrediction;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public final class CouponDto {

    private CouponDto() {}

    public record PredictionRequest(
            @NotNull Long matchId,
            /** Choices from {"1","X","2"}. Multiple = doppia/tripla. */
            @NotEmpty List<String> choices
    ) {}

    public record CouponRequest(
            @NotNull Long contestId,
            @NotEmpty List<PredictionRequest> predictions
    ) {}

    public record PredictionResponse(
            Long id, Long matchId, List<String> choices, Boolean isCorrect
    ) {
        public static PredictionResponse from(CouponPrediction p) {
            return new PredictionResponse(p.id, p.matchId, p.choices, p.isCorrect);
        }
    }

    public record CouponResponse(
            Long id, Long userId, Long contestId,
            LocalDateTime createdAt, LocalDateTime confirmedAt,
            Coupon.Status status, Integer correctCount, Boolean isWinner,
            List<PredictionResponse> predictions
    ) {
        public static CouponResponse from(Coupon c, List<CouponPrediction> preds) {
            return new CouponResponse(c.id, c.userId, c.contestId,
                    c.createdAt, c.confirmedAt, c.status, c.correctCount, c.isWinner,
                    preds.stream().map(PredictionResponse::from).toList());
        }
    }

    public record CouponSummary(
            Long id, Long contestId, LocalDateTime createdAt,
            LocalDateTime confirmedAt, Coupon.Status status,
            Integer correctCount, Boolean isWinner
    ) {
        public static CouponSummary from(Coupon c) {
            return new CouponSummary(c.id, c.contestId, c.createdAt,
                    c.confirmedAt, c.status, c.correctCount, c.isWinner);
        }
    }
}
