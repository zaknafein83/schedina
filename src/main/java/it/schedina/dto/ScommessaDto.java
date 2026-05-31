package it.schedina.dto;

import it.schedina.entity.Scommessa;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Scommesse di FINE CAMPIONATO (self-service per lega): le apre l'utente scegliendo lega+mercato+bersaglio,
 * l'admin ne dichiara il risultato. Le scommesse di partita usano {@link GiocataPartitaDto}.
 */
public final class ScommessaDto {

    private ScommessaDto() {}

    /** L'admin dichiara il risultato per stagione+lega+mercato (self-service: nessun catalogo). */
    public record SeasonResultRequest(
            Long seasonId,
            @NotNull Long leagueId,
            @NotNull Scommessa.Market market,
            @NotBlank String officialResultRef
    ) {}

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
