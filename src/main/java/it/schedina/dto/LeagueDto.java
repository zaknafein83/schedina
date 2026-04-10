package it.schedina.dto;

import it.schedina.entity.League;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

public final class LeagueDto {

    private LeagueDto() {}

    public record LeagueRequest(
            @NotBlank String name,
            String description,
            String country,
            Boolean isActive
    ) {}

    public record LeagueResponse(
            Long id, String name, String description, String country, boolean isActive, LocalDateTime createdAt
    ) {
        public static LeagueResponse from(League l) {
            return new LeagueResponse(l.id, l.name, l.description, l.country, l.isActive, l.createdAt);
        }
    }
}
