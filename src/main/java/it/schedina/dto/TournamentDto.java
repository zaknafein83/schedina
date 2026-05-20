package it.schedina.dto;

import it.schedina.entity.Tournament;
import jakarta.validation.constraints.NotBlank;

public final class TournamentDto {

    private TournamentDto() {}

    public record TournamentRequest(
            @NotBlank String name,
            String type,
            String country,
            Boolean isActive
    ) {}

    public record TournamentResponse(
            Long id,
            String name,
            String type,
            String country,
            boolean isActive
    ) {
        public static TournamentResponse from(Tournament t) {
            return new TournamentResponse(
                    t.id, t.name,
                    t.type != null ? t.type.name() : null,
                    t.country, t.isActive);
        }
    }
}
