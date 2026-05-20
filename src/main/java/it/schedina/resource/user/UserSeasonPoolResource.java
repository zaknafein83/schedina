package it.schedina.resource.user;

import it.schedina.dto.SeasonPoolDto;
import it.schedina.entity.SeasonBet;
import it.schedina.entity.SeasonPool;
import it.schedina.service.AuthService;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;

/** Endpoint read-only sulla pool stagionale aperta per gli utenti. */
@Path("/season-pools")
@Produces(MediaType.APPLICATION_JSON)
public class UserSeasonPoolResource {

    @Inject AuthService auth;

    /** Le pool aperte (in pratica una sola). */
    @GET
    @Transactional
    public List<SeasonPoolDto.PoolResponse> listOpen(@HeaderParam("Authorization") String token) {
        auth.requireAuth(token);
        return SeasonPool.<SeasonPool>find("status", SeasonPool.Status.OPEN).list().stream()
                .map(SeasonPoolDto.PoolResponse::from).toList();
    }

    @GET
    @Path("/{id}")
    @Transactional
    public Response get(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        auth.requireAuth(token);
        SeasonPool p = SeasonPool.findById(id);
        if (p == null) throw new NotFoundException();
        return Response.ok(SeasonPoolDto.PoolResponse.from(p)).build();
    }

    /** Lista dei bet configurati nella pool — necessario per compilare la schedina. */
    @GET
    @Path("/{id}/bets")
    @Transactional
    public List<SeasonPoolDto.BetResponse> bets(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        auth.requireAuth(token);
        SeasonPool p = SeasonPool.findById(id);
        if (p == null) throw new NotFoundException();
        return SeasonBet.findByPool(id).stream().map(SeasonPoolDto.BetResponse::from).toList();
    }
}
