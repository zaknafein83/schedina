package it.schedina.dto;

import it.schedina.entity.Match;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public final class MatchDto {

    private MatchDto() {}

    public record MatchRequest(
            @NotNull Long homeTeamId,
            @NotNull Long awayTeamId,
            @NotNull Long giornataId,
            @NotNull LocalDateTime scheduledAt,
            Double overUnderLine
    ) {}

    public record MatchResultRequest(
            @NotNull Integer homeScore,
            @NotNull Integer awayScore
    ) {}

    public record FirstScorerRequest(Long playerId) {}

    public record MatchResponse(
            Long id, Long giornataId, Long concorsoId,
            Long homeTeamId, String homeTeamName,
            Long awayTeamId, String awayTeamName,
            Long leagueId, LocalDateTime scheduledAt, Match.Status status,
            Double overUnderLine, Integer homeScore, Integer awayScore,
            String result1x2, String resultUO, Long firstScorerPlayerId
    ) {
        public static MatchResponse from(Match m, String homeName, String awayName) {
            return new MatchResponse(m.id, m.giornataId, m.concorsoId, m.homeTeamId, homeName,
                    m.awayTeamId, awayName, m.leagueId, m.scheduledAt, m.status,
                    m.overUnderLine, m.homeScore, m.awayScore, m.result1x2(), m.resultUO(), m.firstScorerPlayerId);
        }
    }
}
