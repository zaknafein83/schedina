package it.schedina.dto;

import it.schedina.entity.Season;
import it.schedina.entity.SeasonBet;
import it.schedina.entity.SeasonPool;
import it.schedina.entity.Tournament;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public final class SeasonPoolDto {

    private SeasonPoolDto() {}

    public record PoolRequest(
            @NotNull Long seasonId,
            @NotBlank String name,
            String description,
            LocalDateTime openAt,
            LocalDateTime closeAt,
            List<Integer> winningThresholds
    ) {}

    public record PoolResponse(
            Long id,
            Long seasonId,
            String seasonLabel,
            String name,
            String description,
            LocalDateTime openAt,
            LocalDateTime closeAt,
            String status,
            List<Integer> winningThresholds,
            int betsCount,
            int resolvedBetsCount
    ) {
        public static PoolResponse from(SeasonPool p) {
            Season s = Season.findById(p.seasonId);
            long total = SeasonBet.count("seasonPoolId", p.id);
            long resolved = SeasonBet.count("seasonPoolId = ?1 and status = ?2",
                    p.id, SeasonBet.Status.RESOLVED);
            return new PoolResponse(
                    p.id, p.seasonId, s != null ? s.label : null,
                    p.name, p.description, p.openAt, p.closeAt,
                    p.status.name(), p.winningThresholds,
                    (int) total, (int) resolved
            );
        }
    }

    public record BetRequest(
            @NotNull Long tournamentId,
            @NotBlank String betType,
            String label
    ) {}

    public record BetResponse(
            Long id,
            Long seasonPoolId,
            Long tournamentId,
            String tournamentName,
            String betType,
            String targetKind,
            String label,
            String status,
            String officialResultRef,
            String officialResultLabel,
            LocalDateTime resolvedAt
    ) {
        public static BetResponse from(SeasonBet b) {
            Tournament t = Tournament.findById(b.tournamentId);
            String resultLabel = resolveLabel(b);
            return new BetResponse(
                    b.id, b.seasonPoolId, b.tournamentId,
                    t != null ? t.name : null,
                    b.betType.name(),
                    b.targetKind().name(),
                    b.label,
                    b.status.name(),
                    b.officialResultRef,
                    resultLabel,
                    b.resolvedAt
            );
        }

        private static String resolveLabel(SeasonBet b) {
            if (b.officialResultRef == null || b.officialResultRef.isBlank()) return null;
            try {
                Long id = Long.parseLong(b.officialResultRef);
                if (b.targetKind() == SeasonBet.TargetKind.TEAM) {
                    var team = it.schedina.entity.Team.<it.schedina.entity.Team>findById(id);
                    return team != null ? team.name : null;
                } else {
                    var player = it.schedina.entity.Player.<it.schedina.entity.Player>findById(id);
                    return player != null ? player.fullName() : null;
                }
            } catch (NumberFormatException e) {
                return b.officialResultRef;
            }
        }
    }

    public record ResolveBetRequest(@NotBlank String officialResultRef) {}
}
