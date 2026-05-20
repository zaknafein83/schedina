package it.schedina.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Concorso stagionale (pronostici a fine stagione: scudetti, capocannoniere, ecc.).
 * Una stagione può avere più pool, ma in pratica una sola attiva alla volta.
 */
@Entity
@Table(name = "season_pools")
public class SeasonPool extends PanacheEntityBase {

    public enum Status { DRAFT, OPEN, CLOSED, PROCESSED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "season_id", nullable = false)
    public Long seasonId;

    @Column(nullable = false, length = 150)
    public String name;

    @Column(columnDefinition = "text")
    public String description;

    @Column(name = "open_at")
    public LocalDateTime openAt;

    @Column(name = "close_at")
    public LocalDateTime closeAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public Status status = Status.DRAFT;

    /** Soglie di vincita (es. [7, 8, 9]). Riusa lo schema di Rule. */
    @Convert(converter = JsonListConverters.IntList.class)
    @Column(name = "winning_thresholds", nullable = false, columnDefinition = "text")
    public List<Integer> winningThresholds = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt = LocalDateTime.now();

    public Season season() {
        return Season.findById(seasonId);
    }
}
