package com.stata.sfi;

import java.io.IOException;

/**
 * Compile-only stand-in for {@code com.stata.sfi.Data} (access to the current Stata dataset).
 *
 * <p>Signatures copied from the official Stata 19 Java API page:
 * https://www.stata.com/java/api19/com/stata/sfi/Data.html (checked 23 Oct 2026).
 * The comment on each method repeats Stata's own thread-safety annotation:
 * "thread-safe" (Stata's @ThreadSafe: may be called from worker threads) or
 * "synchronized" (Stata's @Synchronized: one thread at a time; keep out of parallel loops).
 *
 * <p>Rule: stubs declare NO constants (such as TYPE_DOUBLE). Java copies the value of a
 * compile-time constant into the calling class, so a wrong stub value would silently ship inside
 * geo.jar. tests/stata/check_stubs.do prints the real values; read them at run time instead.
 */
public final class Data {
    private Data() {
    }

    // ----- Adding and dropping variables (all synchronized) -----
    public static int addVarByte(String name) { throw Stub.fail(); }                 // synchronized
    public static int addVarDouble(String name) { throw Stub.fail(); }               // synchronized
    public static int addVarFloat(String name) { throw Stub.fail(); }                // synchronized
    public static int addVarInt(String name) { throw Stub.fail(); }                  // synchronized
    public static int addVarLong(String name) { throw Stub.fail(); }                 // synchronized
    public static int addVarStr(String name, int length) { throw Stub.fail(); }      // synchronized
    public static int addVarStrL(String name) { throw Stub.fail(); }                 // synchronized
    public static int dropVar(int var) { throw Stub.fail(); }                        // synchronized
    public static int renameVar(int var, String newname) { throw Stub.fail(); }      // synchronized

    // ----- strL (long string / binary) access through a StrLConnector -----
    public static int allocateStrL(StrLConnector sc, long size) { throw Stub.fail(); }                 // synchronized
    public static int allocateStrL(StrLConnector sc, long size, boolean binary) { throw Stub.fail(); } // synchronized
    public static int readBytes(StrLConnector sc, byte[] b) throws IOException { throw Stub.fail(); }  // synchronized
    public static int readBytes(StrLConnector sc, byte[] b, int off, int len) throws IOException { throw Stub.fail(); } // synchronized
    public static int writeBytes(StrLConnector sc, byte[] b) { throw Stub.fail(); }                    // synchronized
    public static int writeBytes(StrLConnector sc, byte[] b, int off, int len) { throw Stub.fail(); }  // synchronized
    public static int storeBytes(StrLConnector sc, byte[] bytes, boolean binary) { throw Stub.fail(); } // synchronized

    // ----- Reading values -----
    public static double getNum(int var, long obs) { throw Stub.fail(); }            // thread-safe
    public static String getStr(int var, long obs) { throw Stub.fail(); }            // synchronized
    public static String getStrf(int var, long obs) { throw Stub.fail(); }           // thread-safe
    public static String getFormattedValue(int var, long obs, boolean bValueLabel) { throw Stub.fail(); } // synchronized

    // ----- Writing values -----
    // The *Fast methods return 0 even when Stata stores something else (round 1 tests):
    // out-of-range -> missing, decimals into integer types -> truncated, strings cut at the byte width.
    // geo validates every value and UTF-8 byte length in Java before calling them.
    public static int storeNum(int var, long obs, double value) { throw Stub.fail(); }       // synchronized
    public static int storeNumFast(int var, long obs, double value) { throw Stub.fail(); }   // thread-safe
    public static int storeStr(int var, long obs, String value) { throw Stub.fail(); }       // synchronized
    public static int storeStrf(int var, long obs, String value) { throw Stub.fail(); }      // synchronized
    public static int storeStrfFast(int var, long obs, String value) { throw Stub.fail(); }  // thread-safe
    public static void updateModified() { throw Stub.fail(); }                               // synchronized; call once after fast stores

    // ----- Observations -----
    public static long getObsTotal() { throw Stub.fail(); }                          // thread-safe
    public static int setObsTotal(long obs) { throw Stub.fail(); }                   // synchronized
    public static long getObsParsedIn1() { throw Stub.fail(); }                      // synchronized
    public static long getObsParsedIn2() { throw Stub.fail(); }                      // synchronized
    public static boolean isParsedIfTrue(long obs) { throw Stub.fail(); }            // thread-safe

    // ----- Variables: lookup and metadata -----
    public static int getVarCount() { throw Stub.fail(); }                           // thread-safe
    public static int getVarIndex(String varname) { throw Stub.fail(); }             // synchronized; returns 0 if absent
    public static String getVarName(int var) { throw Stub.fail(); }                  // thread-safe
    public static String getVarLabel(int var) { throw Stub.fail(); }                 // thread-safe
    public static int setVarLabel(int var, String label) { throw Stub.fail(); }      // synchronized
    public static String getVarFormat(int var) { throw Stub.fail(); }                // thread-safe
    public static int setVarFormat(int var, String format) { throw Stub.fail(); }    // synchronized
    public static int getType(int var) { throw Stub.fail(); }                        // thread-safe
    public static int getStrVarWidth(int var) { throw Stub.fail(); }                 // thread-safe
    public static boolean isVarTypeStr(int var) { throw Stub.fail(); }               // thread-safe
    public static boolean isVarTypeStrL(int var) { throw Stub.fail(); }              // thread-safe
    public static boolean isVarTypeString(int var) { throw Stub.fail(); }            // thread-safe
    public static boolean isAlias(int var) { throw Stub.fail(); }                    // thread-safe
    public static int getParsedVarCount() { throw Stub.fail(); }                     // synchronized
    public static int mapParsedVarIndex(int var) { throw Stub.fail(); }              // thread-safe
    public static boolean isVarlistSpecified() { throw Stub.fail(); }                // synchronized

    // ----- Utilities -----
    public static int getBestType(double value) { throw Stub.fail(); }               // thread-safe
    public static int getMaxStrLength() { throw Stub.fail(); }                       // thread-safe
    public static int getMaxVars() { throw Stub.fail(); }                            // thread-safe
    public static Double getRealOfString(String s) { throw Stub.fail(); }            // thread-safe
    public static String makeVarName(String s, boolean retainCase) { throw Stub.fail(); } // thread-safe
}
