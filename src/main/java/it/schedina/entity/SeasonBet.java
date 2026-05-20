package it.schedina.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Singolo pronostico configurato in una SeasonPool.
 * Esempio: (tournamentId=Serie A, betType=WINNER) → "Vincitore Scudetto Serie A".
 *          (tournamentId=Serie A, betType=TOP_SCORER) → "Capocannoniere Serie A".
 */
@Entity
@Table(name = "season_bets")
public class SeasonBet extends PanacheEntityBase {

    public enum BetType {
        /** Vincitore della competizione (target = teamId) */
        WINNER,
        /** Capocannoniere (target = playerId) */
        TOP_SCORER,
        /** Miglior assistman (target = playerId) */
        TOP_ASSIST,
        /** Squadra con più clean sheet / porta inviolata (target = teamId) */
        CLEAN_SHEET_TEAM,
        /** Miglior portiere (target = playerId) */
        BEST_GOALKEEPER,
        /** Squadra con più gol fatti (target = teamId) */
        MOST_GOALS_FOR,
        /** Squadra con meno gol subiti (target = teamId) */
        LEAST_GOALS_AGAINST
    }

    public enum TargetKind { TEAM, PLAYER }

    public enum Status { PENDING, RESOLVED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "season_pool_id", nullable = false)
    public Long seasonPoolId;

    @Column(name = "tournament_id", nullable = false)
    public Long tournamentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "bet_type", nullable = false, length = 30)
    public BetType betType;

    /** Etichetta visibile, es. "Vincitore Scudetto Serie A". Generabile auto da betType+tournament. */
    @Column(nullable = false, length = 200)
    public String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public Status status = Status.PENDING;

    /**
     * Risultato ufficiale memorizzato come stringa:
     * - per i bet TEAM-based:   id della squadra vincitrice
     * - per i bet PLAYER-based: id del giocatore vincitore
     * null finché status = PENDING.
     */
    @Column(name = "official_result_ref", length = 50)
    public String officialResultRef;

    @Column(name = "resolved_at")
    public LocalDateTime resolvedAt;

    public TargetKind targetKind() {
        return targetKindOf(betType);
    }

    public static TargetKind targetKindOf(BetType bt) {
        return switch (bt) {
            case WINNER, CLEAN_SHEET_TEAM, MOST_GOALS_FOR, LEAST_GOALS_AGAINST -> TargetKind.TEAM;
            case TOP_SCORER, TOP_ASSIST, BEST_GOALKEEPER -> TargetKind.PLAYER;
        };
    }

    public static List<SeasonBet> findByPool(Long seasonPoolId) {
        return find("seasonPoolId = ?1 order by tournamentId, betType", seasonPoolId).list();
    }
}
