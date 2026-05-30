package it.schedina.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Giornata del calendario: contiene le partite su cui si gioca la schedina (1X2 + U/O).
 * Sostituisce il vecchio Concorso per il gioco principale.
 */
@Entity
@Table(name = "giornate")
public class Giornata extends PanacheEntityBase {

    public enum Status { DRAFT, OPEN, CLOSED, PROCESSED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "season_id")
    public Long seasonId;

    /** Regola che definisce le soglie vincenti della schedina (opzionale fino all'elaborazione). */
    @Column(name = "rule_id")
    public Long ruleId;

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

    /** Punteggi vincenti della schedina (match esatto), es. [12, 13]. */
    @Convert(converter = JsonListConverters.IntList.class)
    @Column(name = "winning_thresholds", nullable = false, columnDefinition = "text")
    public List<Integer> winningThresholds = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt = LocalDateTime.now();

    public static List<Giornata> findOpen() {
        return find("status = ?1 order by closeAt", Status.OPEN).list();
    }

    public static List<Giornata> allOrdered() {
        return find("order by number desc, id desc").list();
    }
}
