package it.schedina.dto;

import it.schedina.entity.Rule;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public final class RuleDto {

    private RuleDto() {}

    public record RuleRequest(
            @NotBlank String name,
            String description,
            @NotNull Long leagueId,
            @Positive int requiredMatches,
            @NotEmpty List<Integer> winningThresholds,
            Integer maxCouponsPerUser,
            int maxDoubles,
            int maxTriples,
            Boolean fullCompletionRequired,
            Boolean isActive
    ) {}

    public record RuleResponse(
            Long id, String name, String description, Long leagueId,
            int requiredMatches, List<Integer> winningThresholds,
            Integer maxCouponsPerUser, int maxDoubles, int maxTriples,
            boolean fullCompletionRequired, boolean isActive
    ) {
        public static RuleResponse from(Rule r) {
            return new RuleResponse(r.id, r.name, r.description, r.leagueId,
                    r.requiredMatches, r.winningThresholds, r.maxCouponsPerUser,
                    r.maxDoubles, r.maxTriples, r.fullCompletionRequired, r.isActive);
        }
    }
}
