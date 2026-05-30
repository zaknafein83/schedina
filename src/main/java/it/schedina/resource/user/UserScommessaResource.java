package it.schedina.resource.user;

import it.schedina.dto.GiocataDto;
import it.schedina.dto.ScommessaDto;
import it.schedina.entity.BetOption;
import it.schedina.entity.Giocata;
import it.schedina.entity.Scommessa;
import it.schedina.entity.User;
import it.schedina.service.AuthService;
import it.schedina.service.ScommessaResolutionService;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

@Path("/scommesse")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UserScommessaResource {

    @Inject AuthService auth;
    @Inject ScommessaResolutionService resolution;

    /** Scommesse aperte giocabili (fine stagione + di giornata). */
    @GET
    @Transactional
    public List<ScommessaDto.ScommessaResponse> open(@HeaderParam("Authorization") String token) {
        auth.requireAuth(token);
        return Scommessa.findOpen().stream()
                .map(b -> ScommessaDto.ScommessaResponse.from(b, BetOption.findByBet(b.id)))
                .toList();
    }

    /** Le giocate dell'utente. */
    @GET
    @Path("/mine")
    @Transactional
    public List<GiocataDto.GiocataResponse> mine(@HeaderParam("Authorization") String token) {
        User user = auth.requireAuth(token);
        return Giocata.findByUser(user.id).stream().map(this::toResponse).toList();
    }

    @POST
    @Transactional
    public Response place(@HeaderParam("Authorization") String token, @Valid GiocataDto.GiocataRequest req) {
        User user = auth.requireAuth(token);
        Giocata g = resolution.placeGiocata(user.id, req.scommessaId(), req.choiceRef());
        return Response.status(201).entity(toResponse(g)).build();
    }

    private GiocataDto.GiocataResponse toResponse(Giocata g) {
        Scommessa b = Scommessa.findById(g.scommessaId);
        String label = b != null ? b.label : "?";
        String choiceLabel = g.choiceRef;
        for (BetOption o : BetOption.findByBet(g.scommessaId)) {
            if (o.ref.equals(g.choiceRef)) { choiceLabel = o.label; break; }
        }
        return new GiocataDto.GiocataResponse(g.id, g.scommessaId, label,
                b != null ? b.scope : null, g.choiceRef, choiceLabel, g.isCorrect,
                b != null ? b.status : null, b != null ? b.officialResultRef : null);
    }
}
