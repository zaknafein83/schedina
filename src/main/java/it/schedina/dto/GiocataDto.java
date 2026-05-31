package it.schedina.dto;

import it.schedina.entity.Scommessa;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class GiocataDto {

    private GiocataDto() {}

    public record GiocataRequest(
            @NotNull Long scommessaId,
            @NotBlank String choiceRef
    ) {}

    /** Giocata di fine campionato self-service: lega + mercato + bersaglio (id giocatore/squadra). */
    public record GiocataStagioneRequest(
            Long seasonId,
            @NotNull Long leagueId,
            @NotNull Scommessa.Market market,
            @NotBlank String prediction
    ) {}

    public record GiocataResponse(
            Long id, Long scommessaId, String scommessaLabel, Scommessa.Market market,
            String choiceRef, String choiceLabel,
            Boolean isCorrect, Scommessa.Status scommessaStatus, String officialResultRef
    ) {}
}
