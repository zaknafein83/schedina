package it.schedina.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Giornata di campionato (per-lega): è solo calendario. Identifica un turno (number) di una
 * lega specifica e contiene le {@link Match} di quella lega in quel turno. Non è giocabile:
 * la cosa giocabile è il {@link Concorso}, che seleziona partite dalle giornate dello stesso turno.
 */
@Entity
@Table(name = "giornate")
public class Giornata extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "league_id", nullable = false)
    public Long leagueId;

    @Column(name = "season_id")
    public Long seasonId;

    @Column(nullable = false)
    public int number;

    @Column(nullable = false, length = 150)
    public String name;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt = LocalDateTime.now();

    public static List<Giornata> findByLeague(Long leagueId) {
        return find("leagueId = ?1 order by number desc, id desc", leagueId).list();
    }

    public static List<Giornata> findByNumber(int number) {
        return find("number = ?1 order by leagueId", number).list();
    }

    public static List<Giornata> allOrdered() {
        return find("order by number desc, leagueId, id desc").list();
    }
}
