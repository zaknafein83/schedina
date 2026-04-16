package it.schedina.resource.admin;

import it.schedina.dto.LeagueDto;
import it.schedina.entity.*;
import it.schedina.service.AuthService;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;

@Path("/admin/leagues")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminLeagueResource {

    @Inject AuthService auth;

    @GET
    @Transactional
    public List<LeagueDto.LeagueResponse> list(@HeaderParam("Authorization") String token) {
        auth.requireAdmin(token);
        return League.<League>listAll().stream().map(LeagueDto.LeagueResponse::from).toList();
    }

    @POST
    @Transactional
    public Response create(@HeaderParam("Authorization") String token, @Valid LeagueDto.LeagueRequest req) {
        auth.requireAdmin(token);
        League l = new League();
        l.name = req.name();
        l.description = req.description();
        l.country = req.country();
        if (req.isActive() != null) l.isActive = req.isActive();
        l.persist();
        return Response.status(201).entity(LeagueDto.LeagueResponse.from(l)).build();
    }

    @GET
    @Path("/{id}")
    @Transactional
    public LeagueDto.LeagueResponse get(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        auth.requireAdmin(token);
        League l = League.findById(id);
        if (l == null) throw new NotFoundException();
        return LeagueDto.LeagueResponse.from(l);
    }

    @PATCH
    @Path("/{id}")
    @Transactional
    public LeagueDto.LeagueResponse update(
            @HeaderParam("Authorization") String token,
            @PathParam("id") Long id,
            LeagueDto.LeagueRequest req) {
        auth.requireAdmin(token);
        League l = League.findById(id);
        if (l == null) throw new NotFoundException();
        if (req.name() != null) l.name = req.name();
        if (req.description() != null) l.description = req.description();
        if (req.country() != null) l.country = req.country();
        if (req.isActive() != null) l.isActive = req.isActive();
        l.persist();
        return LeagueDto.LeagueResponse.from(l);
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response delete(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        auth.requireAdmin(token);
        League l = League.findById(id);
        if (l == null) throw new NotFoundException();

        // 1. Elimina concorsi e relativi dati (partite, schedine, previsioni)
        List<Contest> contests = Contest.<Contest>find("leagueId", id).list();
        for (Contest c : contests) {
            List<Match> matches = Match.findByContest(c.id);
            for (Match m : matches) m.delete();
            List<Coupon> coupons = Coupon.<Coupon>find("contestId", c.id).list();
            for (Coupon coupon : coupons) {
                CouponPrediction.delete("couponId", coupon.id);
                coupon.delete();
            }
            c.delete();
        }

        // 2. Elimina le squadre collegate
        Team.delete("leagueId", id);

        // 3. Elimina le regole collegate
        Rule.delete("leagueId", id);

        // 4. Elimina la lega
        l.delete();

        return Response.noContent().build();
    }
}
