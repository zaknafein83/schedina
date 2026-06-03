package it.schedina.resource.admin;

import it.schedina.dto.ConcorsoDto;
import it.schedina.dto.MatchDto;
import it.schedina.entity.*;
import it.schedina.service.AuthService;
import it.schedina.service.NotificationService;
import it.schedina.service.SchedinaScoringEngine;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Concorso del Totocalcio: la schedina giocabile (turno + selezione partite + regola). */
@Path("/admin/concorsi")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminConcorsoResource {

    @Inject AuthService auth;
    @Inject SchedinaScoringEngine scoring;
    @Inject NotificationService notifications;
    @Inject it.schedina.service.MontepremiService montepremi;

    private ConcorsoDto.ConcorsoResponse resp(Concorso c) {
        Rule rule = c.ruleId != null ? Rule.findById(c.ruleId) : null;
        return ConcorsoDto.ConcorsoResponse.from(c, rule,
                Match.count("concorsoId", c.id), Schedina.count("concorsoId", c.id));
    }

    private MatchDto.MatchResponse enrich(Match m) {
        Team h = Team.findById(m.homeTeamId);
        Team a = Team.findById(m.awayTeamId);
        return MatchDto.MatchResponse.from(m, h != null ? h.name : "?", a != null ? a.name : "?");
    }

    @GET
    @Transactional
    public List<ConcorsoDto.ConcorsoResponse> list(@HeaderParam("Authorization") String token) {
        auth.requireAdminOrMod(token);
        return Concorso.allOrdered().stream().map(this::resp).toList();
    }

    @GET
    @Path("/{id}")
    @Transactional
    public ConcorsoDto.ConcorsoResponse get(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        auth.requireAdminOrMod(token);
        Concorso c = Concorso.findById(id);
        if (c == null) throw new NotFoundException();
        return resp(c);
    }

    /** Montepremi corrente {1x2, U/O} (fine catena dei concorsi elaborati). */
    @GET
    @Path("/montepremi/current")
    @Transactional
    public Map<String, Long> montepremiCurrent(@HeaderParam("Authorization") String token) {
        auth.requireAdminOrMod(token);
        long[] m = montepremi.current();
        return Map.of("montepremi1x2", m[0], "montepremiUo", m[1]);
    }

    /** Proiezione montepremi + premi per soglia del concorso (managed o legacy). */
    @GET
    @Path("/{id}/montepremi")
    @Transactional
    public it.schedina.service.MontepremiService.Projection montepremiProjection(
            @HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        auth.requireAdminOrMod(token);
        Concorso c = Concorso.findById(id);
        if (c == null) throw new NotFoundException();
        return montepremi.projectionFor(c);
    }

    @POST
    @Transactional
    public Response create(@HeaderParam("Authorization") String token, @Valid ConcorsoDto.ConcorsoRequest req) {
        auth.requireAdminOrMod(token);
        Concorso c = new Concorso();
        c.name = req.name();
        c.number = req.number();
        c.seasonId = req.seasonId();
        c.ruleId = req.ruleId();
        c.openAt = req.openAt();
        c.closeAt = req.closeAt();
        if (req.winningThresholds() != null) c.winningThresholds = new ArrayList<>(req.winningThresholds());
        c.persist();
        return Response.status(201).entity(resp(c)).build();
    }

    @PATCH
    @Path("/{id}")
    @Transactional
    public ConcorsoDto.ConcorsoResponse update(@HeaderParam("Authorization") String token,
            @PathParam("id") Long id, ConcorsoDto.ConcorsoRequest req) {
        auth.requireAdminOrMod(token);
        Concorso c = Concorso.findById(id);
        if (c == null) throw new NotFoundException();
        if (req.name() != null) c.name = req.name();
        if (req.number() != null) c.number = req.number();
        if (req.seasonId() != null) c.seasonId = req.seasonId();
        if (req.ruleId() != null) c.ruleId = req.ruleId();
        if (req.openAt() != null) c.openAt = req.openAt();
        if (req.closeAt() != null) c.closeAt = req.closeAt();
        if (req.winningThresholds() != null) c.winningThresholds = new ArrayList<>(req.winningThresholds());
        c.persist();
        // Soglie/regola possono essere cambiate → se già elaborato, rielabora per aggiornare le vincite.
        if (c.status == Concorso.Status.PROCESSED) {
            scoring.process(c);
            if (c.status == Concorso.Status.PROCESSED) notifications.sendConcorsoNotifications(c.id);
        }
        return resp(c);
    }

    // ----- Selezione partite -----

    @GET
    @Path("/{id}/matches")
    @Transactional
    public List<MatchDto.MatchResponse> selected(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        auth.requireAdminOrMod(token);
        return Match.findByConcorso(id).stream().map(this::enrich).toList();
    }

    /** Partite selezionabili: del numero di turno del concorso, non ancora assegnate. */
    @GET
    @Path("/{id}/available")
    @Transactional
    public List<MatchDto.MatchResponse> available(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        auth.requireAdminOrMod(token);
        Concorso c = Concorso.findById(id);
        if (c == null) throw new NotFoundException();
        List<Long> giornateIds = Giornata.findByNumber(c.number).stream().map(g -> g.id).toList();
        if (giornateIds.isEmpty()) return List.of();
        return Match.<Match>find("giornataId in ?1 and concorsoId is null order by id", giornateIds)
                .list().stream().map(this::enrich).toList();
    }

    @POST
    @Path("/{id}/matches")
    @Transactional
    public Response addMatch(@HeaderParam("Authorization") String token, @PathParam("id") Long id, Map<String, Long> body) {
        auth.requireAdminOrMod(token);
        Concorso c = Concorso.findById(id);
        if (c == null) throw new NotFoundException();
        Long matchId = body.get("matchId");
        Match m = matchId != null ? Match.findById(matchId) : null;
        if (m == null) return Response.status(404).entity(Map.of("error", "Partita non trovata")).build();
        Giornata g = m.giornataId != null ? Giornata.findById(m.giornataId) : null;
        if (g == null || g.number != c.number) {
            return Response.status(400).entity(Map.of("error", "La partita deve appartenere a una giornata del turno " + c.number)).build();
        }
        if (m.concorsoId != null && !m.concorsoId.equals(c.id)) {
            return Response.status(409).entity(Map.of("error", "Partita già in un altro concorso")).build();
        }
        m.concorsoId = c.id;
        m.persist();
        return Response.ok(enrich(m)).build();
    }

    @DELETE
    @Path("/{id}/matches/{matchId}")
    @Transactional
    public Response removeMatch(@HeaderParam("Authorization") String token,
            @PathParam("id") Long id, @PathParam("matchId") Long matchId) {
        auth.requireAdminOrMod(token);
        Match m = Match.findById(matchId);
        if (m == null || !id.equals(m.concorsoId)) throw new NotFoundException();
        m.concorsoId = null;
        m.persist();
        return Response.noContent().build();
    }

    // ----- Stato -----

    @POST
    @Path("/{id}/open")
    @Transactional
    public Response open(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        auth.requireAdminOrMod(token);
        Concorso c = Concorso.findById(id);
        if (c == null) throw new NotFoundException();
        if (Match.count("concorsoId", c.id) == 0) {
            return Response.status(400).entity(Map.of("error", "Il concorso non ha partite selezionate")).build();
        }
        c.status = Concorso.Status.OPEN;
        c.persist();
        return Response.ok(resp(c)).build();
    }

    @POST
    @Path("/{id}/close")
    @Transactional
    public Response close(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        auth.requireAdminOrMod(token);
        Concorso c = Concorso.findById(id);
        if (c == null) throw new NotFoundException();
        c.status = Concorso.Status.CLOSED;
        c.persist();
        return Response.ok(resp(c)).build();
    }

    @POST
    @Path("/{id}/reopen")
    @Transactional
    public Response reopen(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        auth.requireAdminOrMod(token);
        Concorso c = Concorso.findById(id);
        if (c == null) throw new NotFoundException();
        if (c.status != Concorso.Status.CLOSED && c.status != Concorso.Status.PROCESSED) {
            return Response.status(400).entity(Map.of("error", "Solo un concorso chiuso o elaborato può essere riaperto")).build();
        }
        c.status = Concorso.Status.OPEN;
        c.persist();
        return Response.ok(resp(c)).build();
    }

    @POST
    @Path("/{id}/process")
    @Transactional
    public Response process(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        auth.requireAdminOrMod(token);
        Concorso c = Concorso.findById(id);
        if (c == null) throw new NotFoundException();
        if (c.status != Concorso.Status.CLOSED && c.status != Concorso.Status.PROCESSED) {
            return Response.status(400).entity(Map.of("error", "Il concorso deve essere chiuso prima dell'elaborazione")).build();
        }
        Map<String, Object> result = new java.util.HashMap<>(scoring.process(c));
        if (c.status == Concorso.Status.PROCESSED) {
            result.put("notifications", notifications.sendConcorsoNotifications(c.id));
        }
        return Response.ok(result).build();
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response delete(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        auth.requireAdmin(token);
        Concorso c = Concorso.findById(id);
        if (c == null) throw new NotFoundException();
        if (Schedina.count("concorsoId", c.id) > 0) {
            return Response.status(409).entity(Map.of("error", "Concorso con schedine, impossibile eliminare")).build();
        }
        Match.update("concorsoId = null where concorsoId = ?1", c.id);
        c.delete();
        return Response.noContent().build();
    }
}
