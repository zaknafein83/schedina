package it.schedina.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Concorso del Totocalcio: la "schedina" giocabile. Fa riferimento a un turno (number) e
 * contiene una selezione di partite (scelte a mano dall'admin) prese dalle giornate di
 * campionato di quel turno, anche di leghe diverse. L'utente lo compila (1X2 + Under/Over).
 */
@Entity
@Table(name = "concorsi")
public class Concorso extends PanacheEntityBase {

    public enum Status { DRAFT, OPEN, CLOSED, PROCESSED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "season_id")
    public Long seasonId;

    /** Regola con le soglie vincenti della schedina (opzionale fino all'elaborazione). */
    @Column(name = "rule_id")
    public Long ruleId;

    /** Turno di riferimento: le partite selezionate sono tutte di questo numero di giornata. */
    @Column(nullable = false)
    public int number;

    @Column(nullable = false, length = 150)
    public String name;

    @Column(name = "open_at", nullable = false)
    public LocalDateTime openAt;

    @Column(name = "close_at", nullable = false)
    public LocalDateTime closeAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public Status status = Status.DRAFT;

    /** Soglie vincenti legacy/fallback se ruleId è null. */
    @Convert(converter = JsonListConverters.IntList.class)
    @Column(name = "winning_thresholds", nullable = false, columnDefinition = "text")
    public List<Integer> winningThresholds = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt = LocalDateTime.now();

    public static List<Concorso> findOpen() {
        return find("status = ?1 order by closeAt", Status.OPEN).list();
    }

    public static List<Concorso> allOrdered() {
        return find("order by number desc, id desc").list();
    }
}
