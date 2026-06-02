package it.schedina.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Schedina di una {@link Giornata}: per ogni partita l'utente pronostica 1X2 e U/O
 * (vedi {@link Selezione}). Una sola schedina attiva per utente per giornata.
 */
@Entity
@Table(name = "schedine")
public class Schedina extends PanacheEntityBase {

    public enum Status { DRAFT, CONFIRMED, PROCESSED, WINNING, NOT_WINNING, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "user_id", nullable = false)
    public Long userId;

    @Column(name = "concorso_id", nullable = false)
    public Long concorsoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public Status status = Status.DRAFT;

    /** Totale (1X2 + U/O) — legacy, mantenuto per retrocompatibilità. */
    @Column(name = "correct_count")
    public Integer correctCount;

    /** Vittoria su almeno uno dei due giochi — legacy/aggregato. */
    @Column(name = "is_winner")
    public Boolean isWinner;

    /** Pronostici 1X2 esatti (gioco Totocalcio). */
    @Column(name = "correct_1x2_count")
    public Integer correct1x2Count;

    /** Pronostici Under/Over esatti (gioco U/O). */
    @Column(name = "correct_uo_count")
    public Integer correctUoCount;

    /** Vincente nel gioco Totocalcio (1X2). */
    @Column(name = "is_winner_1x2")
    public Boolean isWinner1x2;

    /** Vincente nel gioco Under/Over. */
    @Column(name = "is_winner_uo")
    public Boolean isWinnerUo;

    @Column(name = "confirmed_at")
    public LocalDateTime confirmedAt;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt = LocalDateTime.now();

    public static List<Schedina> findByConcorso(Long concorsoId) {
        return find("concorsoId", concorsoId).list();
    }

    public static List<Schedina> findByUser(Long userId) {
        return find("userId = ?1 order by id desc", userId).list();
    }

    public static Schedina findActiveByUserAndConcorso(Long userId, Long concorsoId) {
        return find("userId = ?1 and concorsoId = ?2 and status != ?3",
                userId, concorsoId, Status.CANCELLED).firstResult();
    }
}
