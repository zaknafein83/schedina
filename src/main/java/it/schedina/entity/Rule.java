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

    @Column(name = "league_id", nullable = false)
    public Long leagueId;

    @Column(name = "required_matches", nullable = false)
    public int requiredMatches;

    /** Configurabili da backoffice – es. [12, 13] */
    @Convert(converter = JsonListConverters.IntList.class)
    @Column(name = "winning_thresholds", nullable = false, columnDefinition = "text")
    public List<Integer> winningThresholds = new ArrayList<>();

    @Column(name = "max_coupons_per_user")
    public Integer maxCouponsPerUser;

    @Column(name = "max_doubles", nullable = false)
    public int maxDoubles = 0;

    @Column(name = "max_triples", nullable = false)
    public int maxTriples = 0;

    @Column(name = "full_completion_required", nullable = false)
    public boolean fullCompletionRequired = true;

    @Column(name = "is_active", nullable = false)
    public boolean isActive = true;
}
