package it.schedina.dto;

import it.schedina.entity.Giornata;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class GiornataDto {

    private GiornataDto() {}

    public record GiornataRequest(
            @NotNull Long leagueId,
            @NotBlank String name,
            Integer number,
            Long seasonId
    ) {}

    public record GiornataResponse(
            Long id, Long leagueId, String leagueName, Long seasonId,
            int number, String name, long matchCount
    ) {
        public static GiornataResponse from(Giornata g, String leagueName, long matchCount) {
            return new GiornataResponse(g.id, g.leagueId, leagueName, g.seasonId, g.number, g.name, matchCount);
        }
    }
}
