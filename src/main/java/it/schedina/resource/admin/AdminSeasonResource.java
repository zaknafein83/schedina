package it.schedina.resource.admin;

import it.schedina.dto.SeasonDto;
import it.schedina.entity.Season;
import it.schedina.service.AuthService;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;

@Path("/admin/seasons")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminSeasonResource {

    @Inject AuthService auth;

    @GET
    @Transactional
    public List<SeasonDto.SeasonResponse> list(@HeaderParam("Authorization") String token) {
        auth.requireAdminOrMod(token);
        return Season.<Season>find("order by startDate desc, id desc").list()
                .stream().map(SeasonDto.SeasonResponse::from).toList();
    }

    @GET
    @Path("/current")
    @Transactional
    public Response current(@HeaderParam("Authorization") String token) {
        auth.requireAuth(token);
        Season s = Season.findCurrent();
        if (s == null) return Response.status(404).entity(Map.of("error", "Nessuna stagione corrente")).build();
        return Response.ok(SeasonDto.SeasonResponse.from(s)).build();
    }

    @POST
    @Transactional
    public Response create(@HeaderParam("Authorization") String token, @Valid SeasonDto.SeasonRequest req) {
        auth.requireAdmin(token);
        if (Season.<Season>find("label", req.label()).firstResult() != null) {
            return Response.status(409).entity(Map.of("error", "Stagione con questa etichetta già esistente")).build();
        }
        Season s = new Season();
        s.label = req.label();
        s.startDate = req.startDate();
        s.endDate = req.endDate();
        if (Boolean.TRUE.equals(req.isCurrent())) {
            clearCurrent();
            s.isCurrent = true;
        }
        s.persist();
        return Response.status(201).entity(SeasonDto.SeasonResponse.from(s)).build();
    }

    @PATCH
    @Path("/{id}")
    @Transactional
    public SeasonDto.SeasonResponse update(
            @HeaderParam("Authorization") String token,
            @PathParam("id") Long id,
            SeasonDto.SeasonRequest req) {
        auth.requireAdmin(token);
        Season s = Season.findById(id);
        if (s == null) throw new NotFoundException();
        if (req.label() != null && !req.label().isBlank()) s.label = req.label();
        if (req.startDate() != null) s.startDate = req.startDate();
        if (req.endDate() != null) s.endDate = req.endDate();
        if (req.isCurrent() != null) {
            if (req.isCurrent() && !s.isCurrent) {
                clearCurrent();
                s.isCurrent = true;
            } else if (!req.isCurrent()) {
                s.isCurrent = false;
            }
        }
        s.persist();
        return SeasonDto.SeasonResponse.from(s);
    }

    @PATCH
    @Path("/{id}/set-current")
    @Transactional
    public SeasonDto.SeasonResponse setCurrent(
            @HeaderParam("Authorization") String token,
            @PathParam("id") Long id) {
        auth.requireAdmin(token);
        Season s = Season.findById(id);
        if (s == null) throw new NotFoundException();
        clearCurrent();
        s.isCurrent = true;
        s.persist();
        return SeasonDto.SeasonResponse.from(s);
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response delete(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        auth.requireAdmin(token);
        Season s = Season.findById(id);
        if (s == null) throw new NotFoundException();
        s.delete();
        return Response.noContent().build();
    }

    /** Resetta tutte le stagioni a non-corrente. Necessario prima di promuoverne una nuova. */
    private void clearCurrent() {
        Season.update("isCurrent = false where isCurrent = true");
        // flush per evitare la violazione del vincolo unique parziale durante il successivo persist
        Season.flush();
    }
}
