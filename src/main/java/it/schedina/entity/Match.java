package it.schedina.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "matches")
public class Match extends PanacheEntityBase {

    public enum Status {
        DRAFT, SCHEDULED, OPEN, CLOSED, RESULT_ENTERED, VALIDATED
    }

    public enum Result {
        HOME("1"), DRAW("X"), AWAY("2");

        public final String value;
        Result(String v) { this.value = v; }

        public static Result fromValue(String v) {
            for (Result r : values()) if (r.value.equals(v)) return r;
            throw new IllegalArgumentException("Invalid result: " + v);
        }
    }

    public enum BetType {
        /** Pronostico classico 1 / X / 2 */
        RESULT_1X2,
        /** Under / Over con soglia (es. 3.5) */
        UNDER_OVER
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "home_team_id", nullable = false)
    public Long homeTeamId;

    @Column(name = "away_team_id", nullable = false)
    public Long awayTeamId;

    @Column(name = "league_id", nullable = false)
    public Long leagueId;

    @Column(name = "contest_id")
    public Long contestId;

    @Column(name = "scheduled_at", nullable = false)
    public LocalDateTime scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public Status status = Status.DRAFT;

    /** Tipo di scommessa per questa partita */
    @Enumerated(EnumType.STRING)
    @Column(name = "bet_type", nullable = false, length = 20)
    public BetType betType = BetType.RESULT_1X2;

    /** Soglia Under/Over (es. 3.5). Usato solo se betType = UNDER_OVER. */
    @Column(name = "over_under_line")
    public Double overUnderLine;

    /** Punteggio squadra di casa */
    @Column(name = "home_score")
    public Integer homeScore;

    /** Punteggio squadra ospite */
    @Column(name = "away_score")
    public Integer awayScore;

    /**
     * Calcolato automaticamente:
     *  - RESULT_1X2:  "1" / "X" / "2"
     *  - UNDER_OVER:  "U" se goal totali ≤ soglia, "O" altrimenti
     */
    @Column(name = "official_result", length = 5)
    public String officialResult;

    public String computeResult() {
        if (homeScore == null || awayScore == null) return null;
        if (betType == BetType.UNDER_OVER) {
            double line = overUnderLine != null ? overUnderLine : 3.5;
            int total = homeScore + awayScore;
            return total > line ? "O" : "U";
        }
        // RESULT_1X2
        if (homeScore > awayScore) return "1";
        if (homeScore.equals(awayScore)) return "X";
        return "2";
    }

    // --- Queries ---

    public static List<Match> findByContest(Long contestId) {
        return find("contestId", contestId).list();
    }

    public static List<Match> findByContestWithResult(Long contestId) {
        return find("contestId = ?1 and officialResult is not null", contestId).list();
    }
}
