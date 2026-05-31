package it.schedina.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scommessa di FINE CAMPIONATO (self-service per lega): auto-creata per (stagione, lega, mercato)
 * alla prima giocata dell'utente ({@link Giocata}); l'admin ne dichiara il risultato ufficiale.
 * Le scommesse DI PARTITA non passano da qui: sono {@link GiocataPartita} (sempre disponibili).
 * L'enum {@link Market} resta completo perché riusato anche dalle giocate di partita.
 */
@Entity
@Table(name = "bets")
public class Scommessa extends PanacheEntityBase {

    public enum Market {
        // di partita
        GOAL_NOGOAL, EXACT_SCORE, WINNER, FIRST_SCORER,
        // di fine campionato
        TOP_SCORER, TOP_ASSIST, BEST_GOALKEEPER, CLEAN_SHEET, MOST_GOALS_FOR, LEAST_GOALS_AGAINST
    }

    public enum TargetKind { TOKEN, TEAM, PLAYER }

    public enum Status { OPEN, RESOLVED, VOID }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, length = 200)
    public String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    public Market market;

    @Column(name = "season_id")
    public Long seasonId;

    @Column(name = "tournament_id")
    public Long tournamentId;

    @Column(name = "league_id")
    public Long leagueId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public Status status = Status.OPEN;

    @Column(name = "official_result_ref", length = 50)
    public String officialResultRef;

    @Column(name = "resolved_at")
    public LocalDateTime resolvedAt;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt = LocalDateTime.now();

    public TargetKind targetKind() {
        return targetKindOf(market);
    }

    public static TargetKind targetKindOf(Market m) {
        return switch (m) {
            case GOAL_NOGOAL, EXACT_SCORE -> TargetKind.TOKEN;
            case WINNER, MOST_GOALS_FOR, LEAST_GOALS_AGAINST -> TargetKind.TEAM;
            case FIRST_SCORER, TOP_SCORER, TOP_ASSIST, BEST_GOALKEEPER, CLEAN_SHEET -> TargetKind.PLAYER;
        };
    }

    /** Mercati di fine campionato (catalogo). */
    public static boolean isSeasonMarket(Market m) {
        return switch (m) {
            case TOP_SCORER, TOP_ASSIST, BEST_GOALKEEPER, CLEAN_SHEET, MOST_GOALS_FOR, LEAST_GOALS_AGAINST -> true;
            default -> false;
        };
    }

    /** Mercati che riguardano i portieri (picker filtrato ai soli GK). */
    public static boolean isGoalkeeperMarket(Market m) {
        return m == Market.BEST_GOALKEEPER || m == Market.CLEAN_SHEET;
    }

    public static List<Scommessa> findBySeason(Long seasonId) {
        return find("seasonId = ?1 order by id", seasonId).list();
    }
}
