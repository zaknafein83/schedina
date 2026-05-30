package it.schedina.resource.admin;

import it.schedina.dto.GiornataDto;
import it.schedina.entity.Giornata;
import it.schedina.entity.League;
import it.schedina.entity.Match;
import it.schedina.service.AuthService;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;

/** Calendario: giornate di campionato per-lega (solo riferimento delle partite). */
@Path("/admin/giornate")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminGiornataResource {

    @Inject AuthService auth;

    private GiornataDto.GiornataResponse resp(Giornata g) {
        League l = League.findById(g.leagueId);
        return GiornataDto.GiornataResponse.from(g, l != null ? l.name : "?", Match.count("giornataId", g.id));
    }

    @GET
    @Transactional
    public List<GiornataDto.GiornataResponse> list(
            @HeaderParam("Authorization") String token,
            @QueryParam("leagueId") Long leagueId,
            @QueryParam("number") Integer number) {
        auth.requireAdminOrMod(token);
        List<Giornata> all;
        if (leagueId != null) all = Giornata.findByLeague(leagueId);
        else if (number != null) all = Giornata.findByNumber(number);
        else all = Giornata.allOrdered();
        return all.stream().map(this::resp).toList();
    }

    @GET
    @Path("/{id}")
    @Transactional
    public GiornataDto.GiornataResponse get(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        auth.requireAdminOrMod(token);
        Giornata g = Giornata.findById(id);
        if (g == null) throw new NotFoundException();
        return resp(g);
    }

    @POST
    @Transactional
    public Response create(@HeaderParam("Authorization") String token, @Valid GiornataDto.GiornataRequest req) {
        auth.requireAdminOrMod(token);
        if (League.findById(req.leagueId()) == null) {
            return Response.status(404).entity(Map.of("error", "Lega non trovata")).build();
        }
        Giornata g = new Giornata();
        g.leagueId = req.leagueId();
        g.name = req.name();
        g.seasonId = req.seasonId();
        g.number = req.number() != null ? req.number()
                : (int) (Giornata.count("leagueId", req.leagueId()) + 1);
        g.persist();
        return Response.status(201).entity(resp(g)).build();
    }

    @PATCH
    @Path("/{id}")
    @Transactional
    public GiornataDto.GiornataResponse update(@HeaderParam("Authorization") String token,
            @PathParam("id") Long id, GiornataDto.GiornataRequest req) {
        auth.requireAdminOrMod(token);
        Giornata g = Giornata.findById(id);
        if (g == null) throw new NotFoundException();
        if (req.leagueId() != null) g.leagueId = req.leagueId();
        if (req.name() != null) g.name = req.name();
        if (req.number() != null) g.number = req.number();
        if (req.seasonId() != null) g.seasonId = req.seasonId();
        g.persist();
        return resp(g);
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response delete(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        auth.requireAdminOrMod(token);
        Giornata g = Giornata.findById(id);
        if (g == null) throw new NotFoundException();
        if (Match.count("giornataId", g.id) > 0) {
            return Response.status(409).entity(Map.of("error", "Giornata con partite, eliminale prima")).build();
        }
        g.delete();
        return Response.noContent().build();
    }
}
