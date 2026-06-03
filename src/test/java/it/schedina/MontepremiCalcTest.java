package it.schedina;

import it.schedina.service.MontepremiService;
import it.schedina.service.MontepremiService.GameOutcome;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Test puri del calcolo montepremi (premi per soglia, divisione, erosione, crescita, floor). */
class MontepremiCalcTest {

    @Test
    void tredici_un_vincitore_75pct_e_floor() {
        // M=500k, N=45, 1 vincitore al 13 → 75% = 375.000; erosione 375k → 125k → floor 300k.
        GameOutcome o = MontepremiService.computeGame(500_000L, 45, Map.of(13, 1));
        assertEquals(375_000L, o.prizePerWinner().get(13));
        assertEquals(375_000L, o.erosion());
        assertEquals(300_000L, o.next());
    }

    @Test
    void tredici_diviso_tra_tre_vincitori() {
        // 375.000 / 3 = 125.000 a testa; erosione = quota intera 375k.
        GameOutcome o = MontepremiService.computeGame(500_000L, 45, Map.of(13, 3));
        assertEquals(125_000L, o.prizePerWinner().get(13));
        assertEquals(375_000L, o.erosion());
        assertEquals(300_000L, o.next());
    }

    @Test
    void dodici_quaranta_pct() {
        // 40% di 500k = 200.000; erosione 200k → 300k (esatto floor).
        GameOutcome o = MontepremiService.computeGame(500_000L, 45, Map.of(12, 1));
        assertEquals(200_000L, o.prizePerWinner().get(12));
        assertEquals(200_000L, o.erosion());
        assertEquals(300_000L, o.next());
    }

    @Test
    void undici_ventotto_pct() {
        // 11 = 28% (= 70% del 40%) = 140.000.
        GameOutcome o = MontepremiService.computeGame(500_000L, 45, Map.of(11, 1));
        assertEquals(140_000L, o.prizePerWinner().get(11));
        assertEquals(140_000L, o.erosion());
        assertEquals(360_000L, o.next()); // 500k - 140k = 360k (sopra il floor)
    }

    @Test
    void solo_9_e_10_non_erodono_e_crescita() {
        // 9 = M/N = 500000/45 = 11111; 10 = +10k = 21111; nessuna fascia 11/12/13 → +30k.
        GameOutcome o = MontepremiService.computeGame(500_000L, 45, Map.of(9, 2, 10, 3));
        assertEquals(11_111L, o.prizePerWinner().get(9));
        assertEquals(21_111L, o.prizePerWinner().get(10));
        assertEquals(0L, o.erosion());
        assertEquals(530_000L, o.next());
    }

    @Test
    void nessun_vincitore_crescita_30k() {
        GameOutcome o = MontepremiService.computeGame(500_000L, 45, Map.of());
        assertEquals(0L, o.erosion());
        assertEquals(530_000L, o.next());
    }

    @Test
    void erosione_oltre_il_floor_si_ferma_a_300k() {
        // 11+12+13 = (28+40+75)% = 143% di 500k = 715k > M → floor 300k.
        GameOutcome o = MontepremiService.computeGame(500_000L, 45, Map.of(11, 1, 12, 1, 13, 1));
        assertEquals(715_000L, o.erosion());
        assertEquals(300_000L, o.next());
    }
}
