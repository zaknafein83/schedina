package it.schedina.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.util.List;

/**
 * Opzione selezionabile di una {@link Scommessa}. Sorgente unica delle scelte ammesse,
 * uniforme per tutti i mercati:
 *  - TOKEN  → ref "1"/"X"/"2"/"U"/"O"/"GOAL"/"NOGOAL"
 *  - TEAM   → ref = teamId
 *  - PLAYER → ref = playerId (oppure "NONE" per "nessuno")
 */
@Entity
@Table(name = "bet_options")
public class BetOption extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "bet_id", nullable = false)
    public Long betId;

    @Column(nullable = false, length = 50)
    public String ref;

    @Column(nullable = false, length = 200)
    public String label;

    @Column(name = "display_order", nullable = false)
    public int displayOrder = 0;

    public static List<BetOption> findByBet(Long betId) {
        return find("betId = ?1 order by displayOrder, id", betId).list();
    }

    public static boolean existsForBet(Long betId, String ref) {
        return count("betId = ?1 and ref = ?2", betId, ref) > 0;
    }

    public static void deleteByBet(Long betId) {
        delete("betId", betId);
    }
}
