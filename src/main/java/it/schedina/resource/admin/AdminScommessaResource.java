package it.schedina.resource.admin;

import it.schedina.dto.ScommessaDto;
import it.schedina.entity.*;
import it.schedina.service.AuthService;
import it.schedina.service.ScommessaResolutionService;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

/**
 * Scommesse di FINE CAMPIONATO: self-service (le apre l'utente scegliendo lega+mercato+bersaglio).
 * Qui l'admin vede le scommesse esistenti e ne dichiara il risultato ufficiale.
 */
@Path("/admin/scommesse")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminScommessaResource {

    @Inject AuthService auth;
    @Inject ScommessaResolutionService resolution;

    private ScommessaDto.ScommessaResponse resp(Scommessa b) {
        League l = b.leagueId != null ? League.findById(b.leagueId) : null;
        long count = Giocata.count("scommessaId", b.id);
        String resultLabel = b.officialResultRef != null ? targetLabel(b, b.officialResultRef) : null;
        return ScommessaDto.ScommessaResponse.from(b, l != null ? l.name : null, resultLabel, count);
    }

    /** Etichetta leggibile del bersaglio (giocatore o squadra) a partire dal ref. */
    private String targetLabel(Scommessa b, String ref) {
        if (b.targetKind() == Scommessa.TargetKind.PLAYER) {
            Player p = Player.findById(parseLong(ref));
            return p != null ? p.firstName + " " + p.lastName : ref;
        }
        if (b.targetKind() == Scommessa.TargetKind.TEAM) {
            Team t = Team.findById(parseLong(ref));
            return t != null ? t.name : ref;
        }
        return ref;
    }

    private long parseLong(String s) {
        try { return Long.parseLong(s); } catch (Exception e) { return -1L; }
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

    /** Dichiara il risultato ufficiale per stagione+lega+mercato (crea la scommessa se non esiste) e risolve le giocate. */
    @POST
    @Path("/result")
    @Transactional
    public ScommessaDto.ScommessaResponse setResult(@HeaderParam("Authorization") String token,
            @Valid ScommessaDto.SeasonResultRequest req) {
        auth.requireAdminOrMod(token);
        return resp(resolution.setSeasonResult(req.seasonId(), req.leagueId(), req.market(), req.officialResultRef()));
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
        b.delete();
        return Response.noContent().build();
    }
}
