package it.schedina.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "rules")
public class Rule extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, length = 150)
    public String name;

    @Column(columnDefinition = "text")
    public String description;

    /** Lega di riferimento. Opzionale: i concorsi stagionali non sono legati a una sola lega. */
    @Column(name = "league_id")
    public Long leagueId;

    /** Quante scommesse deve coprire la schedina. */
    @Column(name = "required_bets", nullable = false)
    public int requiredBets;

    /** Punteggi vincenti (match esatto), es. [12, 13]. */
    @Convert(converter = JsonListConverters.IntList.class)
    @Column(name = "winning_thresholds", nullable = false, columnDefinition = "text")
    public List<Integer> winningThresholds = new ArrayList<>();

    @Column(name = "max_schedine_per_user")
    public Integer maxSchedinePerUser;

    @Column(name = "full_completion_required", nullable = false)
    public boolean fullCompletionRequired = true;

    @Column(name = "is_active", nullable = false)
    public boolean isActive = true;
}
