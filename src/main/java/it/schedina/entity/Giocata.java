package it.schedina.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Puntata singola di un utente su una {@link Scommessa} extra (fine stagione / di giornata).
 * Indipendente dalla schedina: vinta/persa per conto suo.
 */
@Entity
@Table(name = "giocate")
public class Giocata extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "user_id", nullable = false)
    public Long userId;

    @Column(name = "scommessa_id", nullable = false)
    public Long scommessaId;

    @Column(name = "choice_ref", nullable = false, length = 50)
    public String choiceRef;

    @Column(name = "is_correct")
    public Boolean isCorrect;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt = LocalDateTime.now();

    public static List<Giocata> findByUser(Long userId) {
        return find("userId = ?1 order by id desc", userId).list();
    }

    public static List<Giocata> findByScommessa(Long scommessaId) {
        return find("scommessaId", scommessaId).list();
    }

    public static Giocata findByUserAndScommessa(Long userId, Long scommessaId) {
        return find("userId = ?1 and scommessaId = ?2", userId, scommessaId).firstResult();
    }
}
