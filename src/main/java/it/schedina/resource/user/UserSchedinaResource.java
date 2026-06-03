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
        return Schedina.findByUser(user.id).stream().map(SchedinaDto.SchedinaSummary::from).toList();
    }

    /** Totale vinto dall'utente: somma dei premi (1X2 + U/O) delle sue schedine. */
    @GET
    @Path("/winnings")
    @Transactional
    public SchedinaDto.WinningsResponse winnings(@HeaderParam("Authorization") String token) {
        User user = auth.requireAuth(token);
        long t1 = 0, tu = 0;
        int vincenti = 0;
        for (Schedina s : Schedina.findByUser(user.id)) {
            if (s.prize1x2 != null) t1 += s.prize1x2;
            if (s.prizeUo != null) tu += s.prizeUo;
            if (Boolean.TRUE.equals(s.isWinner)) vincenti++;
        }
        return new SchedinaDto.WinningsResponse(t1 + tu, t1, tu, vincenti);
    }

    /** Classifica giocatori per vincite totali (somma premi 1X2 + U/O su tutte le schedine giocate). */
    @GET
    @Path("/classifica")
    @Transactional
    public List<SchedinaDto.ClassificaRow> classifica(@HeaderParam("Authorization") String token) {
        auth.requireAuth(token);
        var giocate = List.of(Schedina.Status.CONFIRMED, Schedina.Status.PROCESSED,
                Schedina.Status.WINNING, Schedina.Status.NOT_WINNING);
        // Aggrega per utente: [totale1x2, totaleUo, schedineVincenti, schedineGiocate].
        Map<Long, long[]> agg = new java.util.HashMap<>();
        for (Schedina s : Schedina.<Schedina>find("status in ?1", giocate).list()) {
            long[] a = agg.computeIfAbsent(s.userId, k -> new long[4]);
            a[0] += s.prize1x2 != null ? s.prize1x2 : 0;
            a[1] += s.prizeUo != null ? s.prizeUo : 0;
            if (Boolean.TRUE.equals(s.isWinner)) a[2]++;
            a[3]++;
        }
        List<SchedinaDto.ClassificaRow> rows = new java.util.ArrayList<>();
        for (var e : agg.entrySet()) {
            User u = User.findById(e.getKey());
            long[] a = e.getValue();
            String fullName = u != null ? ((u.firstName != null ? u.firstName : "") + " "
                    + (u.lastName != null ? u.lastName : "")).trim() : null;
            rows.add(new SchedinaDto.ClassificaRow(0, e.getKey(), u != null ? u.username : null, fullName,
                    a[0] + a[1], a[0], a[1], (int) a[2], (int) a[3]));
        }
        // Ordina per totale desc, poi per schedine vincenti desc, poi per username.
        rows.sort((x, y) -> {
            int c = Long.compare(y.total(), x.total());
            if (c != 0) return c;
            c = Integer.compare(y.schedineVincenti(), x.schedineVincenti());
            if (c != 0) return c;
            return String.valueOf(x.username()).compareToIgnoreCase(String.valueOf(y.username()));
        });
        List<SchedinaDto.ClassificaRow> ranked = new java.util.ArrayList<>(rows.size());
        int pos = 1;
        for (SchedinaDto.ClassificaRow r : rows) {
            ranked.add(new SchedinaDto.ClassificaRow(pos++, r.userId(), r.username(), r.fullName(),
                    r.total(), r.totalTotocalcio(), r.totalUnderOver(), r.schedineVincenti(), r.schedineGiocate()));
        }
        return ranked;
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
        if (s.confirmedAt == null) s.confirmedAt = LocalDateTime.now();
        s.persist();
        return Response.noContent().build();
    }
}
