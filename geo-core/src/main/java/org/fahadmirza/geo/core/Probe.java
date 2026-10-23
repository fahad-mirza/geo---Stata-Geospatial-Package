package org.fahadmirza.geo.core;

import java.util.concurrent.Callable;

/**
 * A small, self-contained check of one capability (for example "DuckDB answers SELECT 42"), with
 * its outcome, a plain-language detail and how long it took. Used by engine tests and by the
 * Stata-side check do-files, so both report the same thing in the same words.
 */
public final class Probe {

    /**
     * Outcome of one probe.
     *
     * @param name    short name of what was checked
     * @param ok      true if the capability works
     * @param detail  what was found, or why it failed
     * @param millis  wall-clock time in milliseconds (first calls include one-off setup costs)
     */
    public record Result(String name, boolean ok, String detail, long millis) {
    }

    /** Thrown inside a probe to report a clean failure with a readable message. */
    public static final class Failure extends Exception {
        private static final long serialVersionUID = 1L;

        public Failure(String message) {
            super(message);
        }
    }

    private Probe() {
    }

    /**
     * Runs {@code body}; its return value becomes the detail of a successful result. Any exception
     * or linkage error (such as a native library that cannot be loaded) becomes a failed result,
     * never a crash, so one broken capability does not hide the others.
     */
    public static Result run(String name, Callable<String> body) {
        long start = System.nanoTime();
        try {
            String detail = body.call();
            return new Result(name, true, detail, elapsed(start));
        } catch (Failure f) {
            return new Result(name, false, f.getMessage(), elapsed(start));
        } catch (Exception | LinkageError e) {
            return new Result(name, false, describe(e), elapsed(start));
        }
    }

    /** Exception class and message, followed by the root cause if different. */
    public static String describe(Throwable t) {
        StringBuilder sb = new StringBuilder(t.getClass().getName());
        if (t.getMessage() != null) {
            sb.append(": ").append(t.getMessage());
        }
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        if (root != t) {
            sb.append(" | root cause ").append(root.getClass().getName());
            if (root.getMessage() != null) {
                sb.append(": ").append(root.getMessage());
            }
        }
        return sb.toString();
    }

    private static long elapsed(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}
