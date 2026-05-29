package it.schedina.dto;

import it.schedina.entity.Schedina;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public final class SchedinaDto {

    private SchedinaDto() {}

    public record SelezioneInput(@NotNull Long betId, @NotBlank String choiceRef) {}

    public record CreateRequest(
            @NotNull Long concorsoId,
            @NotEmpty List<SelezioneInput> selezioni
    ) {}

    public record SelezioneResponse(
            Long betId, String betLabel, String choiceRef, String choiceLabel,
            Boolean isCorrect, String officialResultRef
    ) {}

    public record SchedinaSummary(
            Long id, Long userId, Long concorsoId, Schedina.Status status,
            Integer correctCount, Boolean isWinner,
            LocalDateTime confirmedAt, LocalDateTime createdAt
    ) {
        public static SchedinaSummary from(Schedina s) {
            return new SchedinaSummary(s.id, s.userId, s.concorsoId, s.status,
                    s.correctCount, s.isWinner, s.confirmedAt, s.createdAt);
        }
    }

    public record SchedinaDetail(
            Long id, Long userId, Long concorsoId, Schedina.Status status,
            Integer correctCount, Boolean isWinner, LocalDateTime confirmedAt,
            List<SelezioneResponse> selezioni
    ) {}
}
