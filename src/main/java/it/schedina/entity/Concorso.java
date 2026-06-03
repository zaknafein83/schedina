package it.schedina.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Concorso del Totocalcio: la "schedina" giocabile. Fa riferimento a un turno (number) e
 * contiene una selezione di partite (scelte a mano dall'admin) prese dalle giornate di
 * campionato di quel turno, anche di leghe diverse. L'utente lo compila (1X2 + Under/Over).
 */
@Entity
@Table(name = "concorsi")
public class Concorso extends PanacheEntityBase {

    public enum Status { DRAFT, OPEN, CLOSED, PROCESSED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "season_id")
    public Long seasonId;

    /** Regola con le soglie vincenti della schedina (opzionale fino all'elaborazione). */
    @Column(name = "rule_id")
    public Long ruleId;

    /** Turno di riferimento: le partite selezionate sono tutte di questo numero di giornata. */
    @Column(nullable = false)
    public int number;

    @Column(nullable = false, length = 150)
    public String name;

    @Column(name = "open_at", nullable = false)
    public LocalDateTime openAt;

    @Column(name = "close_at", nullable = false)
    public LocalDateTime closeAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public Status status = Status.DRAFT;

    /** Soglie vincenti legacy/fallback se ruleId è null. */
    @Convert(converter = JsonListConverters.IntList.class)
    @Column(name = "winning_thresholds", nullable = false, columnDefinition = "text")
    public List<Integer> winningThresholds = new ArrayList<>();

    /** Premi (€) per soglia del Totocalcio (1X2): per i concorsi legacy = inseriti a mano;
     *  per quelli a montepremi = premio per-vincitore CALCOLATO all'elaborazione (per UI/audit). */
    @Convert(converter = JsonListConverters.IntLongMap.class)
    @Column(name = "prizes_1x2", nullable = false, columnDefinition = "text")
    public Map<Integer, Long> prizes1x2 = new LinkedHashMap<>();

    /** Premi (€) per soglia dell'Under/Over (vedi prizes1x2). */
    @Convert(converter = JsonListConverters.IntLongMap.class)
    @Column(name = "prizes_uo", nullable = false, columnDefinition = "text")
    public Map<Integer, Long> prizesUo = new LinkedHashMap<>();

    /** Montepremi in ingresso usato dal concorso (snapshot), per gioco. Solo concorsi a montepremi. */
    @Column(name = "montepremi_1x2")
    public Long montepremi1x2;

    @Column(name = "montepremi_uo")
    public Long montepremiUo;

    /** true = premi calcolati dal montepremi (catena per turno); false = legacy con premi a mano. */
    @Column(name = "montepremi_managed", nullable = false)
    public boolean montepremiManaged = true;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt = LocalDateTime.now();

    public static List<Concorso> findManagedOrdered() {
        return find("montepremiManaged = true order by number, id").list();
    }

    /** Premio del Totocalcio per un dato numero di risultati esatti (0 se non previsto). */
    public long prize1x2For(int correct) {
        Long p = prizes1x2 != null ? prizes1x2.get(correct) : null;
        return p != null ? p : 0L;
    }

    /** Premio dell'Under/Over per un dato numero di risultati esatti (0 se non previsto). */
    public long prizeUoFor(int correct) {
        Long p = prizesUo != null ? prizesUo.get(correct) : null;
        return p != null ? p : 0L;
    }

    public static List<Concorso> findOpen() {
        return find("status = ?1 order by closeAt", Status.OPEN).list();
    }

    public static List<Concorso> allOrdered() {
        return find("order by number desc, id desc").list();
    }
}
