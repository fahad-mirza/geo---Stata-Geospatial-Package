package org.fahadmirza.geo.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ToleranceTest {

    @Test
    void acceptsDifferencesBelowOnePartIn1e10() {
        assertTrue(Tolerance.matchesReference(1_000_000.0, 1_000_000.0 + 5e-5));
        // Round 1: fixed-order vs sequential sums differed by ~3 parts in 1e13.
        assertTrue(Tolerance.matchesReference(1.0, 1.0 + 3e-13));
    }

    @Test
    void rejectsDifferencesAboveTolerance() {
        assertFalse(Tolerance.matchesReference(1.0, 1.0 + 1e-8));
        assertFalse(Tolerance.matchesReference(0.0, 1e-9));
    }

    @Test
    void handlesSpecialValues() {
        assertTrue(Tolerance.matchesReference(Double.NaN, Double.NaN));
        assertFalse(Tolerance.matchesReference(Double.NaN, 0.0));
        assertTrue(Tolerance.matchesReference(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY));
        assertFalse(Tolerance.matchesReference(Double.POSITIVE_INFINITY, 1e308));
        assertTrue(Tolerance.matchesReference(0.0, -0.0));
    }
}
