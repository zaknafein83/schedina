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

    /** Stored as "1", "X", "2" */
    @Column(name = "official_result", length = 5)
    public String officialResult;

    // --- Queries ---

    public static List<Match> findByContest(Long contestId) {
        return find("contestId", contestId).list();
    }

    public static List<Match> findByContestWithResult(Long contestId) {
        return find("contestId = ?1 and officialResult is not null", contestId).list();
    }
}
