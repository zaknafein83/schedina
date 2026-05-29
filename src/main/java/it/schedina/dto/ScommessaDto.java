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
            @NotNull Scommessa.Scope scope,
            @NotBlank String label,
            @NotNull Scommessa.Market market,
            Long seasonId,
            Long giornataId,
            Long matchId,
            Long leagueId,
            Long tournamentId,
            List<OptionInput> options
    ) {}

    public record ResolveRequest(@NotBlank String officialResultRef) {}

    public record OptionResponse(Long id, String ref, String label, int displayOrder) {
        public static OptionResponse from(BetOption o) {
            return new OptionResponse(o.id, o.ref, o.label, o.displayOrder);
        }
    }

    public record ScommessaResponse(
            Long id, Scommessa.Scope scope, String label,
            Scommessa.Market market, Scommessa.TargetKind targetKind,
            Long seasonId, Long giornataId, Long matchId, Long tournamentId, Long leagueId,
            Scommessa.ResolutionMode resolutionMode, Scommessa.Status status,
            String officialResultRef, List<OptionResponse> options
    ) {
        public static ScommessaResponse from(Scommessa b, List<BetOption> opts) {
            return new ScommessaResponse(b.id, b.scope, b.label, b.market, b.targetKind(),
                    b.seasonId, b.giornataId, b.matchId, b.tournamentId, b.leagueId,
                    b.resolutionMode, b.status, b.officialResultRef,
                    opts.stream().map(OptionResponse::from).toList());
        }
    }
}
