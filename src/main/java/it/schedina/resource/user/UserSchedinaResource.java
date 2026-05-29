package it.schedina.resource.user;

import it.schedina.dto.SchedinaDto;
import it.schedina.entity.Schedina;
import it.schedina.entity.User;
import it.schedina.resource.admin.AdminSchedinaResource;
import it.schedina.service.AuthService;
import it.schedina.service.SchedinaScoringEngine;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Path("/schedine")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UserSchedinaResource {

    @Inject AuthService auth;
    @Inject SchedinaScoringEngine scoring;

    @GET
    @Transactional
    public List<SchedinaDto.SchedinaSummary> mine(@HeaderParam("Authorization") String token) {
        User user = auth.requireAuth(token);
        return Schedina.findByUser(user.id).stream()
                .map(SchedinaDto.SchedinaSummary::from).toList();
    }

    @POST
    @Transactional
    public Response create(@HeaderParam("Authorization") String token, @Valid SchedinaDto.CreateRequest req) {
        User user = auth.requireAuth(token);
        Schedina s = scoring.createSchedina(user.id, req);
        return Response.status(201).entity(SchedinaDto.SchedinaSummary.from(s)).build();
    }

    @GET
    @Path("/{id}")
    @Transactional
    public SchedinaDto.SchedinaDetail get(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        User user = auth.requireAuth(token);
        Schedina s = Schedina.findById(id);
        if (s == null || !s.userId.equals(user.id)) throw new NotFoundException();
        return AdminSchedinaResource.detailOf(s);
    }

    @POST
    @Path("/{id}/confirm")
    @Transactional
    public SchedinaDto.SchedinaSummary confirm(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        User user = auth.requireAuth(token);
        Schedina s = Schedina.findById(id);
        if (s == null || !s.userId.equals(user.id)) throw new NotFoundException();
        return SchedinaDto.SchedinaSummary.from(scoring.confirm(s));
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response cancel(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        User user = auth.requireAuth(token);
        Schedina s = Schedina.findById(id);
        if (s == null || !s.userId.equals(user.id)) throw new NotFoundException();
        if (s.status != Schedina.Status.DRAFT && s.status != Schedina.Status.CONFIRMED) {
            return Response.status(400).entity(Map.of("error", "Schedina non annullabile in stato " + s.status)).build();
        }
        s.status = Schedina.Status.CANCELLED;
        s.confirmedAt = s.confirmedAt != null ? s.confirmedAt : LocalDateTime.now();
        s.persist();
        return Response.noContent().build();
    }
}
