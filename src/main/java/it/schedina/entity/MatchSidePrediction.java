package it.schedina.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Pronostico "extra" per una singola partita, oltre al risultato 1X2 / Under-Over.
 * Esempi: Gol/No gol (entrambe segnano sì/no), Primo marcatore (playerId).
 *
 * Ogni Match può avere 0+ side prediction; l'utente, in fase di compilazione,
 * vede le side prediction esistenti per ciascuna partita e le compila.
 */
@Entity
@Table(name = "match_side_predictions")
public class MatchSidePrediction extends PanacheEntityBase {

    public enum BetType {
        /** Entrambe le squadre segnano sì/no → choice "GOAL" o "NOGOAL" */
        GOAL_NOGOAL,
        /** Primo marcatore della partita → choice = playerId */
        FIRST_SCORER
    }

    public enum Status { PENDING, RESOLVED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "match_id", nullable = false)
    public Long matchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "bet_type", nullable = false, length = 30)
    public BetType betType;

    @Column(nullable = false, length = 200)
    public String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public Status status = Status.PENDING;

    /**
     * GOAL_NOGOAL → "GOAL" o "NOGOAL"
     * FIRST_SCORER → playerId come stringa (o "NONE" se nessuno ha segnato)
     */
    @Column(name = "official_result", length = 50)
    public String officialResult;

    @Column(name = "resolved_at")
    public LocalDateTime resolvedAt;

    public static List<MatchSidePrediction> findByMatch(Long matchId) {
        return find("matchId", matchId).list();
    }
}
