package org.fahadmirza.geo.io;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.fahadmirza.geo.core.Probe;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Runs the GeoTools probes on every CI runner (Windows, macOS, Linux; x64 and ARM64). */
class GeoToolsProbeTest {

    @TempDir
    Path tmp;

    private static void check(Probe.Result r) {
        System.out.println("[geo probe] " + r.name() + ": " + (r.ok() ? "OK" : "FAILED") + " in "
                + r.millis() + " ms; " + r.detail());
        assertTrue(r.ok(), r.name() + " failed: " + r.detail());
    }

    @Test
    void geoTiffFormatIsRegistered() {
        check(GeoToolsProbe.formats());
    }

    @Test
    void epsgDatabaseAnswers() {
        check(GeoToolsProbe.epsg());
    }

    @Test
    void geoTiffRoundTripIsExact() {
        check(GeoToolsProbe.roundTrip(tmp.resolve("folder with spaces")));
    }
}
