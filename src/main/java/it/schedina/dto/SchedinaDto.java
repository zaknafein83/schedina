package it.schedina.dto;

import it.schedina.entity.Schedina;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public final class SchedinaDto {

    private SchedinaDto() {}

    /** Pronostico su una partita: esito 1X2 + Under/Over. */
    public record PronosticoInput(
            @NotNull Long matchId,
            @NotBlank String choice1x2,
            @NotBlank String choiceUo
    ) {}

    public record CreateRequest(
            @NotNull Long giornataId,
            @NotEmpty List<PronosticoInput> pronostici
    ) {}

    public record SchedinaSummary(
            Long id, Long userId, Long giornataId, Schedina.Status status,
            Integer correctCount, Boolean isWinner,
            LocalDateTime confirmedAt, LocalDateTime createdAt
    ) {
        public static SchedinaSummary from(Schedina s) {
            return new SchedinaSummary(s.id, s.userId, s.giornataId, s.status,
                    s.correctCount, s.isWinner, s.confirmedAt, s.createdAt);
        }
    }

    public record SelezioneResponse(
            Long matchId, String home, String away,
            String choice1x2, String choiceUo,
            Boolean correct1x2, Boolean correctUo,
            String result1x2, String resultUO, Double overUnderLine
    ) {}

    public record SchedinaDetail(
            Long id, Long userId, Long giornataId, Schedina.Status status,
            Integer correctCount, Boolean isWinner, List<SelezioneResponse> selezioni
    ) {}
}
