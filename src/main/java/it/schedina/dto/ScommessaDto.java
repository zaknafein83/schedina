package it.schedina.dto;

import it.schedina.entity.BetOption;
import it.schedina.entity.Scommessa;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public final class ScommessaDto {

    private ScommessaDto() {}

    public record OptionInput(@NotBlank String ref, String label, Integer displayOrder) {}

    public record ScommessaRequest(
            Long concorsoId,
            @NotBlank String label,
            @NotNull Scommessa.Market market,
            Long matchId,
            Long tournamentId,
            Long seasonId,
            Long leagueId,
            Double overUnderLine,
            /** Per i mercati TEAM/PLAYER vanno fornite; per i token (1X2/UO/GG) sono autogenerate. */
            List<OptionInput> options
    ) {}

    public record ResolveRequest(@NotBlank String officialResultRef) {}

    public record OptionResponse(Long id, String ref, String label, int displayOrder) {
        public static OptionResponse from(BetOption o) {
            return new OptionResponse(o.id, o.ref, o.label, o.displayOrder);
        }
    }

    public record ScommessaResponse(
            Long id, Long concorsoId, String label,
            Scommessa.Market market, Scommessa.TargetKind targetKind,
            Long matchId, Long tournamentId, Long seasonId, Long leagueId,
            Double overUnderLine, Scommessa.ResolutionMode resolutionMode,
            Scommessa.Status status, String officialResultRef,
            List<OptionResponse> options
    ) {
        public static ScommessaResponse from(Scommessa b, List<BetOption> opts) {
            return new ScommessaResponse(
                    b.id, b.concorsoId, b.label, b.market, b.targetKind(),
                    b.matchId, b.tournamentId, b.seasonId, b.leagueId,
                    b.overUnderLine, b.resolutionMode, b.status, b.officialResultRef,
                    opts.stream().map(OptionResponse::from).toList());
        }
    }
}
