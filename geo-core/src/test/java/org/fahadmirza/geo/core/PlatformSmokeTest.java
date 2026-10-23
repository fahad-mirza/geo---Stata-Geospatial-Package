package org.fahadmirza.geo.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Phase 0 task 2: the trivial test that proves the build and test chain works on every runner.
 * It also prints the platform so each CI log shows which system the tests ran on.
 */
class PlatformSmokeTest {

    @Test
    void reportsPlatformAndRunsOnJava17OrLater() {
        String line = GeoVersion.describe()
                + " | Java " + System.getProperty("java.version")
                + " (" + System.getProperty("java.vendor") + ")"
                + " | " + System.getProperty("os.name") + " " + System.getProperty("os.arch")
                + " | cores " + Runtime.getRuntime().availableProcessors();
        System.out.println("[geo platform] " + line);
        assertTrue(Runtime.version().feature() >= 17, "geo needs Java 17 or later");
    }

    @Test
    void buildPropertiesWereFilledByMaven() {
        // If resource filtering failed, the raw placeholder "${project.version}" would remain.
        assertFalse(GeoVersion.version().contains("${"), "version placeholder not filled");
        assertFalse(GeoVersion.build().contains("${"), "build placeholder not filled");
        assertFalse(GeoVersion.version().equals("unknown"), "geo-build.properties not found");
    }

    @Test
    void sequentialSumIsIdenticalAcrossRepeats() {
        // Determinism baseline: the same values summed in the same order give the same bits.
        double[] values = new double[100_000];
        for (int i = 0; i < values.length; i++) {
            values[i] = Math.sin(i) * 1e6 + 1.0 / (i + 1);
        }
        double first = 0;
        for (double v : values) {
            first += v;
        }
        for (int run = 0; run < 5; run++) {
            double again = 0;
            for (double v : values) {
                again += v;
            }
            assertEquals(Double.doubleToRawLongBits(first), Double.doubleToRawLongBits(again));
        }
    }
}
