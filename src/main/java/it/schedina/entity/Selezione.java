package it.schedina.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.util.List;

/**
 * Singola scelta di una {@link Schedina}: punta a una {@link Scommessa} con una choice singola.
 */
@Entity
@Table(name = "schedina_selezioni")
public class Selezione extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "schedina_id", nullable = false)
    public Long schedinaId;

    @Column(name = "bet_id", nullable = false)
    public Long betId;

    /** Riferimento dell'opzione scelta (token, teamId o playerId). */
    @Column(name = "choice_ref", nullable = false, length = 50)
    public String choiceRef;

    @Column(name = "is_correct")
    public Boolean isCorrect;

    public static List<Selezione> findBySchedina(Long schedinaId) {
        return find("schedinaId", schedinaId).list();
    }

    public static void deleteBySchedina(Long schedinaId) {
        delete("schedinaId", schedinaId);
    }
}
