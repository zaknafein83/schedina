package it.schedina.service;

import it.schedina.dto.SeasonCouponDto;
import it.schedina.entity.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Logica dei concorsi stagionali. Le schedine stagionali contengono N pronostici
 * (uno per ogni SeasonBet della pool), ciascuno con una choice (teamId o playerId).
 *
 * Processing multi-step:
 *  - i bet sono RESOLVED uno alla volta dall'admin man mano che arrivano i risultati
 *  - process() può essere chiamato più volte: ricalcola correctCount usando SOLO
 *    i bet RESOLVED, lascia null/false isCorrect per i bet ancora PENDING
 *  - la pool diventa PROCESSED solo quando tutti i bet sono RESOLVED
 *    altrimenti rimane in CLOSED (multi-snapshot)
 */
@ApplicationScoped
public class SeasonCouponEngine {

    @Transactional
    public SeasonCoupon createCoupon(Long userId, SeasonCouponDto.CreateRequest req) {
        LocalDateTime now = LocalDateTime.now();

        SeasonPool pool = SeasonPool.findById(req.seasonPoolId());
        if (pool == null) throw bad("Pool non trovata");
        if (pool.status != SeasonPool.Status.OPEN) throw bad("La pool non è aperta alle giocate");
        if (pool.closeAt != null && now.isAfter(pool.closeAt)) {
            throw bad("Termine di chiusura della pool superato");
        }

        // Una schedina per utente per pool
        SeasonCoupon existing = SeasonCoupon.findByUserAndPool(userId, req.seasonPoolId());
        if (existing != null && existing.status != SeasonCoupon.Status.CANCELLED) {
            throw bad("Hai già una schedina per questa pool");
        }

        List<SeasonBet> bets = SeasonBet.findByPool(req.seasonPoolId());
        Map<Long, SeasonBet> betById = new HashMap<>();
        for (SeasonBet b : bets) betById.put(b.id, b);

        // Tutte le predictions devono riferire a bet della pool
        Set<Long> seen = new HashSet<>();
        for (var pred : req.predictions()) {
            SeasonBet b = betById.get(pred.seasonBetId());
            if (b == null) throw bad("Bet " + pred.seasonBetId() + " non appartiene a questa pool");
            if (!seen.add(pred.seasonBetId())) throw bad("Bet duplicato: " + pred.seasonBetId());
            validateChoice(b, pred.choiceRef());
        }

        // Tutti i bet della pool devono avere una choice (schedina completa)
        if (seen.size() != bets.size()) {
            List<Long> missing = bets.stream().map(b -> b.id).filter(id -> !seen.contains(id)).toList();
            throw bad("Mancano pronostici per i bet: " + missing);
        }

        SeasonCoupon coupon = new SeasonCoupon();
        coupon.userId = userId;
        coupon.seasonPoolId = req.seasonPoolId();
        coupon.persist();

        for (var pred : req.predictions()) {
            SeasonCouponPrediction p = new SeasonCouponPrediction();
            p.seasonCouponId = coupon.id;
            p.seasonBetId = pred.seasonBetId();
            p.choiceRef = pred.choiceRef();
            p.persist();
        }

        return coupon;
    }

    @Transactional
    public SeasonCoupon confirmCoupon(SeasonCoupon coupon) {
        if (coupon.status != SeasonCoupon.Status.DRAFT) {
            throw bad("Solo le schedine in bozza possono essere confermate");
        }
        SeasonPool pool = SeasonPool.findById(coupon.seasonPoolId);
        if (pool.closeAt != null && LocalDateTime.now().isAfter(pool.closeAt)) {
            throw bad("Termine di chiusura della pool superato");
        }
        coupon.status = SeasonCoupon.Status.CONFIRMED;
        coupon.confirmedAt = LocalDateTime.now();
        coupon.persist();
        return coupon;
    }

    /**
     * Idempotente. Aggiorna correctCount/isCorrect per le predictions associate
     * a bet RESOLVED. Le predictions di bet ancora PENDING restano con isCorrect=null.
     * Promuove la pool a PROCESSED solo quando TUTTI i bet sono RESOLVED.
     */
    @Transactional
    public Map<String, Object> process(SeasonPool pool) {
        List<SeasonBet> bets = SeasonBet.findByPool(pool.id);
        long resolvedCount = bets.stream().filter(b -> b.status == SeasonBet.Status.RESOLVED).count();
        boolean allResolved = resolvedCount == bets.size() && !bets.isEmpty();

        Map<Long, String> officialByBet = new HashMap<>();
        for (SeasonBet b : bets) {
            if (b.status == SeasonBet.Status.RESOLVED && b.officialResultRef != null) {
                officialByBet.put(b.id, b.officialResultRef);
            }
        }

        // Considera solo le schedine attive
        List<SeasonCoupon> coupons = SeasonCoupon.<SeasonCoupon>find(
                "seasonPoolId = ?1 and status in ?2",
                pool.id,
                List.of(SeasonCoupon.Status.CONFIRMED,
                        SeasonCoupon.Status.PROCESSED,
                        SeasonCoupon.Status.WINNING,
                        SeasonCoupon.Status.NOT_WINNING)
        ).list();

        int winners = 0;
        for (SeasonCoupon coupon : coupons) {
            List<SeasonCouponPrediction> preds = SeasonCouponPrediction.findByCoupon(coupon.id);
            int correct = 0;
            for (SeasonCouponPrediction pred : preds) {
                String official = officialByBet.get(pred.seasonBetId);
                if (official == null) {
                    pred.isCorrect = null; // bet ancora PENDING
                } else {
                    pred.isCorrect = official.equals(pred.choiceRef);
                    if (pred.isCorrect) correct++;
                }
                pred.persist();
            }
            coupon.correctCount = correct;
            if (allResolved) {
                coupon.isWinner = pool.winningThresholds.contains(correct);
                coupon.status = coupon.isWinner ? SeasonCoupon.Status.WINNING : SeasonCoupon.Status.NOT_WINNING;
                if (Boolean.TRUE.equals(coupon.isWinner)) winners++;
            } else {
                coupon.status = SeasonCoupon.Status.PROCESSED;
            }
            coupon.persist();
        }

        if (allResolved) {
            pool.status = SeasonPool.Status.PROCESSED;
            pool.persist();
        }

        return Map.ofEntries(
                Map.entry("poolId", pool.id),
                Map.entry("totalBets", bets.size()),
                Map.entry("resolvedBets", (int) resolvedCount),
                Map.entry("allResolved", allResolved),
                Map.entry("couponsProcessed", coupons.size()),
                Map.entry("winners", winners),
                Map.entry("status", pool.status.name())
        );
    }

    private void validateChoice(SeasonBet bet, String choiceRef) {
        if (choiceRef == null || choiceRef.isBlank()) throw bad("choiceRef vuoto per bet " + bet.id);
        Long id;
        try { id = Long.parseLong(choiceRef); }
        catch (NumberFormatException e) { throw bad("choiceRef non numerico per bet " + bet.id); }

        if (bet.targetKind() == SeasonBet.TargetKind.TEAM) {
            if (Team.findById(id) == null) throw bad("Squadra non trovata: " + id);
        } else {
            if (Player.findById(id) == null) throw bad("Giocatore non trovato: " + id);
        }
    }

    private WebApplicationException bad(String msg) {
        return new WebApplicationException(
                Response.status(400).entity(Map.of("error", msg)).build());
    }
}
