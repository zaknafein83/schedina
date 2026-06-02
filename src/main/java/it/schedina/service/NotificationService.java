package it.schedina.service;

import it.schedina.entity.Concorso;
import it.schedina.entity.Notification;
import it.schedina.entity.Schedina;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class NotificationService {

    /**
     * Riconcilia le notifiche del concorso col modello a DUE giochi (post split V22):
     * una notifica per ogni gioco vinto (Totocalcio 1X2 / Under/Over), col conteggio per-gioco.
     * Idempotente e auto-pulente: a ogni (ri)elaborazione crea le mancanti, salta le già presenti
     * e rimuove quelle obsolete (gioco non più vinto) o legacy (combinato, game=null).
     */
    @Transactional
    public Map<String, Integer> sendConcorsoNotifications(Long concorsoId) {
        Concorso c = Concorso.findById(concorsoId);
        if (c == null || c.status != Concorso.Status.PROCESSED) {
            return Map.of("sent", 0, "skipped", 0, "removed", 0, "errors", 0);
        }
        List<Schedina> schedine = Schedina.find("concorsoId", concorsoId).list();

        int sent = 0, skipped = 0, removed = 0, errors = 0;
        for (Schedina s : schedine) {
            // Giochi effettivamente vinti dalla schedina, con il rispettivo conteggio per-gioco.
            Map<Notification.Game, Integer> won = new EnumMap<>(Notification.Game.class);
            if (Boolean.TRUE.equals(s.isWinner1x2)) won.put(Notification.Game.TOTOCALCIO, nz(s.correct1x2Count));
            if (Boolean.TRUE.equals(s.isWinnerUo)) won.put(Notification.Game.UNDER_OVER, nz(s.correctUoCount));

            // Rimuove le notifiche non più valide (gioco non vinto, o legacy senza gioco).
            for (Notification n : Notification.findBySchedina(s.id)) {
                if (n.game == null || !won.containsKey(n.game)) {
                    n.delete();
                    removed++;
                }
            }
            // Crea quelle mancanti (una per gioco vinto), saltando quelle già presenti.
            for (Map.Entry<Notification.Game, Integer> e : won.entrySet()) {
                if (Notification.alreadyNotifiedForGame(s.id, e.getKey())) { skipped++; continue; }
                try {
                    Notification n = new Notification();
                    n.userId = s.userId;
                    n.schedinaId = s.id;
                    n.game = e.getKey();
                    n.threshold = e.getValue();
                    n.message = winMessage(s.id, c.name, e.getKey(), e.getValue());
                    n.status = Notification.Status.SENT;
                    n.sentAt = LocalDateTime.now();
                    n.persist();
                    sent++;
                } catch (Exception ex) {
                    errors++;
                }
            }
        }
        return Map.of("sent", sent, "skipped", skipped, "removed", removed, "errors", errors);
    }

    private static int nz(Integer v) { return v != null ? v : 0; }

    private static String winMessage(Long schedinaId, String concorso, Notification.Game game, int correct) {
        String gioco = game == Notification.Game.TOTOCALCIO
                ? "il Totocalcio (1X2)" : "l'Under/Over";
        return "Congratulazioni! La tua schedina #" + schedinaId + " ha vinto " + gioco +
                " del concorso " + concorso + " con " + correct + " risultati esatti.";
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
