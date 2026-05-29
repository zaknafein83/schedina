package it.schedina.dto;

import it.schedina.entity.Concorso;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public final class ConcorsoDto {

    private ConcorsoDto() {}

    public record ConcorsoRequest(
            @NotBlank String name,
            String description,
            Concorso.Kind kind,
            @NotNull Long ruleId,
            @NotNull LocalDateTime openAt,
            @NotNull LocalDateTime closeAt
    ) {}

    public record ConcorsoResponse(
            Long id, String name, String description, Concorso.Kind kind,
            Long ruleId, LocalDateTime openAt, LocalDateTime closeAt,
            Concorso.Status status, long betCount, long schedinaCount
    ) {
        public static ConcorsoResponse from(Concorso c, long betCount, long schedinaCount) {
            return new ConcorsoResponse(c.id, c.name, c.description, c.kind,
                    c.ruleId, c.openAt, c.closeAt, c.status, betCount, schedinaCount);
        }
    }
}
