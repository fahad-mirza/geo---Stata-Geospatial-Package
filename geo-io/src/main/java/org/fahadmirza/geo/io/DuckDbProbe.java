package org.fahadmirza.geo.io;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Locale;
import java.util.Properties;

import org.fahadmirza.geo.core.Probe;

/**
 * Phase 0 task 3: proves that DuckDB's native engine loads from inside geo.jar and answers SQL.
 *
 * <p>How DuckDB loads (duckdb-java source, DuckDBNative.java, tag v1.5.6.0): the jar holds one native
 * library per platform, named libduckdb_java.so_{os}_{arch}; on first use the driver copies the one
 * for this machine into Java's temporary folder (java.io.tmpdir) and loads it from there. On a
 * locked-down machine that copy-and-load step is the part most likely to be blocked.
 *
 * <p>The driver is created directly ({@code new org.duckdb.DuckDBDriver()}) rather than through
 * java.sql.DriverManager, which looks drivers up through classloaders that may not see geo.jar
 * when Stata calls it.
 */
public final class DuckDbProbe {

    private DuckDbProbe() {
    }

    /** Name of the native library DuckDB will look for on this machine (mirrors DuckDBNative). */
    public static String nativeLibraryName() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).trim();
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT).trim();
        String osPart;
        if (os.startsWith("windows")) {
            osPart = "windows";
        } else if (os.startsWith("mac")) {
            return "libduckdb_java.so_osx_universal";
        } else if (os.startsWith("linux")) {
            osPart = "linux";
        } else {
            osPart = os.replaceAll("[^a-z0-9_\\-.]", "");
        }
        String archPart;
        switch (arch) {
            case "x86_64":
            case "amd64":
                archPart = "amd64";
                break;
            case "aarch64":
            case "arm64":
                archPart = "arm64";
                break;
            default:
                archPart = arch.replaceAll("[^a-z0-9_\\-.]", "");
        }
        return "libduckdb_java.so_" + osPart + "_" + archPart;
    }

    /** Is a DuckDB native library for this operating system and chip inside the jar? */
    public static Probe.Result platformSupported() {
        return Probe.run("DuckDB native library for this platform", () -> {
            String lib = nativeLibraryName();
            if (DuckDbProbe.class.getResource("/" + lib) == null) {
                throw new Probe.Failure("no " + lib + " inside the jar: DuckDB has no engine for "
                        + System.getProperty("os.name") + " " + System.getProperty("os.arch"));
            }
            return lib + " is bundled; it is unpacked to " + System.getProperty("java.io.tmpdir")
                    + " on first use";
        });
    }

    /** Opens an in-memory DuckDB database and runs SELECT 42. */
    public static Probe.Result select42() {
        return Probe.run("DuckDB SELECT 42", () -> {
            Driver driver = new org.duckdb.DuckDBDriver();
            try (Connection c = driver.connect("jdbc:duckdb:", new Properties());
                 Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery("SELECT 42 AS answer, version() AS duckdb_version")) {
                if (!rs.next()) {
                    throw new Probe.Failure("query returned no row");
                }
                int answer = rs.getInt(1);
                if (answer != 42) {
                    throw new Probe.Failure("query returned " + answer + " instead of 42");
                }
                return "returned 42; DuckDB engine " + rs.getString(2);
            }
        });
    }
}
