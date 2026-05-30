package it.schedina.dto;

import it.schedina.entity.Rule;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public final class RuleDto {

    private RuleDto() {}

    public record RuleRequest(
            @NotBlank String name,
            List<Integer> winningThresholds,
            Boolean isActive
    ) {}

    public record RuleResponse(
            Long id, String name, List<Integer> winningThresholds, boolean isActive
    ) {
        public static RuleResponse from(Rule r) {
            return new RuleResponse(r.id, r.name, r.winningThresholds, r.isActive);
        }
    }
}
