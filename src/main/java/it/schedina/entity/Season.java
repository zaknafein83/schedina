package it.schedina.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "seasons")
public class Season extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** Etichetta visibile, es. "2025-26" */
    @Column(nullable = false, unique = true, length = 50)
    public String label;

    @Column(name = "start_date")
    public LocalDate startDate;

    @Column(name = "end_date")
    public LocalDate endDate;

    /** Una sola stagione "corrente" alla volta (vincolo unique parziale a livello DB). */
    @Column(name = "is_current", nullable = false)
    public boolean isCurrent = false;

    public static Season findCurrent() {
        return find("isCurrent", true).firstResult();
    }
}
