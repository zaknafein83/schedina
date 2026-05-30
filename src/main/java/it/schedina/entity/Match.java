package it.schedina.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Partita di una {@link Giornata}: squadre, orario, soglia U/O e punteggio.
 * Gli esiti 1X2 e U/O si calcolano dal punteggio.
 */
@Entity
@Table(name = "matches")
public class Match extends PanacheEntityBase {

    public enum Status { DRAFT, SCHEDULED, OPEN, CLOSED, RESULT_ENTERED, VALIDATED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "home_team_id", nullable = false)
    public Long homeTeamId;

    @Column(name = "away_team_id", nullable = false)
    public Long awayTeamId;

    @Column(name = "league_id", nullable = false)
    public Long leagueId;

    @Column(name = "giornata_id")
    public Long giornataId;

    @Column(name = "scheduled_at", nullable = false)
    public LocalDateTime scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public Status status = Status.DRAFT;

    /** Soglia Under/Over della partita (default 2.5). */
    @Column(name = "over_under_line", nullable = false)
    public Double overUnderLine = 2.5;

    @Column(name = "home_score")
    public Integer homeScore;

    @Column(name = "away_score")
    public Integer awayScore;

    public boolean hasScore() {
        return homeScore != null && awayScore != null;
    }

    /** Esito 1/X/2 dal punteggio, o null se non inserito. */
    public String result1x2() {
        if (!hasScore()) return null;
        if (homeScore > awayScore) return "1";
        return homeScore.equals(awayScore) ? "X" : "2";
    }

    /** Esito U/O dal punteggio rispetto a overUnderLine, o null se non inserito. */
    public String resultUO() {
        if (!hasScore()) return null;
        double line = overUnderLine != null ? overUnderLine : 2.5;
        return (homeScore + awayScore) > line ? "O" : "U";
    }

    public static List<Match> findByGiornata(Long giornataId) {
        return find("giornataId = ?1 order by id", giornataId).list();
    }

    public static List<Match> findByLeague(Long leagueId) {
        return find("leagueId", leagueId).list();
    }
}
