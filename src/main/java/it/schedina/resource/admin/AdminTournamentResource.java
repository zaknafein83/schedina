package it.schedina.resource.admin;

import it.schedina.dto.TournamentDto;
import it.schedina.entity.Tournament;
import it.schedina.service.AuthService;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;

@Path("/admin/tournaments")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminTournamentResource {

    @Inject AuthService auth;

    @GET
    @Transactional
    public List<TournamentDto.TournamentResponse> list(
            @HeaderParam("Authorization") String token,
            @QueryParam("type") String type,
            @QueryParam("isActive") Boolean isActive) {
        auth.requireAdminOrMod(token);
        return Tournament.<Tournament>find("order by type, name").list().stream()
                .filter(t -> type == null || (t.type != null && t.type.name().equalsIgnoreCase(type)))
                .filter(t -> isActive == null || t.isActive == isActive)
                .map(TournamentDto.TournamentResponse::from)
                .toList();
    }

    @GET
    @Path("/{id}")
    @Transactional
    public TournamentDto.TournamentResponse get(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        auth.requireAdminOrMod(token);
        Tournament t = Tournament.findById(id);
        if (t == null) throw new NotFoundException();
        return TournamentDto.TournamentResponse.from(t);
    }

    @POST
    @Transactional
    public Response create(@HeaderParam("Authorization") String token, @Valid TournamentDto.TournamentRequest req) {
        auth.requireAdmin(token);
        if (Tournament.<Tournament>find("name", req.name()).firstResult() != null) {
            return Response.status(409).entity(Map.of("error", "Torneo con questo nome già esistente")).build();
        }
        Tournament t = new Tournament();
        t.name = req.name();
        t.type = parseType(req.type());
        t.country = req.country();
        if (req.isActive() != null) t.isActive = req.isActive();
        t.persist();
        return Response.status(201).entity(TournamentDto.TournamentResponse.from(t)).build();
    }

    @PATCH
    @Path("/{id}")
    @Transactional
    public TournamentDto.TournamentResponse update(
            @HeaderParam("Authorization") String token,
            @PathParam("id") Long id,
            TournamentDto.TournamentRequest req) {
        auth.requireAdmin(token);
        Tournament t = Tournament.findById(id);
        if (t == null) throw new NotFoundException();
        if (req.name() != null && !req.name().isBlank()) t.name = req.name();
        if (req.type() != null) t.type = parseType(req.type());
        if (req.country() != null) t.country = req.country();
        if (req.isActive() != null) t.isActive = req.isActive();
        t.persist();
        return TournamentDto.TournamentResponse.from(t);
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response delete(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        auth.requireAdmin(token);
        Tournament t = Tournament.findById(id);
        if (t == null) throw new NotFoundException();
        t.delete();
        return Response.noContent().build();
    }

    private Tournament.Type parseType(String s) {
        if (s == null || s.isBlank()) {
            throw new BadRequestException("Type obbligatorio (ammessi: LEAGUE_NATIONAL, CUP_NATIONAL, CUP_INTERNATIONAL)");
        }
        try { return Tournament.Type.valueOf(s.toUpperCase()); }
        catch (IllegalArgumentException e) {
            throw new BadRequestException("Type non valido: " + s);
        }
    }
}
