package it.schedina.service;

import it.schedina.entity.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class NotificationService {

    @Transactional
    public Map<String, Integer> sendContestNotifications(Long contestId) {
        Contest contest = Contest.findById(contestId);
        if (contest == null || contest.status != Contest.Status.PROCESSED) {
            return Map.of("sent", 0, "skipped", 0, "errors", 0);
        }

        Rule rule = contest.rule();
        List<Integer> thresholds = rule.winningThresholds.stream().sorted((a, b) -> b - a).toList();

        List<Coupon> winners = Coupon.find("contestId = ?1 and status = ?2",
                contestId, Coupon.Status.WINNING).list();

        int sent = 0, skipped = 0, errors = 0;

        for (Coupon coupon : winners) {
            int correct = coupon.correctCount != null ? coupon.correctCount : 0;
            for (int threshold : thresholds) {
                if (correct < threshold) continue;
                if (Notification.alreadyNotified(coupon.id, threshold)) {
                    skipped++;
                    continue;
                }
                try {
                    Notification n = new Notification();
                    n.userId = coupon.userId;
                    n.couponId = coupon.id;
                    n.threshold = threshold;
                    n.message = "Congratulazioni! La tua schedina #" + coupon.id +
                            " per il concorso \"" + contest.name + "\" ha indovinato " +
                            correct + " risultati, raggiungendo la soglia di " + threshold + ".";
                    n.status = Notification.Status.SENT;
                    n.sentAt = LocalDateTime.now();
                    n.persist();
                    sent++;
                    // TODO: aggiungere qui invio email/push reale
                } catch (Exception e) {
                    errors++;
                }
            }
        }

        return Map.of("sent", sent, "skipped", skipped, "errors", errors);
    }

    @Transactional
    public Notification resend(Long notificationId) {
        Notification n = Notification.findById(notificationId);
        if (n == null) throw notFound();
        if (n.status == Notification.Status.SENT) {
            throw new WebApplicationException(
                    Response.status(400).entity(Map.of("error", "Notifica già inviata")).build());
        }
        n.status = Notification.Status.SENT;
        n.sentAt = LocalDateTime.now();
        n.persist();
        return n;
    }

    private WebApplicationException notFound() {
        return new WebApplicationException(
                Response.status(404).entity(Map.of("error", "Notifica non trovata")).build());
    }
}
