package it.schedina.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scommessa di partita giocata dall'utente (sempre disponibile, non pre-creata dall'admin).
 * L'utente sceglie partita + tipo + previsione. Risoluzione: GOAL_NOGOAL/WINNER/EXACT_SCORE
 * automatiche dal punteggio; FIRST_SCORER manuale (Match.firstScorerPlayerId).
 *
 * prediction per market:
 *  - GOAL_NOGOAL → "GOAL" | "NOGOAL"
 *  - WINNER      → teamId (una delle due squadre)
 *  - EXACT_SCORE → "h-a" (punteggio libero, es. "2-1")
 *  - FIRST_SCORER→ playerId (di una delle due squadre)
 */
@Entity
@Table(name = "giocate_partita",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "match_id", "market"}))
public class GiocataPartita extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "user_id", nullable = false)
    public Long userId;

    @Column(name = "match_id", nullable = false)
    public Long matchId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    public Scommessa.Market market;

    @Column(nullable = false, length = 50)
    public String prediction;

    @Column(name = "is_correct")
    public Boolean isCorrect;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt = LocalDateTime.now();

    /** Mercati ammessi per le scommesse di partita. */
    public static boolean isMatchMarket(Scommessa.Market m) {
        return m == Scommessa.Market.GOAL_NOGOAL || m == Scommessa.Market.WINNER
                || m == Scommessa.Market.EXACT_SCORE || m == Scommessa.Market.FIRST_SCORER;
    }

    public static List<GiocataPartita> findByUser(Long userId) {
        return find("userId = ?1 order by id desc", userId).list();
    }

    public static List<GiocataPartita> findByMatch(Long matchId) {
        return find("matchId", matchId).list();
    }

    public static GiocataPartita findByUserMatchMarket(Long userId, Long matchId, Scommessa.Market market) {
        return find("userId = ?1 and matchId = ?2 and market = ?3", userId, matchId, market).firstResult();
    }
}
