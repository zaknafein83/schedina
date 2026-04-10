package it.schedina.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

@Entity
@Table(name = "teams")
public class Team extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, length = 150)
    public String name;

    @Column(name = "short_name", length = 10)
    public String shortName;

    @Column(name = "league_id", nullable = false)
    public Long leagueId;

    @Column(name = "is_active", nullable = false)
    public boolean isActive = true;

    // Convenience lookup
    public League league() {
        return League.findById(leagueId);
    }
}
