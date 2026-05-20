package it.schedina.dto;

import it.schedina.entity.*;
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

    public record SidePredictionRequest(
            @NotNull Long matchSidePredictionId,
            @NotNull String choice
    ) {}

    public record CouponRequest(
            @NotNull Long contestId,
            @NotEmpty List<PredictionRequest> predictions,
            /** Opzionale: pronostici extra per side bet (gol/no gol, primo marcatore) */
            List<SidePredictionRequest> sidePredictions
    ) {}

    public record PredictionResponse(
            Long id, Long matchId,
            String homeTeamName, String awayTeamName,
            List<String> choices, Boolean isCorrect,
            Integer homeScore, Integer awayScore, String officialResult
    ) {
        public static PredictionResponse from(CouponPrediction p) {
            Match m = Match.findById(p.matchId);
            String homeName = "?", awayName = "?";
            Integer homeScore = null, awayScore = null;
            String officialResult = null;
            if (m != null) {
                Team home = Team.findById(m.homeTeamId);
                Team away = Team.findById(m.awayTeamId);
                homeName       = home != null ? home.name : "?";
                awayName       = away != null ? away.name : "?";
                homeScore      = m.homeScore;
                awayScore      = m.awayScore;
                officialResult = m.officialResult;
            }
            return new PredictionResponse(p.id, p.matchId,
                    homeName, awayName,
                    p.choices, p.isCorrect,
                    homeScore, awayScore, officialResult);
        }
    }

    public record SidePredictionResponse(
            Long id,
            Long matchSidePredictionId,
            Long matchId,
            String betType,
            String betLabel,
            String choice,
            String choiceLabel,
            Boolean isCorrect,
            String officialResult,
            String officialResultLabel,
            String betStatus
    ) {
        public static SidePredictionResponse from(CouponSidePrediction csp) {
            MatchSidePrediction msp = MatchSidePrediction.findById(csp.matchSidePredictionId);
            String choiceLabel = labelFor(msp, csp.choice);
            String officialLabel = msp != null && msp.officialResult != null ? labelFor(msp, msp.officialResult) : null;
            return new SidePredictionResponse(
                    csp.id,
                    csp.matchSidePredictionId,
                    msp != null ? msp.matchId : null,
                    msp != null ? msp.betType.name() : null,
                    msp != null ? msp.label : null,
                    csp.choice,
                    choiceLabel,
                    csp.isCorrect,
                    msp != null ? msp.officialResult : null,
                    officialLabel,
                    msp != null ? msp.status.name() : null
            );
        }

        private static String labelFor(MatchSidePrediction msp, String ref) {
            if (msp == null || ref == null) return ref;
            if (msp.betType == MatchSidePrediction.BetType.GOAL_NOGOAL) return ref;
            if ("NONE".equalsIgnoreCase(ref)) return "Nessuno";
            try {
                Long pid = Long.parseLong(ref);
                Player p = Player.findById(pid);
                return p != null ? p.fullName() : ref;
            } catch (NumberFormatException e) {
                return ref;
            }
        }
    }

    public record CouponResponse(
            Long id, Long userId, Long contestId,
            LocalDateTime createdAt, LocalDateTime confirmedAt,
            Coupon.Status status, Integer correctCount, Boolean isWinner,
            List<PredictionResponse> predictions,
            List<SidePredictionResponse> sidePredictions
    ) {
        public static CouponResponse from(Coupon c, List<CouponPrediction> preds) {
            List<SidePredictionResponse> sides = CouponSidePrediction.findByCoupon(c.id)
                    .stream().map(SidePredictionResponse::from).toList();
            return new CouponResponse(c.id, c.userId, c.contestId,
                    c.createdAt, c.confirmedAt, c.status, c.correctCount, c.isWinner,
                    preds.stream().map(PredictionResponse::from).toList(),
                    sides);
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

    /** CouponSummary arricchito con info utente, per admin/mod */
    public record AdminCouponSummary(
            Long id, Long userId, String userName, String userEmail,
            Long contestId, LocalDateTime createdAt, LocalDateTime confirmedAt,
            Coupon.Status status, Integer correctCount, Boolean isWinner
    ) {}

    /** Una entry della pagina 'Schedine' raggruppata per concorso */
    public record ContestCoupons(
            Long contestId, String contestName, String contestStatus,
            long couponCount, List<?> coupons
    ) {}
}
