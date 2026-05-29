package it.schedina.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Contenitore di Scommesse con regole di gioco. Fonde i vecchi Contest (a giornata)
 * e SeasonPool (stagionali): la differenza è solo nel {@link Kind} e nelle scommesse incluse.
 */
@Entity
@Table(name = "concorsi")
public class Concorso extends PanacheEntityBase {

    public enum Kind { MATCHDAY, SEASON }

    public enum Status { DRAFT, OPEN, CLOSED, PROCESSED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, length = 150)
    public String name;

    @Column(columnDefinition = "text")
    public String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public Kind kind = Kind.MATCHDAY;

    @Column(name = "rule_id", nullable = false)
    public Long ruleId;

    @Column(name = "open_at", nullable = false)
    public LocalDateTime openAt;

    @Column(name = "close_at", nullable = false)
    public LocalDateTime closeAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public Status status = Status.DRAFT;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt = LocalDateTime.now();

    public Rule rule() {
        return Rule.findById(ruleId);
    }

    public static List<Concorso> findOpen() {
        return find("status = ?1 order by closeAt", Status.OPEN).list();
    }
}
