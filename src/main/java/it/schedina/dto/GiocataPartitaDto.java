package it.schedina.dto;

import it.schedina.entity.Scommessa;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class GiocataPartitaDto {

    private GiocataPartitaDto() {}

    public record GiocataPartitaRequest(
            @NotNull Long matchId,
            @NotNull Scommessa.Market market,
            @NotBlank String prediction
    ) {}

    public record GiocataPartitaResponse(
            Long id, Long matchId, String home, String away,
            Scommessa.Market market, String prediction, String predictionLabel, Boolean isCorrect
    ) {}
}
