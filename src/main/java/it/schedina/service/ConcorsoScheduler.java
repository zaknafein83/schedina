package it.schedina.service;

import io.quarkus.scheduler.Scheduled;
import it.schedina.entity.Concorso;
import it.schedina.entity.Match;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Apertura/chiusura automatica dei concorsi del Totocalcio (orari in fuso Europe/Rome).
 * <ul>
 *   <li><b>Apre</b> i concorsi DRAFT con apertura programmata già passata, purché abbiano partite
 *       selezionate (un concorso senza partite resta da aprire all'admin).</li>
 *   <li><b>Chiude</b> i concorsi OPEN la cui chiusura (20:30 del giorno delle partite) è passata,
 *       a meno che la chiusura automatica sia stata disabilitata da una riapertura manuale.</li>
 * </ul>
 * Apertura/chiusura manuali dell'admin restano sempre disponibili e prevalgono.
 */
@ApplicationScoped
public class ConcorsoScheduler {

    public static final ZoneId ROME = ZoneId.of("Europe/Rome");

    @Scheduled(every = "60s")
    void tick() {
        run(LocalDateTime.now(ROME));
    }

    /** Esegue una passata allo specifico istante (parametro estratto per i test). Ritorna i concorsi cambiati. */
    @Transactional
    public int run(LocalDateTime now) {
        int changed = 0;

        // Auto-apertura: DRAFT con apertura programmata passata e almeno una partita.
        List<Concorso> daAprire = Concorso.find(
                "status = ?1 and openAt is not null and openAt <= ?2",
                Concorso.Status.DRAFT, now).list();
        for (Concorso c : daAprire) {
            if (Match.count("concorsoId", c.id) > 0) {
                c.status = Concorso.Status.OPEN;
                c.persist();
                changed++;
            }
        }

        // Auto-chiusura: OPEN con chiusura passata e chiusura automatica ancora attiva.
        List<Concorso> daChiudere = Concorso.find(
                "status = ?1 and closeAuto = true and closeAt <= ?2",
                Concorso.Status.OPEN, now).list();
        for (Concorso c : daChiudere) {
            c.status = Concorso.Status.CLOSED;
            c.persist();
            changed++;
        }

        return changed;
    }
}
