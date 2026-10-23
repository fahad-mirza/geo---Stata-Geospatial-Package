package org.fahadmirza.geo.stata;

/**
 * Stata return codes used by geo's javacall entry points.
 *
 * <p>Values verified against Stata's own constants by tests/stata/check_stubs.do on 23 Oct 2026
 * (StataNow 19.5): SFIToolkit.RC_SYNTAX_ERROR = 198, RC_GENERAL_EXCEPTION = 5100. They are copied
 * here because the sfi-stub module deliberately carries no constants.
 */
public final class ReturnCodes {
    /** Wrong number or form of arguments ("invalid syntax"). */
    public static final int SYNTAX = 198;
    /** A Java exception stopped the command (Stata's general Java exception code). */
    public static final int JAVA_EXCEPTION = 5100;

    private ReturnCodes() {
    }
}
