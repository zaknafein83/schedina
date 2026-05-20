package it.schedina.resource.admin;

import it.schedina.dto.SeasonPoolDto;
import it.schedina.entity.*;
import it.schedina.service.AuthService;
import it.schedina.service.SeasonCouponEngine;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Path("/admin/season-pools")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminSeasonPoolResource {

    @Inject AuthService auth;
    @Inject SeasonCouponEngine engine;

    // ─── Pool CRUD ───────────────────────────────────────────────────────────

    @GET
    @Transactional
    public List<SeasonPoolDto.PoolResponse> list(
            @HeaderParam("Authorization") String token,
            @QueryParam("seasonId") Long seasonId,
            @QueryParam("status") String status) {
        auth.requireAdminOrMod(token);
        return SeasonPool.<SeasonPool>find("order by id desc").list().stream()
                .filter(p -> seasonId == null || seasonId.equals(p.seasonId))
                .filter(p -> status == null || p.status.name().equalsIgnoreCase(status))
                .map(SeasonPoolDto.PoolResponse::from)
                .toList();
    }

    @GET
    @Path("/{id}")
    @Transactional
    public SeasonPoolDto.PoolResponse get(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        auth.requireAdminOrMod(token);
        SeasonPool p = SeasonPool.findById(id);
        if (p == null) throw new NotFoundException();
        return SeasonPoolDto.PoolResponse.from(p);
    }

    @POST
    @Transactional
    public Response create(@HeaderParam("Authorization") String token, @Valid SeasonPoolDto.PoolRequest req) {
        auth.requireAdminOrMod(token);
        if (Season.findById(req.seasonId()) == null) {
            return Response.status(404).entity(Map.of("error", "Stagione non trovata")).build();
        }
        SeasonPool p = new SeasonPool();
        p.seasonId = req.seasonId();
        p.name = req.name();
        p.description = req.description();
        p.openAt = req.openAt();
        p.closeAt = req.closeAt();
        if (req.winningThresholds() != null) p.winningThresholds = req.winningThresholds();
        p.persist();
        return Response.status(201).entity(SeasonPoolDto.PoolResponse.from(p)).build();
    }

    @PATCH
    @Path("/{id}")
    @Transactional
    public Response update(@HeaderParam("Authorization") String token, @PathParam("id") Long id, SeasonPoolDto.PoolRequest req) {
        auth.requireAdminOrMod(token);
        SeasonPool p = SeasonPool.findById(id);
        if (p == null) throw new NotFoundException();
        if (req.seasonId() != null) {
            if (Season.findById(req.seasonId()) == null) {
                return Response.status(404).entity(Map.of("error", "Stagione non trovata")).build();
            }
            p.seasonId = req.seasonId();
        }
        if (req.name() != null && !req.name().isBlank()) p.name = req.name();
        if (req.description() != null) p.description = req.description();
        if (req.openAt() != null) p.openAt = req.openAt();
        if (req.closeAt() != null) p.closeAt = req.closeAt();
        if (req.winningThresholds() != null) p.winningThresholds = req.winningThresholds();
        p.persist();
        return Response.ok(SeasonPoolDto.PoolResponse.from(p)).build();
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response delete(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        auth.requireAdmin(token);
        SeasonPool p = SeasonPool.findById(id);
        if (p == null) throw new NotFoundException();
        long couponCount = SeasonCoupon.count("seasonPoolId", id);
        if (couponCount > 0) {
            return Response.status(409).entity(Map.of(
                    "error", "Impossibile eliminare: esistono " + couponCount + " schedine collegate."
            )).build();
        }
        // i bet vengono eliminati a cascata
        p.delete();
        return Response.noContent().build();
    }

    // ─── Lifecycle ──────────────────────────────────────────────────────────

    @POST
    @Path("/{id}/open")
    @Transactional
    public Response open(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        auth.requireAdminOrMod(token);
        SeasonPool p = SeasonPool.findById(id);
        if (p == null) throw new NotFoundException();
        if (p.status != SeasonPool.Status.DRAFT) {
            return Response.status(400).entity(Map.of("error", "Pool non in stato DRAFT")).build();
        }
        long bets = SeasonBet.count("seasonPoolId", id);
        if (bets == 0) {
            return Response.status(400).entity(Map.of("error", "Nessun pronostico configurato in questa pool")).build();
        }
        p.status = SeasonPool.Status.OPEN;
        p.persist();
        return Response.ok(SeasonPoolDto.PoolResponse.from(p)).build();
    }

    @POST
    @Path("/{id}/close")
    @Transactional
    public Response close(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        auth.requireAdminOrMod(token);
        SeasonPool p = SeasonPool.findById(id);
        if (p == null) throw new NotFoundException();
        if (p.status != SeasonPool.Status.OPEN) {
            return Response.status(400).entity(Map.of("error", "Pool non in stato OPEN")).build();
        }
        p.status = SeasonPool.Status.CLOSED;
        p.persist();
        return Response.ok(SeasonPoolDto.PoolResponse.from(p)).build();
    }

    /**
     * Multi-processing: ricalcola correctCount per ogni schedina usando
     * SOLO i bet RESOLVED. Idempotente, può essere lanciato più volte.
     * Marca la pool come PROCESSED solo se tutti i bet sono RESOLVED.
     */
    @POST
    @Path("/{id}/process")
    @Transactional
    public Response process(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        auth.requireAdminOrMod(token);
        SeasonPool p = SeasonPool.findById(id);
        if (p == null) throw new NotFoundException();
        if (p.status == SeasonPool.Status.DRAFT) {
            return Response.status(400).entity(Map.of("error", "Pool ancora in DRAFT")).build();
        }
        var summary = engine.process(p);
        return Response.ok(summary).build();
    }

    // ─── SeasonBet (config dei pronostici della pool) ───────────────────────

    @GET
    @Path("/{id}/bets")
    @Transactional
    public List<SeasonPoolDto.BetResponse> listBets(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        auth.requireAdminOrMod(token);
        SeasonPool p = SeasonPool.findById(id);
        if (p == null) throw new NotFoundException();
        return SeasonBet.findByPool(id).stream().map(SeasonPoolDto.BetResponse::from).toList();
    }

    @POST
    @Path("/{id}/bets")
    @Transactional
    public Response createBet(
            @HeaderParam("Authorization") String token,
            @PathParam("id") Long id,
            @Valid SeasonPoolDto.BetRequest req) {
        auth.requireAdminOrMod(token);
        SeasonPool p = SeasonPool.findById(id);
        if (p == null) throw new NotFoundException();
        if (p.status != SeasonPool.Status.DRAFT) {
            return Response.status(400).entity(Map.of("error", "Configurabile solo in stato DRAFT")).build();
        }
        Tournament t = Tournament.findById(req.tournamentId());
        if (t == null) {
            return Response.status(404).entity(Map.of("error", "Torneo non trovato")).build();
        }
        SeasonBet.BetType bt;
        try { bt = SeasonBet.BetType.valueOf(req.betType().toUpperCase()); }
        catch (IllegalArgumentException e) {
            return Response.status(400).entity(Map.of("error", "BetType non valido: " + req.betType())).build();
        }
        // unique (pool, tournament, betType)
        long dup = SeasonBet.count("seasonPoolId = ?1 and tournamentId = ?2 and betType = ?3",
                id, req.tournamentId(), bt);
        if (dup > 0) {
            return Response.status(409).entity(Map.of("error", "Pronostico già configurato")).build();
        }
        SeasonBet b = new SeasonBet();
        b.seasonPoolId = id;
        b.tournamentId = req.tournamentId();
        b.betType = bt;
        b.label = (req.label() != null && !req.label().isBlank())
                ? req.label()
                : defaultLabel(bt, t.name);
        b.persist();
        return Response.status(201).entity(SeasonPoolDto.BetResponse.from(b)).build();
    }

    @PATCH
    @Path("/{id}/bets/{betId}")
    @Transactional
    public Response updateBet(
            @HeaderParam("Authorization") String token,
            @PathParam("id") Long id,
            @PathParam("betId") Long betId,
            SeasonPoolDto.BetRequest req) {
        auth.requireAdminOrMod(token);
        SeasonBet b = SeasonBet.findById(betId);
        if (b == null || !b.seasonPoolId.equals(id)) throw new NotFoundException();
        if (req.label() != null && !req.label().isBlank()) b.label = req.label();
        b.persist();
        return Response.ok(SeasonPoolDto.BetResponse.from(b)).build();
    }

    @DELETE
    @Path("/{id}/bets/{betId}")
    @Transactional
    public Response deleteBet(@HeaderParam("Authorization") String token, @PathParam("id") Long id, @PathParam("betId") Long betId) {
        auth.requireAdminOrMod(token);
        SeasonBet b = SeasonBet.findById(betId);
        if (b == null || !b.seasonPoolId.equals(id)) throw new NotFoundException();
        SeasonPool p = SeasonPool.findById(id);
        if (p.status != SeasonPool.Status.DRAFT) {
            return Response.status(400).entity(Map.of("error", "Bet eliminabile solo in pool DRAFT")).build();
        }
        b.delete();
        return Response.noContent().build();
    }

    /**
     * Inserisce/aggiorna il risultato ufficiale di un singolo bet e lo marca RESOLVED.
     * Può essere lanciato più volte (rivedere un risultato sbagliato).
     */
    @PATCH
    @Path("/{id}/bets/{betId}/resolve")
    @Transactional
    public Response resolveBet(
            @HeaderParam("Authorization") String token,
            @PathParam("id") Long id,
            @PathParam("betId") Long betId,
            @Valid SeasonPoolDto.ResolveBetRequest req) {
        auth.requireAdminOrMod(token);
        SeasonBet b = SeasonBet.findById(betId);
        if (b == null || !b.seasonPoolId.equals(id)) throw new NotFoundException();
        b.officialResultRef = req.officialResultRef();
        b.status = SeasonBet.Status.RESOLVED;
        b.resolvedAt = LocalDateTime.now();
        b.persist();
        return Response.ok(SeasonPoolDto.BetResponse.from(b)).build();
    }

    /** Annulla la risoluzione: riporta il bet a PENDING. */
    @POST
    @Path("/{id}/bets/{betId}/unresolve")
    @Transactional
    public Response unresolveBet(
            @HeaderParam("Authorization") String token,
            @PathParam("id") Long id,
            @PathParam("betId") Long betId) {
        auth.requireAdminOrMod(token);
        SeasonBet b = SeasonBet.findById(betId);
        if (b == null || !b.seasonPoolId.equals(id)) throw new NotFoundException();
        b.officialResultRef = null;
        b.status = SeasonBet.Status.PENDING;
        b.resolvedAt = null;
        b.persist();
        return Response.ok(SeasonPoolDto.BetResponse.from(b)).build();
    }

    private String defaultLabel(SeasonBet.BetType bt, String tournamentName) {
        String prefix = switch (bt) {
            case WINNER -> "Vincitore";
            case TOP_SCORER -> "Capocannoniere";
            case TOP_ASSIST -> "Miglior assist";
            case CLEAN_SHEET_TEAM -> "Porta inviolata";
            case BEST_GOALKEEPER -> "Miglior portiere";
            case MOST_GOALS_FOR -> "Miglior attacco";
            case LEAST_GOALS_AGAINST -> "Miglior difesa";
        };
        return prefix + " " + tournamentName;
    }
}
