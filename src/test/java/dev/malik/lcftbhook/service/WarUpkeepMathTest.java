package dev.malik.lcftbhook.service;

import org.junit.jupiter.api.Test;

import static dev.malik.lcftbhook.service.WarUpkeepMath.*;
import static org.junit.jupiter.api.Assertions.*;

class WarUpkeepMathTest {

    // --- geometricFactor ---

    @Test
    void geometricFactor_negativeK_returnsOne() {
        assertEquals(1.0, geometricFactor(-1, 2.0));
    }

    @Test
    void geometricFactor_zeroWars_returnsOne() {
        assertEquals(1.0, geometricFactor(0, 2.0));
    }

    @Test
    void geometricFactor_oneWar_l2() {
        assertEquals(3.0, geometricFactor(1, 2.0));
    }

    @Test
    void geometricFactor_twoWars_l2() {
        assertEquals(7.0, geometricFactor(2, 2.0));
    }

    @Test
    void geometricFactor_threeWars_l2() {
        assertEquals(15.0, geometricFactor(3, 2.0));
    }

    @Test
    void geometricFactor_multiplierOne_usesLinear() {
        assertEquals(1.0, geometricFactor(0, 1.0));
        assertEquals(2.0, geometricFactor(1, 1.0));
        assertEquals(4.0, geometricFactor(3, 1.0));
    }

    @Test
    void geometricFactor_fractionalL() {
        assertEquals(2.5, geometricFactor(1, 1.5), 1e-9);
    }

    // --- ordinalWarTermCopper (0-based n) ---

    @Test
    void ordinalTerm_zeroBase_returnsZero() {
        assertEquals(0L, ordinalWarTermCopper(0L, 0, 2.0));
    }

    @Test
    void ordinalTerm_negativeBase_returnsZero() {
        assertEquals(0L, ordinalWarTermCopper(-100L, 0, 2.0));
    }

    @Test
    void ordinalTerm_negativeIndex_returnsZero() {
        assertEquals(0L, ordinalWarTermCopper(100L, -1, 2.0));
    }

    @Test
    void ordinalTerm_firstWar_usesMultiplierOne() {
        assertEquals(100L, ordinalWarTermCopper(100L, 0, 2.0));
    }

    @Test
    void ordinalTerm_secondWar_l2() {
        assertEquals(200L, ordinalWarTermCopper(100L, 1, 2.0));
    }

    @Test
    void ordinalTerm_thirdWar_l2() {
        assertEquals(400L, ordinalWarTermCopper(100L, 2, 2.0));
    }

    @Test
    void ordinalTerm_secondWar_l1_5() {
        assertEquals(150L, ordinalWarTermCopper(100L, 1, 1.5));
    }

    @Test
    void ordinalTerm_overflow_cappedAtLongMax() {
        long result = ordinalWarTermCopper(Long.MAX_VALUE / 2, 100, 2.0);
        assertEquals(Long.MAX_VALUE, result);
    }

    @Test
    void ordinalTerm_infinity_cappedAtLongMax() {
        long result = ordinalWarTermCopper(1_000_000_000L, 1000, 2.0);
        assertEquals(Long.MAX_VALUE, result);
    }

    @Test
    void ordinalTerm_neverNegative() {
        for (int n = 0; n <= 200; n++) {
            long v = ordinalWarTermCopper(1_000_000L, n, 2.0);
            assertTrue(v >= 0, "Negative at n=" + n);
        }
    }

    // --- sumOrdinalIncomingTerms ---

    @Test
    void sumOrdinal_zeroWars_returnsZero() {
        assertEquals(0L, sumOrdinalIncomingTerms(100L, 0, 2.0));
    }

    @Test
    void sumOrdinal_zeroBase_returnsZero() {
        assertEquals(0L, sumOrdinalIncomingTerms(0L, 3, 2.0));
    }

    @Test
    void sumOrdinal_oneWar_usesMultiplierOne() {
        assertEquals(100L, sumOrdinalIncomingTerms(100L, 1, 2.0));
    }

    @Test
    void sumOrdinal_twoWars_l2() {
        assertEquals(300L, sumOrdinalIncomingTerms(100L, 2, 2.0));
    }

    @Test
    void sumOrdinal_threeWars_l2() {
        assertEquals(700L, sumOrdinalIncomingTerms(100L, 3, 2.0));
    }

    @Test
    void sumOrdinal_overflow_cappedAtLongMax() {
        long result = sumOrdinalIncomingTerms(Long.MAX_VALUE, 10, 2.0);
        assertEquals(Long.MAX_VALUE, result);
    }

    @Test
    void sumOrdinal_neverNegative() {
        for (int wars = 1; wars <= 100; wars++) {
            long v = sumOrdinalIncomingTerms(1_000_000_000L, wars, 2.0);
            assertTrue(v >= 0, "Negative at wars=" + wars);
        }
    }

    @Test
    void totalUpkeepFactor_oneIncomingWar() {
        assertEquals(2.0, totalUpkeepFactor(1, 1.2), 1e-9);
    }

    @Test
    void formatExtraTermSum_oneWar_showsOne() {
        assertEquals("1", formatExtraTermSum(1, 1.2));
    }

    @Test
    void formatExtraTermSum_twoWars_l1_2() {
        assertEquals("1 + 1.20", formatExtraTermSum(2, 1.2));
    }

    @Test
    void formatTotalUpkeepFactor_oneIncomingWar() {
        assertEquals("2", formatTotalUpkeepFactor(1, 1.2));
    }

    @Test
    void formatGeometricFactor_zeroWars_returnsOne() {
        assertEquals("1", formatGeometricFactor(0, 2.0));
    }

    @Test
    void formatGeometricFactor_oneWar_l2() {
        assertEquals("3", formatGeometricFactor(1, 2.0));
    }

    @Test
    void formatGeometricFactor_multiplierOne() {
        assertEquals("3", formatGeometricFactor(2, 1.0));
    }
}
