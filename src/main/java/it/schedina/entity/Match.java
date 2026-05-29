package it.schedina.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Il fatto sportivo: squadre, orario, punteggio. NON è più "la scommessa".
 * L'inserimento del punteggio risolve automaticamente le Scommesse AUTO collegate
 * (vedi {@code ScommessaResolutionService}).
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

    @Column(name = "scheduled_at", nullable = false)
    public LocalDateTime scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public Status status = Status.DRAFT;

    @Column(name = "home_score")
    public Integer homeScore;

    @Column(name = "away_score")
    public Integer awayScore;

    public boolean hasScore() {
        return homeScore != null && awayScore != null;
    }

    public static List<Match> findByLeague(Long leagueId) {
        return find("leagueId", leagueId).list();
    }
}
