package it.schedina.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Regola riusabile: definisce le soglie di vittoria della schedina (punteggio a match esatto).
 * Una {@link Giornata} ne seleziona una; lo scoring legge le soglie dalla regola.
 */
@Entity
@Table(name = "rules")
public class Rule extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, length = 150)
    public String name;

    /** Punteggi vincenti (match esatto), es. [12, 13]. */
    @Convert(converter = JsonListConverters.IntList.class)
    @Column(name = "winning_thresholds", nullable = false, columnDefinition = "text")
    public List<Integer> winningThresholds = new ArrayList<>();

    @Column(name = "is_active", nullable = false)
    public boolean isActive = true;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt = LocalDateTime.now();

    public static List<Rule> allOrdered() {
        return find("order by isActive desc, name").list();
    }
}
