package it.schedina.dto;

import it.schedina.entity.MatchSidePrediction;
import it.schedina.entity.Player;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

public final class MatchSideBetDto {

    private MatchSideBetDto() {}

    public record SideBetRequest(
            @NotBlank String betType,
            String label
    ) {}

    public record SideBetResponse(
            Long id,
            Long matchId,
            String betType,
            String label,
            String status,
            String officialResult,
            String officialResultLabel,
            LocalDateTime resolvedAt
    ) {
        public static SideBetResponse from(MatchSidePrediction m) {
            String label = resolveOfficialLabel(m);
            return new SideBetResponse(
                    m.id, m.matchId, m.betType.name(), m.label,
                    m.status.name(), m.officialResult, label, m.resolvedAt
            );
        }

        private static String resolveOfficialLabel(MatchSidePrediction m) {
            if (m.officialResult == null || m.officialResult.isBlank()) return null;
            if (m.betType == MatchSidePrediction.BetType.GOAL_NOGOAL) {
                return m.officialResult; // GOAL o NOGOAL
            }
            // FIRST_SCORER → playerId o "NONE"
            if ("NONE".equalsIgnoreCase(m.officialResult)) return "Nessuno";
            try {
                Long pid = Long.parseLong(m.officialResult);
                Player p = Player.findById(pid);
                return p != null ? p.fullName() : m.officialResult;
            } catch (NumberFormatException e) {
                return m.officialResult;
            }
        }
    }

    public record ResolveRequest(@NotBlank String officialResult) {}
}
