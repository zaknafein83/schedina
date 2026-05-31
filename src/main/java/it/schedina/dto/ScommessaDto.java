package it.schedina.dto;

import it.schedina.entity.BetOption;
import it.schedina.entity.Scommessa;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Scommesse di FINE CAMPIONATO (catalogo). Le scommesse di partita usano {@link GiocataPartitaDto}. */
public final class ScommessaDto {

    private ScommessaDto() {}

    public record OptionInput(@NotBlank String ref, String label, Integer displayOrder) {}

    public record ScommessaRequest(
            @NotBlank String label,
            @NotNull Scommessa.Market market,
            Long seasonId,
            Long tournamentId,
            Long leagueId,
            List<OptionInput> options
    ) {}

    public record ResolveRequest(@NotBlank String officialResultRef) {}

    /** L'admin dichiara il risultato per stagione+lega+mercato (self-service: nessun catalogo). */
    public record SeasonResultRequest(
            Long seasonId,
            @NotNull Long leagueId,
            @NotNull Scommessa.Market market,
            @NotBlank String officialResultRef
    ) {}

    public record OptionResponse(Long id, String ref, String label, int displayOrder) {
        public static OptionResponse from(BetOption o) {
            return new OptionResponse(o.id, o.ref, o.label, o.displayOrder);
        }
    }

    public record ScommessaResponse(
            Long id, String label, Scommessa.Market market, Scommessa.TargetKind targetKind,
            Long seasonId, Long leagueId, String leagueName,
            Scommessa.Status status, String officialResultRef, String officialResultLabel, long giocateCount
    ) {
        public static ScommessaResponse from(Scommessa b, String leagueName, String officialResultLabel, long giocateCount) {
            return new ScommessaResponse(b.id, b.label, b.market, b.targetKind(),
                    b.seasonId, b.leagueId, leagueName,
                    b.status, b.officialResultRef, officialResultLabel, giocateCount);
        }
    }
}
