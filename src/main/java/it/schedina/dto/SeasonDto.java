package it.schedina.dto;

import it.schedina.entity.Season;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public final class SeasonDto {

    private SeasonDto() {}

    public record SeasonRequest(
            @NotBlank String label,
            LocalDate startDate,
            LocalDate endDate,
            Boolean isCurrent
    ) {}

    public record SeasonResponse(
            Long id,
            String label,
            LocalDate startDate,
            LocalDate endDate,
            boolean isCurrent
    ) {
        public static SeasonResponse from(Season s) {
            return new SeasonResponse(s.id, s.label, s.startDate, s.endDate, s.isCurrent);
        }
    }
}
