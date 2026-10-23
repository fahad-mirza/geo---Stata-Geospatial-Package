package com.stata.sfi;

/**
 * Compile-only stand-in for {@code com.stata.sfi.Missing} (Stata missing values).
 *
 * <p>Signatures from https://www.stata.com/java/api19/com/stata/sfi/Missing.html and
 * Missing.Extended.html (checked 23 Oct 2026). All methods are thread-safe.
 *
 * <p>geo's convention: .a no valid pixels, .b outside the raster, .c invalid or empty geometry,
 * written with {@code Missing.getValue(Missing.Extended.a)} and so on.
 */
public class Missing {
    public Missing() {
        throw Stub.fail();
    }

    /** Stata's 27 missing values: system missing ({@code _sys}, shown as ".") and .a to .z. */
    public static enum Extended {
        _sys, a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t, u, v, w, x, y, z
    }

    public static double getValue() { throw Stub.fail(); }
    public static double getValue(Extended extendedMissing) { throw Stub.fail(); }
    public static Extended getMissing(double value) { throw Stub.fail(); }
    public static boolean isMissing(double value) { throw Stub.fail(); }
    public static boolean parseIsMissing(String s) { throw Stub.fail(); }
}
