package it.schedina.dto;

import it.schedina.entity.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public final class SeasonCouponDto {

    private SeasonCouponDto() {}

    public record PredictionInput(
            @NotNull Long seasonBetId,
            @NotBlank String choiceRef
    ) {}

    public record CreateRequest(
            @NotNull Long seasonPoolId,
            @Valid @NotNull List<PredictionInput> predictions
    ) {}

    public record PredictionDetail(
            Long id,
            Long seasonBetId,
            String betLabel,
            String betType,
            String targetKind,
            String tournamentName,
            String choiceRef,
            String choiceLabel,
            Boolean isCorrect,
            String officialResultRef,
            String officialResultLabel,
            String betStatus
    ) {
        public static PredictionDetail from(SeasonCouponPrediction p) {
            SeasonBet bet = SeasonBet.findById(p.seasonBetId);
            String choiceLabel = labelFor(bet != null ? bet.targetKind() : null, p.choiceRef);
            String resultLabel = bet != null && bet.officialResultRef != null
                    ? labelFor(bet.targetKind(), bet.officialResultRef) : null;
            Tournament t = bet != null ? Tournament.<Tournament>findById(bet.tournamentId) : null;
            return new PredictionDetail(
                    p.id, p.seasonBetId,
                    bet != null ? bet.label : null,
                    bet != null ? bet.betType.name() : null,
                    bet != null ? bet.targetKind().name() : null,
                    t != null ? t.name : null,
                    p.choiceRef,
                    choiceLabel,
                    p.isCorrect,
                    bet != null ? bet.officialResultRef : null,
                    resultLabel,
                    bet != null ? bet.status.name() : null
            );
        }

        private static String labelFor(SeasonBet.TargetKind kind, String ref) {
            if (kind == null || ref == null || ref.isBlank()) return ref;
            try {
                Long id = Long.parseLong(ref);
                if (kind == SeasonBet.TargetKind.TEAM) {
                    Team t = Team.findById(id);
                    return t != null ? t.name : ref;
                } else {
                    Player p = Player.findById(id);
                    return p != null ? p.fullName() : ref;
                }
            } catch (NumberFormatException e) {
                return ref;
            }
        }
    }

    public record CouponSummary(
            Long id,
            Long seasonPoolId,
            String poolName,
            String status,
            Integer correctCount,
            Boolean isWinner,
            LocalDateTime createdAt,
            LocalDateTime confirmedAt
    ) {
        public static CouponSummary from(SeasonCoupon c) {
            SeasonPool p = SeasonPool.findById(c.seasonPoolId);
            return new CouponSummary(
                    c.id, c.seasonPoolId,
                    p != null ? p.name : null,
                    c.status.name(),
                    c.correctCount, c.isWinner,
                    c.createdAt, c.confirmedAt
            );
        }
    }

    public record CouponDetail(
            Long id,
            Long seasonPoolId,
            String poolName,
            String status,
            Integer correctCount,
            Boolean isWinner,
            LocalDateTime createdAt,
            LocalDateTime confirmedAt,
            List<PredictionDetail> predictions
    ) {
        public static CouponDetail from(SeasonCoupon c) {
            SeasonPool p = SeasonPool.findById(c.seasonPoolId);
            List<PredictionDetail> preds = SeasonCouponPrediction.findByCoupon(c.id).stream()
                    .map(PredictionDetail::from).toList();
            return new CouponDetail(
                    c.id, c.seasonPoolId,
                    p != null ? p.name : null,
                    c.status.name(),
                    c.correctCount, c.isWinner,
                    c.createdAt, c.confirmedAt,
                    preds
            );
        }
    }
}
