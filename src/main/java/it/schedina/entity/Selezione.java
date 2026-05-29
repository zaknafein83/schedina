package it.schedina.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.util.List;

/**
 * Pronostico di una {@link Schedina} su una partita: 1X2 + Under/Over (scelta singola ciascuno).
 */
@Entity
@Table(name = "schedina_selezioni")
public class Selezione extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "schedina_id", nullable = false)
    public Long schedinaId;

    @Column(name = "match_id", nullable = false)
    public Long matchId;

    /** "1" / "X" / "2" */
    @Column(name = "choice_1x2", nullable = false, length = 1)
    public String choice1x2;

    /** "U" / "O" */
    @Column(name = "choice_uo", nullable = false, length = 1)
    public String choiceUo;

    @Column(name = "correct_1x2")
    public Boolean correct1x2;

    @Column(name = "correct_uo")
    public Boolean correctUo;

    public static List<Selezione> findBySchedina(Long schedinaId) {
        return find("schedinaId", schedinaId).list();
    }

    public static void deleteBySchedina(Long schedinaId) {
        delete("schedinaId", schedinaId);
    }
}
