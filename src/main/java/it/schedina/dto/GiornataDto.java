package it.schedina.dto;

import it.schedina.entity.Giornata;
import it.schedina.entity.Rule;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public final class GiornataDto {

    private GiornataDto() {}

    public record GiornataRequest(
            @NotBlank String name,
            Integer number,
            Long seasonId,
            Long ruleId,
            @NotNull LocalDateTime openAt,
            @NotNull LocalDateTime closeAt,
            List<Integer> winningThresholds
    ) {}

    public record GiornataResponse(
            Long id, Long seasonId, Long ruleId, String ruleName, int number, String name,
            LocalDateTime openAt, LocalDateTime closeAt, Giornata.Status status,
            List<Integer> winningThresholds, long matchCount, long schedinaCount
    ) {
        public static GiornataResponse from(Giornata g, Rule rule, long matchCount, long schedinaCount) {
            // Soglie effettive: quelle della regola se assegnata, altrimenti quelle locali (legacy/fallback).
            List<Integer> thresholds = rule != null ? rule.winningThresholds : g.winningThresholds;
            return new GiornataResponse(g.id, g.seasonId, g.ruleId, rule != null ? rule.name : null,
                    g.number, g.name, g.openAt, g.closeAt, g.status, thresholds, matchCount, schedinaCount);
        }
    }
}
