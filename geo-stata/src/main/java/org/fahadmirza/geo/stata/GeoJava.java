package org.fahadmirza.geo.stata;

import com.stata.sfi.SFIToolkit;
import org.fahadmirza.geo.core.GeoVersion;

/**
 * javacall entry points used by geo.ado. Each method has the form Stata's javacall expects:
 * {@code public static int name(String[] args)}, returning 0 on success or a Stata return code.
 *
 * <p>Output goes through SFIToolkit on the main thread only.
 */
public final class GeoJava {
    private GeoJava() {
    }

    /**
     * "geo version": prints the package version, the build (Git commit) and the Java runtime.
     * Phase 0 task 12 extends this into "geo check" (heap, cores, DuckDB platform support).
     */
    public static int version(String[] args) {
        try {
            Runtime rt = Runtime.getRuntime();
            SFIToolkit.displayln("{txt}" + GeoVersion.describe());
            SFIToolkit.displayln("{txt}Java " + System.getProperty("java.version")
                    + " (" + System.getProperty("java.vendor") + ")");
            SFIToolkit.displayln("{txt}Platform " + System.getProperty("os.name") + " "
                    + System.getProperty("os.arch") + ", " + rt.availableProcessors()
                    + " logical cores, maximum heap " + (rt.maxMemory() / (1024 * 1024)) + " MB");
            return 0;
        } catch (RuntimeException e) {
            SFIToolkit.errorln("geo version failed: " + e);
            return ReturnCodes.JAVA_EXCEPTION;
        }
    }
}
