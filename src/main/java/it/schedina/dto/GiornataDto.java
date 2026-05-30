package it.schedina.dto;

import it.schedina.entity.Giornata;
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
            @NotNull LocalDateTime openAt,
            @NotNull LocalDateTime closeAt,
            List<Integer> winningThresholds
    ) {}

    public record GiornataResponse(
            Long id, Long seasonId, int number, String name,
            LocalDateTime openAt, LocalDateTime closeAt, Giornata.Status status,
            List<Integer> winningThresholds, long matchCount, long schedinaCount
    ) {
        public static GiornataResponse from(Giornata g, long matchCount, long schedinaCount) {
            return new GiornataResponse(g.id, g.seasonId, g.number, g.name,
                    g.openAt, g.closeAt, g.status, g.winningThresholds, matchCount, schedinaCount);
        }
    }
}
