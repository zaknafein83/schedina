package it.schedina.dto;

import it.schedina.entity.Match;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public final class MatchDto {

    private MatchDto() {}

    public record MatchRequest(
            @NotNull Long homeTeamId,
            @NotNull Long awayTeamId,
            Long contestId,
            @NotNull LocalDateTime scheduledAt
    ) {}

    /** Richiede il punteggio reale: 1/X/2 viene calcolato automaticamente */
    public record MatchResultRequest(
            @NotNull Integer homeScore,
            @NotNull Integer awayScore
    ) {}

    public record MatchResponse(
            Long id, Long homeTeamId, String homeTeamName,
            Long awayTeamId, String awayTeamName,
            Long leagueId, Long contestId,
            LocalDateTime scheduledAt, Match.Status status,
            Integer homeScore, Integer awayScore,
            String officialResult
    ) {
        public static MatchResponse from(Match m, String homeName, String awayName) {
            return new MatchResponse(m.id, m.homeTeamId, homeName,
                    m.awayTeamId, awayName, m.leagueId, m.contestId,
                    m.scheduledAt, m.status,
                    m.homeScore, m.awayScore,
                    m.officialResult);
        }
    }
}
