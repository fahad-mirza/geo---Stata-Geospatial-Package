package org.fahadmirza.geo.core;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Version and build identifier of the geo engine, read from geo-build.properties, which Maven
 * fills in at build time. The build identifier is the Git commit id for jars built by GitHub
 * Actions, so "geo version" in Stata shows exactly which commit produced the loaded geo.jar.
 */
public final class GeoVersion {
    private static final String RESOURCE = "geo-build.properties";
    private static final Properties PROPS = load();

    private GeoVersion() {
    }

    /** Package version, for example "0.0.1-SNAPSHOT". */
    public static String version() {
        return PROPS.getProperty("version", "unknown");
    }

    /** Build identifier: the Git commit id for CI builds, "local" otherwise. */
    public static String build() {
        return PROPS.getProperty("build", "unknown");
    }

    /** One-line description used by "geo version" and in test logs. */
    public static String describe() {
        return "geo " + version() + " (build " + build() + ")";
    }

    private static Properties load() {
        Properties p = new Properties();
        try (InputStream in = GeoVersion.class.getResourceAsStream(RESOURCE)) {
            if (in != null) {
                p.load(in);
            }
        } catch (IOException e) {
            // Leave the properties empty; version() and build() then report "unknown".
        }
        return p;
    }
}
