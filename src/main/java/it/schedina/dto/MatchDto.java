package it.schedina.dto;

import it.schedina.entity.Match;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public final class MatchDto {

    private MatchDto() {}

    public record MatchRequest(
            @NotNull Long homeTeamId,
            @NotNull Long awayTeamId,
            @NotNull LocalDateTime scheduledAt
    ) {}

    /** Punteggio reale: risolve automaticamente le scommesse AUTO collegate. */
    public record MatchResultRequest(
            @NotNull Integer homeScore,
            @NotNull Integer awayScore
    ) {}

    public record MatchResponse(
            Long id, Long homeTeamId, String homeTeamName,
            Long awayTeamId, String awayTeamName,
            Long leagueId, LocalDateTime scheduledAt, Match.Status status,
            Integer homeScore, Integer awayScore
    ) {
        public static MatchResponse from(Match m, String homeName, String awayName) {
            return new MatchResponse(m.id, m.homeTeamId, homeName,
                    m.awayTeamId, awayName, m.leagueId,
                    m.scheduledAt, m.status, m.homeScore, m.awayScore);
        }
    }
}
