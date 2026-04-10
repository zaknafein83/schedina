package it.schedina.dto;

import it.schedina.entity.Team;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class TeamDto {

    private TeamDto() {}

    public record TeamRequest(
            @NotBlank String name,
            String shortName,
            @NotNull Long leagueId,
            Boolean isActive
    ) {}

    public record TeamResponse(Long id, String name, String shortName, Long leagueId, boolean isActive) {
        public static TeamResponse from(Team t) {
            return new TeamResponse(t.id, t.name, t.shortName, t.leagueId, t.isActive);
        }
    }
}
