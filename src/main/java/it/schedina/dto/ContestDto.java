package it.schedina.dto;

import it.schedina.entity.Contest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public final class ContestDto {

    private ContestDto() {}

    public record ContestRequest(
            @NotBlank String name,
            String description,
            @NotNull Long leagueId,
            @NotNull Long ruleId,
            @NotNull LocalDateTime openAt,
            @NotNull LocalDateTime closeAt
    ) {}

    public record ContestResponse(
            Long id, String name, String description,
            Long leagueId, Long ruleId,
            LocalDateTime openAt, LocalDateTime closeAt,
            Contest.Status status, LocalDateTime createdAt,
            long matchCount, long couponCount
    ) {
        public static ContestResponse from(Contest c, long matchCount, long couponCount) {
            return new ContestResponse(c.id, c.name, c.description, c.leagueId, c.ruleId,
                    c.openAt, c.closeAt, c.status, c.createdAt, matchCount, couponCount);
        }
    }
}
