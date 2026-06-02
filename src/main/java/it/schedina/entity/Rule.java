package it.schedina.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    /** Premio (€) per soglia vincente, es. {13:500000}. Uguale per i due giochi (1X2 e U/O). */
    @Convert(converter = JsonListConverters.IntLongMap.class)
    @Column(nullable = false, columnDefinition = "text")
    public Map<Integer, Long> prizes = new LinkedHashMap<>();

    @Column(name = "is_active", nullable = false)
    public boolean isActive = true;

    /** Premio per un dato numero di risultati esatti (0 se non previsto). */
    public long prizeFor(int correct) {
        Long p = prizes != null ? prizes.get(correct) : null;
        return p != null ? p : 0L;
    }

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt = LocalDateTime.now();

    public static List<Rule> allOrdered() {
        return find("order by isActive desc, name").list();
    }
}
