package it.schedina.resource.user;

import it.schedina.dto.ConcorsoDto;
import it.schedina.dto.ScommessaDto;
import it.schedina.entity.BetOption;
import it.schedina.entity.Concorso;
import it.schedina.entity.Schedina;
import it.schedina.entity.Scommessa;
import it.schedina.service.AuthService;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/concorsi")
@Produces(MediaType.APPLICATION_JSON)
public class UserConcorsoResource {

    @Inject AuthService auth;

    private ConcorsoDto.ConcorsoResponse resp(Concorso c) {
        long bets = Scommessa.count("concorsoId", c.id);
        long sched = Schedina.count("concorsoId", c.id);
        return ConcorsoDto.ConcorsoResponse.from(c, bets, sched);
    }

    @GET
    @Transactional
    public List<ConcorsoDto.ConcorsoResponse> listOpen(@HeaderParam("Authorization") String token) {
        auth.requireAuth(token);
        return Concorso.findOpen().stream().map(this::resp).toList();
    }

    @GET
    @Path("/{id}")
    @Transactional
    public ConcorsoDto.ConcorsoResponse get(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        auth.requireAuth(token);
        Concorso c = Concorso.findById(id);
        if (c == null) throw new NotFoundException();
        return resp(c);
    }

    @GET
    @Path("/{id}/scommesse")
    @Transactional
    public List<ScommessaDto.ScommessaResponse> scommesse(
            @HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        auth.requireAuth(token);
        Concorso c = Concorso.findById(id);
        if (c == null) throw new NotFoundException();
        return Scommessa.findByConcorso(id).stream()
                .map(b -> ScommessaDto.ScommessaResponse.from(b, BetOption.findByBet(b.id)))
                .toList();
    }
}
