package it.schedina.service;

import it.schedina.entity.Concorso;
import it.schedina.entity.Rule;
import it.schedina.entity.Schedina;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Montepremi per gioco (Totocalcio 1X2 / Under-Over). Stato che si trascina di concorso in
 * concorso (catena per numero di turno), da cui si CALCOLANO i premi delle soglie:
 * <ul>
 *   <li>9  = montepremi / n° schedine giocate (intero a ciascun vincitore)</li>
 *   <li>10 = (9) + 10.000 (intero a ciascuno)</li>
 *   <li>11 = 28% del montepremi, diviso tra i vincitori dell'11</li>
 *   <li>12 = 40% del montepremi, diviso tra i vincitori del 12</li>
 *   <li>13 = 75% del montepremi, diviso tra i vincitori del 13</li>
 * </ul>
 * Dopo il concorso il montepremi evolve: erosione = somma delle quote di fascia 11/12/13
 * effettivamente vinte; se nessuna fascia 11/12/13 è vinta → +30k; pavimento a 300k. Le vincite
 * 9/10 non erodono. I concorsi legacy (montepremiManaged=false) restano fuori dalla catena.
 */
@ApplicationScoped
public class MontepremiService {

    public static final long INITIAL = 500_000L;
    public static final long FLOOR = 300_000L;
    public static final long GROWTH = 30_000L;
    public static final long BONUS_10 = 10_000L;

    /** Percentuale (su 100) della fascia 11/12/13; 0 per le altre soglie. */
    private static int quotaPct(int count) {
        return switch (count) {
            case 13 -> 75;
            case 12 -> 40;
            case 11 -> 28; // 70% del 40%
            default -> 0;
        };
    }

    private static List<Schedina> playedSchedine(Long concorsoId) {
        return Schedina.find("concorsoId = ?1 and status in ?2", concorsoId,
                List.of(Schedina.Status.CONFIRMED, Schedina.Status.PROCESSED,
                        Schedina.Status.WINNING, Schedina.Status.NOT_WINNING)).list();
    }

    /** Premi per-vincitore per soglia + montepremi successivo, per UN gioco. */
    public record GameOutcome(Map<Integer, Long> prizePerWinner, long erosion, long next) {}

    /** Calcolo puro (senza DB), testabile in isolamento. m = montepremi in ingresso, n = schedine giocate. */
    public static GameOutcome computeGame(long m, int n, Map<Integer, Integer> winnersByCount) {
        Map<Integer, Long> prize = new LinkedHashMap<>();
        long erosion = 0;
        for (Map.Entry<Integer, Integer> e : winnersByCount.entrySet()) {
            int count = e.getKey();
            int w = e.getValue();
            if (w <= 0) continue;
            long perWinner;
            if (count == 9) {
                perWinner = n > 0 ? m / n : 0;
            } else if (count == 10) {
                perWinner = (n > 0 ? m / n : 0) + BONUS_10;
            } else {
                int pct = quotaPct(count);
                if (pct == 0) {
                    perWinner = 0;
                } else {
                    long pool = m * pct / 100;
                    perWinner = pool / w;
                    erosion += pool; // l'intera quota di fascia esce dal montepremi
                }
            }
            prize.put(count, perWinner);
        }
        long next = erosion == 0 ? m + GROWTH : Math.max(FLOOR, m - erosion);
        return new GameOutcome(prize, erosion, next);
    }

    private static void countWinners(List<Schedina> played, Map<Integer, Integer> w1x2, Map<Integer, Integer> wUo) {
        for (Schedina s : played) {
            if (Boolean.TRUE.equals(s.isWinner1x2) && s.correct1x2Count != null)
                w1x2.merge(s.correct1x2Count, 1, Integer::sum);
            if (Boolean.TRUE.equals(s.isWinnerUo) && s.correctUoCount != null)
                wUo.merge(s.correctUoCount, 1, Integer::sum);
        }
    }

    /**
     * Ricalcola l'intera catena (per turno) dei concorsi a montepremi: assegna a ciascuno il
     * montepremi in ingresso e i premi calcolati, e a ogni schedina vincente il premio per-gioco.
     * Idempotente. Ritorna il montepremi corrente {1x2, U/O} (fine catena).
     */
    @Transactional
    public long[] recompute() {
        long m1 = INITIAL, mu = INITIAL;
        for (Concorso c : Concorso.findManagedOrdered()) {
            List<Schedina> played = playedSchedine(c.id);
            int n = played.size();
            Map<Integer, Integer> w1 = new LinkedHashMap<>(), wu = new LinkedHashMap<>();
            countWinners(played, w1, wu);

            GameOutcome o1 = computeGame(m1, n, w1);
            GameOutcome ou = computeGame(mu, n, wu);

            c.montepremi1x2 = m1;
            c.montepremiUo = mu;
            c.prizes1x2 = new LinkedHashMap<>(o1.prizePerWinner());
            c.prizesUo = new LinkedHashMap<>(ou.prizePerWinner());
            c.persist();

            for (Schedina s : played) {
                s.prize1x2 = Boolean.TRUE.equals(s.isWinner1x2) && s.correct1x2Count != null
                        ? o1.prizePerWinner().getOrDefault(s.correct1x2Count, 0L) : 0L;
                s.prizeUo = Boolean.TRUE.equals(s.isWinnerUo) && s.correctUoCount != null
                        ? ou.prizePerWinner().getOrDefault(s.correctUoCount, 0L) : 0L;
                s.persist();
            }

            // Il montepremi avanza solo se il concorso è elaborato (esiti definitivi).
            if (c.status == Concorso.Status.PROCESSED) {
                m1 = o1.next();
                mu = ou.next();
            }
        }
        return new long[]{m1, mu};
    }

    /** Montepremi corrente {1x2, U/O} = fine catena dei concorsi elaborati (read-only, no persist). */
    public long[] current() {
        long m1 = INITIAL, mu = INITIAL;
        for (Concorso c : Concorso.findManagedOrdered()) {
            if (c.status != Concorso.Status.PROCESSED) continue;
            List<Schedina> played = playedSchedine(c.id);
            Map<Integer, Integer> w1 = new LinkedHashMap<>(), wu = new LinkedHashMap<>();
            countWinners(played, w1, wu);
            m1 = computeGame(m1, played.size(), w1).next();
            mu = computeGame(mu, played.size(), wu).next();
        }
        return new long[]{m1, mu};
    }

    /** Montepremi in INGRESSO {1x2, U/O} per un concorso = catena dei turni precedenti elaborati. */
    public long[] inputFor(Concorso target) {
        long m1 = INITIAL, mu = INITIAL;
        for (Concorso c : Concorso.findManagedOrdered()) {
            if (c.id.equals(target.id) || c.number >= target.number) continue;
            if (c.status != Concorso.Status.PROCESSED) continue;
            List<Schedina> played = playedSchedine(c.id);
            Map<Integer, Integer> w1 = new LinkedHashMap<>(), wu = new LinkedHashMap<>();
            countWinners(played, w1, wu);
            m1 = computeGame(m1, played.size(), w1).next();
            mu = computeGame(mu, played.size(), wu).next();
        }
        return new long[]{m1, mu};
    }

    /** Riga di proiezione premi per una soglia (prima dell'elaborazione). */
    public record PrizeRow(int threshold, long amount1x2, long amountUo, boolean divided) {}

    /** Proiezione premi {montepremi + righe per soglia} per un concorso (managed o legacy). */
    public record Projection(boolean managed, long montepremi1x2, long montepremiUo,
                             int schedineGiocate, List<PrizeRow> prizes) {}

    private static long projectAmount(long m, int n, int t) {
        return switch (t) {
            case 9 -> m / Math.max(n, 1);
            case 10 -> m / Math.max(n, 1) + BONUS_10;
            default -> m * quotaPct(t) / 100; // 11/12/13 = quota (pool da dividere tra i vincitori)
        };
    }

    public Projection projectionFor(Concorso c) {
        int n = playedSchedine(c.id).size();
        Rule rule = c.ruleId != null ? Rule.findById(c.ruleId) : null;
        List<Integer> thresholds = rule != null ? rule.winningThresholds : c.winningThresholds;

        if (!c.montepremiManaged) {
            // Legacy: premi fissi inseriti a mano, niente montepremi né divisione.
            List<PrizeRow> rows = thresholds.stream().sorted()
                    .map(t -> new PrizeRow(t, c.prizes1x2.getOrDefault(t, 0L), c.prizesUo.getOrDefault(t, 0L), false))
                    .toList();
            return new Projection(false, 0, 0, n, rows);
        }

        long[] in = inputFor(c);
        long m1 = in[0], mu = in[1];
        List<PrizeRow> rows = thresholds.stream().sorted()
                .map(t -> new PrizeRow(t, projectAmount(m1, n, t), projectAmount(mu, n, t), quotaPct(t) > 0))
                .toList();
        return new Projection(true, m1, mu, n, rows);
    }
}
