package it.schedina.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scommessa: entità di prima classe, indipendente dalla schedina. Rappresenta una singola
 * cosa da pronosticare, con le sue opzioni ({@link BetOption}) e un risultato ufficiale.
 *
 * Può essere legata a una partita ({@code matchId}, mercati AUTO derivabili dal punteggio)
 * oppure a un torneo/stagione ({@code tournamentId}/{@code seasonId}, mercati MANUAL).
 */
@Entity
@Table(name = "bets")
public class Scommessa extends PanacheEntityBase {

    public enum Market {
        RESULT_1X2, UNDER_OVER, GOAL_NOGOAL, FIRST_SCORER,
        WINNER, TOP_SCORER, TOP_ASSIST, CLEAN_SHEET_TEAM,
        BEST_GOALKEEPER, MOST_GOALS_FOR, LEAST_GOALS_AGAINST
    }

    public enum TargetKind { TOKEN, TEAM, PLAYER }

    public enum ResolutionMode { AUTO, MANUAL }

    public enum Status { OPEN, RESOLVED, VOID }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** Concorso di appartenenza. null finché la scommessa non è assegnata a un concorso. */
    @Column(name = "concorso_id")
    public Long concorsoId;

    @Column(nullable = false, length = 200)
    public String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    public Market market;

    // --- Contesto (uno o più a seconda del mercato) ---
    @Column(name = "match_id")
    public Long matchId;

    @Column(name = "tournament_id")
    public Long tournamentId;

    @Column(name = "season_id")
    public Long seasonId;

    @Column(name = "league_id")
    public Long leagueId;

    /** Soglia Under/Over (solo market UNDER_OVER). */
    @Column(name = "over_under_line")
    public Double overUnderLine;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolution_mode", nullable = false, length = 10)
    public ResolutionMode resolutionMode = ResolutionMode.MANUAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public Status status = Status.OPEN;

    /** Riferimento dell'opzione vincente (token, teamId o playerId). null finché OPEN. */
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
            case RESULT_1X2, UNDER_OVER, GOAL_NOGOAL -> TargetKind.TOKEN;
            case WINNER, CLEAN_SHEET_TEAM, MOST_GOALS_FOR, LEAST_GOALS_AGAINST -> TargetKind.TEAM;
            case FIRST_SCORER, TOP_SCORER, TOP_ASSIST, BEST_GOALKEEPER -> TargetKind.PLAYER;
        };
    }

    /** I mercati AUTO sono risolvibili direttamente dal punteggio della partita collegata. */
    public static boolean isAutoMarket(Market m) {
        return m == Market.RESULT_1X2 || m == Market.UNDER_OVER || m == Market.GOAL_NOGOAL;
    }

    public static ResolutionMode defaultResolution(Market m) {
        return isAutoMarket(m) ? ResolutionMode.AUTO : ResolutionMode.MANUAL;
    }

    public static List<Scommessa> findByConcorso(Long concorsoId) {
        return find("concorsoId = ?1 order by id", concorsoId).list();
    }

    public static List<Scommessa> findByMatch(Long matchId) {
        return find("matchId", matchId).list();
    }
}
