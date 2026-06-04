package it.schedina.dto;

import it.schedina.entity.Schedina;
import it.schedina.entity.User;
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
            @NotNull Long concorsoId,
            @NotEmpty List<PronosticoInput> pronostici
    ) {}

    public record SchedinaSummary(
            Long id, Long userId, String userEmail, String userUsername,
            String userFirstName, String userLastName, Long concorsoId, Schedina.Status status,
            Integer correctCount, Boolean isWinner,
            Integer correct1x2Count, Boolean isWinner1x2, Long prize1x2,
            Integer correctUoCount, Boolean isWinnerUo, Long prizeUo,
            LocalDateTime confirmedAt, LocalDateTime createdAt
    ) {
        public static SchedinaSummary from(Schedina s) {
            User u = User.findById(s.userId);
            return new SchedinaSummary(s.id, s.userId,
                    u != null ? u.email : null, u != null ? u.username : null,
                    u != null ? u.firstName : null, u != null ? u.lastName : null,
                    s.concorsoId, s.status,
                    s.correctCount, s.isWinner,
                    s.correct1x2Count, s.isWinner1x2, s.prize1x2,
                    s.correctUoCount, s.isWinnerUo, s.prizeUo,
                    s.confirmedAt, s.createdAt);
        }
    }

    /** Totale vinto da un utente (somma premi delle sue schedine), per la dashboard. */
    public record WinningsResponse(
            long total, long totalTotocalcio, long totalUnderOver, int schedineVincenti
    ) {}

    /** Riga della classifica giocatori: vincite totali aggregate per utente. */
    public record ClassificaRow(
            int rank, Long userId, String username, String fullName,
            long total, long totalTotocalcio, long totalUnderOver,
            int schedineVincenti, int schedineGiocate
    ) {}

    public record SelezioneResponse(
            Long matchId, String home, String away,
            String choice1x2, String choiceUo,
            Boolean correct1x2, Boolean correctUo,
            String result1x2, String resultUO, Double overUnderLine
    ) {}

    public record SchedinaDetail(
            Long id, Long userId, Long concorsoId, Schedina.Status status,
            Integer correctCount, Boolean isWinner,
            Integer correct1x2Count, Boolean isWinner1x2, Long prize1x2,
            Integer correctUoCount, Boolean isWinnerUo, Long prizeUo,
            List<SelezioneResponse> selezioni
    ) {}
}
