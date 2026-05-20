package it.schedina.dto;

import it.schedina.entity.Player;
import it.schedina.entity.Team;
import jakarta.validation.constraints.NotBlank;

public final class PlayerDto {

    private PlayerDto() {}

    public record PlayerRequest(
            @NotBlank String firstName,
            @NotBlank String lastName,
            Long teamId,
            String role,
            Boolean isActive
    ) {}

    public record PlayerResponse(
            Long id,
            String firstName,
            String lastName,
            String fullName,
            Long teamId,
            String teamName,
            Long leagueId,
            String role,
            boolean isActive
    ) {
        public static PlayerResponse from(Player p) {
            Team t = p.teamId != null ? Team.<Team>findById(p.teamId) : null;
            return new PlayerResponse(
                    p.id,
                    p.firstName,
                    p.lastName,
                    p.fullName(),
                    p.teamId,
                    t != null ? t.name : null,
                    t != null ? t.leagueId : null,
                    p.role != null ? p.role.name() : null,
                    p.isActive
            );
        }
    }
}
