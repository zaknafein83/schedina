package it.schedina.dto;

import it.schedina.entity.Rule;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;

public final class RuleDto {

    private RuleDto() {}

    public record RuleRequest(
            @NotBlank String name,
            List<Integer> winningThresholds,
            Map<Integer, Long> prizes,
            Boolean isActive
    ) {}

    public record RuleResponse(
            Long id, String name, List<Integer> winningThresholds,
            Map<Integer, Long> prizes, boolean isActive
    ) {
        public static RuleResponse from(Rule r) {
            return new RuleResponse(r.id, r.name, r.winningThresholds, r.prizes, r.isActive);
        }
    }
}
