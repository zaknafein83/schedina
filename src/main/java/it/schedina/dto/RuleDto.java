package it.schedina.dto;

import it.schedina.entity.Rule;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

import java.util.List;

public final class RuleDto {

    private RuleDto() {}

    public record RuleRequest(
            @NotBlank String name,
            String description,
            Long leagueId,
            @Positive int requiredBets,
            @NotEmpty List<Integer> winningThresholds,
            Integer maxSchedinePerUser,
            Boolean fullCompletionRequired,
            Boolean isActive
    ) {}

    public record RuleResponse(
            Long id, String name, String description, Long leagueId,
            int requiredBets, List<Integer> winningThresholds,
            Integer maxSchedinePerUser, boolean fullCompletionRequired, boolean isActive
    ) {
        public static RuleResponse from(Rule r) {
            return new RuleResponse(r.id, r.name, r.description, r.leagueId,
                    r.requiredBets, r.winningThresholds, r.maxSchedinePerUser,
                    r.fullCompletionRequired, r.isActive);
        }
    }
}
