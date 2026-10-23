package org.fahadmirza.geo.io;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fahadmirza.geo.core.Probe;
import org.junit.jupiter.api.Test;

/**
 * Runs the DuckDB probes on every CI runner. All six CI platforms are expected to have a bundled
 * native library (Windows ARM64 through DuckDB's windows_arm64 classifier jar), so a missing
 * library fails the test rather than skipping it.
 */
class DuckDbProbeTest {

    private static void check(Probe.Result r) {
        System.out.println("[geo probe] " + r.name() + ": " + (r.ok() ? "OK" : "FAILED") + " in "
                + r.millis() + " ms; " + r.detail());
        assertTrue(r.ok(), r.name() + " failed: " + r.detail());
    }

    @Test
    void nativeLibraryIsBundledForThisPlatform() {
        check(DuckDbProbe.platformSupported());
    }

    @Test
    void duckDbAnswers42() {
        check(DuckDbProbe.select42());
    }
}
