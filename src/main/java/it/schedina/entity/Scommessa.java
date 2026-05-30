package it.schedina.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scommessa extra (catalogo), giocata con flow separato dalla schedina.
 * Scope: SEASON (fine stagione) o GIORNATA (legata a una giornata, eventualmente a una partita).
 */
@Entity
@Table(name = "bets")
public class Scommessa extends PanacheEntityBase {

    public enum Scope { SEASON, GIORNATA }

    public enum Market {
        GOAL_NOGOAL, FIRST_SCORER, EXACT_SCORE,
        WINNER, TOP_SCORER, TOP_ASSIST, CLEAN_SHEET_TEAM,
        BEST_GOALKEEPER, MOST_GOALS_FOR, LEAST_GOALS_AGAINST
    }

    public enum TargetKind { TOKEN, TEAM, PLAYER }

    public enum ResolutionMode { AUTO, MANUAL }

    public enum Status { OPEN, RESOLVED, VOID }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public Scope scope = Scope.SEASON;

    @Column(nullable = false, length = 200)
    public String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    public Market market;

    // --- Contesto secondo lo scope ---
    @Column(name = "season_id")
    public Long seasonId;

    @Column(name = "giornata_id")
    public Long giornataId;

    @Column(name = "match_id")
    public Long matchId;

    @Column(name = "tournament_id")
    public Long tournamentId;

    @Column(name = "league_id")
    public Long leagueId;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolution_mode", nullable = false, length = 10)
    public ResolutionMode resolutionMode = ResolutionMode.MANUAL;

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
            case WINNER, CLEAN_SHEET_TEAM, MOST_GOALS_FOR, LEAST_GOALS_AGAINST -> TargetKind.TEAM;
            case FIRST_SCORER, TOP_SCORER, TOP_ASSIST, BEST_GOALKEEPER -> TargetKind.PLAYER;
        };
    }

    /** AUTO = risolvibile dal punteggio della partita collegata (es. gol/no gol). */
    public static boolean isAutoMarket(Market m) {
        return m == Market.GOAL_NOGOAL;
    }

    public static ResolutionMode defaultResolution(Market m) {
        return isAutoMarket(m) ? ResolutionMode.AUTO : ResolutionMode.MANUAL;
    }

    public static List<Scommessa> findByGiornata(Long giornataId) {
        return find("scope = ?1 and giornataId = ?2 order by id", Scope.GIORNATA, giornataId).list();
    }

    public static List<Scommessa> findBySeason(Long seasonId) {
        return find("scope = ?1 and seasonId = ?2 order by id", Scope.SEASON, seasonId).list();
    }

    public static List<Scommessa> findByMatch(Long matchId) {
        return find("matchId", matchId).list();
    }

    public static List<Scommessa> findOpen() {
        return find("status = ?1 order by id", Status.OPEN).list();
    }
}
