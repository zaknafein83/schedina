package it.schedina.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "players")
public class Player extends PanacheEntityBase {

    public enum Role { GK, DEF, MID, FWD }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "first_name", nullable = false, length = 100)
    public String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    public String lastName;

    @Column(name = "team_id")
    public Long teamId;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    public Role role;

    @Column(name = "is_active", nullable = false)
    public boolean isActive = true;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt = LocalDateTime.now();

    public String fullName() {
        return (firstName + " " + lastName).trim();
    }

    public static List<Player> findByTeam(Long teamId) {
        return find("teamId", teamId).list();
    }
}
