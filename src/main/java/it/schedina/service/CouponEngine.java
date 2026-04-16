package it.schedina.service;

import it.schedina.dto.CouponDto;
import it.schedina.entity.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.time.LocalDateTime;
import java.util.*;

@ApplicationScoped
public class CouponEngine {

    private static final Set<String> VALID_CHOICES = Set.of("1", "X", "2");

    @Transactional
    public Coupon createCoupon(Long userId, CouponDto.CouponRequest req) {
        LocalDateTime now = LocalDateTime.now();

        Contest contest = Contest.findById(req.contestId());
        if (contest == null) throw bad("Concorso non trovato");
        if (contest.status != Contest.Status.OPEN) throw bad("Il concorso non è aperto alle giocate");
        if (now.isAfter(contest.closeAt)) throw bad("Termine di chiusura del concorso superato");

        Rule rule = contest.rule();
        if (rule == null) throw bad("Regola del concorso non trovata");

        // Max coupons per user
        if (rule.maxCouponsPerUser != null) {
            long existing = Coupon.countActiveByUserAndContest(userId, req.contestId());
            if (existing >= rule.maxCouponsPerUser) {
                throw bad("Numero massimo di schedine per utente raggiunto (" + rule.maxCouponsPerUser + ")");
            }
        }

        // Match count
        if (req.predictions().size() != rule.requiredMatches) {
            throw bad("La schedina deve avere esattamente " + rule.requiredMatches +
                    " pronostici, trovati " + req.predictions().size());
        }

        // Validate each prediction and collect match IDs
        Set<Long> matchIdSet = new HashSet<>();
        for (var pred : req.predictions()) {
            if (!matchIdSet.add(pred.matchId())) {
                throw bad("Partita duplicata: " + pred.matchId());
            }
            List<String> sorted = validateChoices(pred.choices());
            Match match = Match.findById(pred.matchId());
            if (match == null) throw bad("Partita non trovata: " + pred.matchId());
            if (!Objects.equals(match.contestId, req.contestId())) {
                throw bad("La partita " + pred.matchId() + " non appartiene a questo concorso");
            }
            if (match.status != Match.Status.OPEN && match.status != Match.Status.SCHEDULED) {
                throw bad("La partita " + pred.matchId() + " non è disponibile per le giocate");
            }
        }

        // Count doubles and triples
        long doubles = req.predictions().stream().filter(p -> p.choices().size() == 2).count();
        long triples = req.predictions().stream().filter(p -> p.choices().size() == 3).count();
        if (doubles > rule.maxDoubles) throw bad("Troppe doppie: " + doubles + " (max " + rule.maxDoubles + ")");
        if (triples > rule.maxTriples) throw bad("Troppe triple: " + triples + " (max " + rule.maxTriples + ")");

        // Persist coupon
        Coupon coupon = new Coupon();
        coupon.userId = userId;
        coupon.contestId = req.contestId();
        coupon.persist();

        for (var pred : req.predictions()) {
            CouponPrediction cp = new CouponPrediction();
            cp.couponId = coupon.id;
            cp.matchId = pred.matchId();
            cp.choices = validateChoices(pred.choices());
            cp.persist();
        }

        return coupon;
    }

    @Transactional
    public Coupon confirmCoupon(Coupon coupon) {
        if (coupon.status != Coupon.Status.DRAFT) {
            throw bad("Solo le schedine in bozza possono essere confermate");
        }
        Contest contest = Contest.findById(coupon.contestId);
        if (LocalDateTime.now().isAfter(contest.closeAt)) {
            throw bad("Termine di chiusura del concorso superato");
        }
        coupon.status = Coupon.Status.CONFIRMED;
        coupon.confirmedAt = LocalDateTime.now();
        coupon.persist();
        return coupon;
    }

    @Transactional
    public Map<String, Object> processContest(Long contestId) {
        Contest contest = Contest.findById(contestId);
        if (contest == null) throw bad("Concorso non trovato");

        boolean isReprocess = contest.status == Contest.Status.PROCESSED;
        if (!isReprocess
                && contest.status != Contest.Status.CLOSED
                && contest.status != Contest.Status.PROCESSING) {
            throw bad("Il concorso deve essere chiuso prima dell'elaborazione");
        }

        // Verifica che tutte le partite abbiano un risultato
        List<Match> matches = Match.findByContest(contestId);
        List<Long> unresolved = matches.stream()
                .filter(m -> m.officialResult == null)
                .map(m -> m.id)
                .toList();
        if (!unresolved.isEmpty()) {
            throw bad("Partite senza risultato ufficiale: " + unresolved);
        }

        contest.status = Contest.Status.PROCESSING;
        contest.persist();

        Rule rule = contest.rule();
        Map<Long, String> resultMap = new HashMap<>();
        for (Match m : matches) resultMap.put(m.id, m.officialResult);

        // In caso di ricalcolo, recupera ANCHE le schedine già elaborate (WINNING/NOT_WINNING)
        List<Coupon> coupons = isReprocess
                ? Coupon.<Coupon>find("contestId = ?1 and status in ?2", contestId,
                        List.of(Coupon.Status.CONFIRMED, Coupon.Status.PENDING_RESULT,
                                Coupon.Status.WINNING, Coupon.Status.NOT_WINNING)).list()
                : Coupon.findConfirmedByContest(contestId);

        int winners = 0;

        for (Coupon coupon : coupons) {
            List<CouponPrediction> preds = CouponPrediction.findByCoupon(coupon.id);
            int correct = 0;
            for (CouponPrediction pred : preds) {
                String official = resultMap.get(pred.matchId);
                pred.isCorrect = official != null && pred.choices.contains(official);
                if (pred.isCorrect) correct++;
                pred.persist();
            }
            coupon.correctCount = correct;
            coupon.isWinner = rule.winningThresholds.contains(correct);
            coupon.status = coupon.isWinner ? Coupon.Status.WINNING : Coupon.Status.NOT_WINNING;
            coupon.persist();
            if (coupon.isWinner) winners++;
        }

        contest.status = Contest.Status.PROCESSED;
        contest.persist();

        return Map.ofEntries(
                Map.entry("contestId", contestId),
                Map.entry("couponsProcessed", coupons.size()),
                Map.entry("winners", winners),
                Map.entry("reprocessed", isReprocess)
        );
    }

    private List<String> validateChoices(List<String> raw) {
        if (raw == null || raw.isEmpty()) throw bad("Le scelte non possono essere vuote");
        List<String> sorted = raw.stream().distinct().sorted().toList();
        for (String c : sorted) {
            if (!VALID_CHOICES.contains(c)) throw bad("Scelta non valida: " + c + ". Valori ammessi: 1, X, 2");
        }
        return sorted;
    }

    private WebApplicationException bad(String msg) {
        return new WebApplicationException(
                Response.status(400).entity(Map.of("error", msg)).build());
    }
}
