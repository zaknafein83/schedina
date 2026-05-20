package it.schedina.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.util.List;

/**
 * Competizione di cui si pronosticano vincitore / capocannoniere / etc.
 * Esempi: Serie A, Serie B, Serie C, Coppa Italia, Champions League.
 * Una Tournament può corrispondere ma è indipendente da una League.
 */
@Entity
@Table(name = "tournaments")
public class Tournament extends PanacheEntityBase {

    public enum Type {
        /** Campionato nazionale (es. Serie A, Serie B, Serie C) */
        LEAGUE_NATIONAL,
        /** Coppa nazionale (es. Coppa Italia) */
        CUP_NATIONAL,
        /** Coppa internazionale (es. Champions League) */
        CUP_INTERNATIONAL
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true, length = 150)
    public String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    public Type type;

    /** Nullable per le competizioni internazionali */
    @Column(length = 100)
    public String country;

    @Column(name = "is_active", nullable = false)
    public boolean isActive = true;

    public static List<Tournament> findActive() {
        return find("isActive = true order by type, name").list();
    }
}
