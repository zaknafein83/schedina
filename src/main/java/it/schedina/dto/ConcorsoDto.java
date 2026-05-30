package it.schedina.dto;

import it.schedina.entity.Concorso;
import it.schedina.entity.Rule;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public final class ConcorsoDto {

    private ConcorsoDto() {}

    public record ConcorsoRequest(
            @NotBlank String name,
            @NotNull Integer number,
            Long seasonId,
            Long ruleId,
            @NotNull LocalDateTime openAt,
            @NotNull LocalDateTime closeAt,
            List<Integer> winningThresholds
    ) {}

    public record ConcorsoResponse(
            Long id, Long seasonId, Long ruleId, String ruleName, int number, String name,
            LocalDateTime openAt, LocalDateTime closeAt, Concorso.Status status,
            List<Integer> winningThresholds, long matchCount, long schedinaCount
    ) {
        public static ConcorsoResponse from(Concorso c, Rule rule, long matchCount, long schedinaCount) {
            List<Integer> thresholds = rule != null ? rule.winningThresholds : c.winningThresholds;
            return new ConcorsoResponse(c.id, c.seasonId, c.ruleId, rule != null ? rule.name : null,
                    c.number, c.name, c.openAt, c.closeAt, c.status, thresholds, matchCount, schedinaCount);
        }
    }
}
