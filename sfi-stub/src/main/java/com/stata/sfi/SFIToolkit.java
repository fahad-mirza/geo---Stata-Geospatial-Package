package com.stata.sfi;

import java.io.File;

/**
 * Compile-only stand-in for {@code com.stata.sfi.SFIToolkit} (output, polling, utilities).
 *
 * <p>Signatures from https://www.stata.com/java/api19/com/stata/sfi/SFIToolkit.html
 * (checked 23 Oct 2026). This is a SUBSET: only the String-based display methods and the
 * utilities geo uses. The overloads that take a DisplayMode or an Object are left out until needed.
 * The RC_* return-code constants are deliberately left out (see the rule in {@link Data}).
 *
 * <p>Output and polling stay on the main thread: pollnow() and pollstd() do nothing on a worker.
 */
public final class SFIToolkit {
    private SFIToolkit() {
    }

    // ----- Output (synchronized) -----
    public static void display(String s) { throw Stub.fail(); }
    public static void display(String s, boolean asis) { throw Stub.fail(); }
    public static void displayln() { throw Stub.fail(); }
    public static void displayln(String s) { throw Stub.fail(); }
    public static void error(String s) { throw Stub.fail(); }
    public static void error(String s, boolean asis) { throw Stub.fail(); }
    public static void errorln(String s) { throw Stub.fail(); }
    public static int errorMsg(int rc) { throw Stub.fail(); }
    public static boolean errorDebug(String s) { throw Stub.fail(); }
    public static boolean errorDebug(Throwable t) { throw Stub.fail(); }

    // ----- Polling: keeps Stata responsive and detects the Break key (main thread only) -----
    public static int pollnow() { throw Stub.fail(); }
    public static int pollstd() { throw Stub.fail(); }

    // ----- Utilities -----
    public static String formatValue(double val, String format) { throw Stub.fail(); }  // thread-safe
    public static String stackTraceToString(Throwable t) { throw Stub.fail(); }         // thread-safe
    public static String stackTraceToString(Exception e) { throw Stub.fail(); }         // thread-safe
    public static boolean isValidName(String s) { throw Stub.fail(); }                  // thread-safe
    public static boolean isValidVariableName(String s) { throw Stub.fail(); }          // thread-safe
    public static double getCallerVersion() { throw Stub.fail(); }
    public static String getTempFile() { throw Stub.fail(); }
    public static String getTempName() { throw Stub.fail(); }
    public static String getWorkingDir() { throw Stub.fail(); }
    public static File resolvePath(String path) { throw Stub.fail(); }   // real one declares InvalidPathException (unchecked)
    public static String processTildePath(String path) { throw Stub.fail(); }
    public static void setRC(int rc) { throw Stub.fail(); }
    public static int executeCommand(String command, boolean echo) { throw Stub.fail(); } // main thread; not in java: blocks
}
