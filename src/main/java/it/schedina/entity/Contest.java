package it.schedina.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "contests")
public class Contest extends PanacheEntityBase {

    public enum Status {
        DRAFT, SCHEDULED, OPEN, CLOSED, PROCESSING, PROCESSED, CANCELLED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, length = 150)
    public String name;

    @Column(columnDefinition = "text")
    public String description;

    @Column(name = "league_id", nullable = false)
    public Long leagueId;

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
}
