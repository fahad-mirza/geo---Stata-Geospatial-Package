package org.fahadmirza.geo.core;

/**
 * Numeric tolerances used when comparing geo's results with reference tools.
 *
 * <p>Foundation specification: results must be identical across runs and thread counts
 * (exact equality, checked separately), and within a relative tolerance of 1e-10 of
 * exactextract, terra and geopandas. Round 1 showed that a fixed-order parallel sum and a plain
 * sequential sum differ by about 3 parts in 10^13, well inside this tolerance.
 */
public final class Tolerance {
    /** Relative tolerance against R and Python reference outputs. */
    public static final double REFERENCE_RELATIVE = 1e-10;

    private Tolerance() {
    }

    /**
     * True when {@code actual} is within {@code relTol} of {@code expected}, relative to the larger
     * magnitude of the two. Two NaN values count as equal (both mean "no result"); NaN against a
     * number does not. Infinities must match exactly.
     */
    public static boolean withinRelative(double expected, double actual, double relTol) {
        if (Double.isNaN(expected) || Double.isNaN(actual)) {
            return Double.isNaN(expected) && Double.isNaN(actual);
        }
        if (Double.isInfinite(expected) || Double.isInfinite(actual)) {
            return expected == actual;
        }
        if (expected == actual) {
            return true;
        }
        double scale = Math.max(Math.abs(expected), Math.abs(actual));
        return Math.abs(expected - actual) <= relTol * scale;
    }

    /** {@link #withinRelative(double, double, double)} at the reference tolerance of 1e-10. */
    public static boolean matchesReference(double expected, double actual) {
        return withinRelative(expected, actual, REFERENCE_RELATIVE);
    }
}
