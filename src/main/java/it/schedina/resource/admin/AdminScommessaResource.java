package it.schedina.resource.admin;

import it.schedina.dto.ScommessaDto;
import it.schedina.entity.BetOption;
import it.schedina.entity.Giocata;
import it.schedina.entity.Scommessa;
import it.schedina.service.AuthService;
import it.schedina.service.ScommessaResolutionService;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

/** Catalogo delle scommesse di FINE CAMPIONATO (le scommesse di partita sono guidate dall'utente). */
@Path("/admin/scommesse")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminScommessaResource {

    @Inject AuthService auth;
    @Inject ScommessaResolutionService resolution;

    private ScommessaDto.ScommessaResponse resp(Scommessa b) {
        return ScommessaDto.ScommessaResponse.from(b, BetOption.findByBet(b.id));
    }

    @GET
    @Transactional
    public List<ScommessaDto.ScommessaResponse> list(
            @HeaderParam("Authorization") String token,
            @QueryParam("seasonId") Long seasonId) {
        auth.requireAdminOrMod(token);
        List<Scommessa> bets = seasonId != null ? Scommessa.findBySeason(seasonId) : Scommessa.listAll();
        return bets.stream().map(this::resp).toList();
    }

    @GET
    @Path("/{id}")
    @Transactional
    public ScommessaDto.ScommessaResponse get(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        auth.requireAdminOrMod(token);
        Scommessa b = Scommessa.findById(id);
        if (b == null) throw new NotFoundException();
        return resp(b);
    }

    @POST
    @Transactional
    public Response create(@HeaderParam("Authorization") String token, @Valid ScommessaDto.ScommessaRequest req) {
        auth.requireAdminOrMod(token);
        Scommessa b = resolution.create(req);
        return Response.status(201).entity(resp(b)).build();
    }

    @PATCH
    @Path("/{id}/resolve")
    @Transactional
    public ScommessaDto.ScommessaResponse resolve(@HeaderParam("Authorization") String token,
            @PathParam("id") Long id, @Valid ScommessaDto.ResolveRequest req) {
        auth.requireAdminOrMod(token);
        return resp(resolution.resolveManual(id, req.officialResultRef()));
    }

    @POST
    @Path("/{id}/unresolve")
    @Transactional
    public ScommessaDto.ScommessaResponse unresolve(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        auth.requireAdminOrMod(token);
        return resp(resolution.unresolve(id));
    }

    @POST
    @Path("/{id}/void")
    @Transactional
    public ScommessaDto.ScommessaResponse voidBet(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        auth.requireAdminOrMod(token);
        return resp(resolution.voidBet(id));
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response delete(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        auth.requireAdminOrMod(token);
        Scommessa b = Scommessa.findById(id);
        if (b == null) throw new NotFoundException();
        Giocata.delete("scommessaId", id);
        BetOption.deleteByBet(id);
        b.delete();
        return Response.noContent().build();
    }
}
