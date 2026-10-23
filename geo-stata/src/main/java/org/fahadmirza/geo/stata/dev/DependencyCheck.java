package org.fahadmirza.geo.stata.dev;

import com.stata.sfi.SFIToolkit;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.fahadmirza.geo.core.Probe;
import org.fahadmirza.geo.io.DuckDbProbe;
import org.fahadmirza.geo.io.GeoToolsProbe;
import org.fahadmirza.geo.stata.JavaContext;
import org.fahadmirza.geo.stata.ReturnCodes;
import org.fahadmirza.geo.stata.dev.CheckRow.Verdict;

/**
 * Phase 0 task 3 (and the packaging part of task 5): proves, inside Stata, that the shaded geo.jar
 * carries working copies of GeoTools and DuckDB. Called by tests/stata/check_dependencies.do:
 *
 * <pre>javacall org.fahadmirza.geo.stata.dev.DependencyCheck run, jars(geo.jar) args("report.tsv" "work folder")</pre>
 *
 * <p>Writes the same tab-separated report format as StubCheck (section, kind, check, verdict,
 * detail), which the do-file prints one verdict per line.
 */
public final class DependencyCheck {
    private static final String SHADED = "org/fahadmirza/geo/shaded/";
    /**
     * "org/apache/commons/", built at run time ON PURPOSE. The Shade plugin rewrites every string
     * constant in geo.jar that looks like a relocated package, including this class's own text, so
     * a plain literal "org/apache/commons/" would ship as the relocated path and the check below
     * would count the renamed copies (bug found 23 Oct 2026: 1,083 false "unrelocated" entries).
     * String.join is not a compile-time constant, so the build tool cannot see or rewrite it.
     */
    private static final String COMMONS = String.join("/", "org", "apache", "commons") + "/";
    private static final String GT_SERVICE = "META-INF/services/org.geotools.coverage.grid.io.GridFormatFactorySpi";
    private static final String[] IMAGEN_REGISTRIES = {
        "META-INF/registryFile.imagen", "META-INF/org.eclipse.imagen.registryFile.imagen",
    };

    private DependencyCheck() {
    }

    /** javacall entry point. args[0] = report file to write, args[1] = folder for the test GeoTIFF. */
    public static int run(String[] args) {
        if (args == null || args.length < 2) {
            SFIToolkit.errorln("DependencyCheck needs two arguments: the report file and a work folder");
            return ReturnCodes.SYNTAX;
        }
        try {
            String contextLoader = describeLoader(Thread.currentThread().getContextClassLoader());
            List<CheckRow> rows = JavaContext.call(() -> execute(Paths.get(args[1]), contextLoader));
            StubCheck.writeReport(rows, new File(args[0]));
            Map<Verdict, Integer> c = StubCheck.count(rows);
            SFIToolkit.displayln("{txt}DependencyCheck ran " + rows.size() + " items: "
                    + c.get(Verdict.PASS) + " pass, " + c.get(Verdict.FAIL) + " fail, "
                    + c.get(Verdict.WARN) + " warn, " + c.get(Verdict.INFO) + " info");
            return 0;
        } catch (Exception e) {
            SFIToolkit.errorln("DependencyCheck failed: " + SFIToolkit.stackTraceToString(e));
            return ReturnCodes.JAVA_EXCEPTION;
        }
    }

    /** All checks; no Stata dependency. {@code contextLoaderAtEntry} is recorded for information. */
    static List<CheckRow> execute(Path workDir, String contextLoaderAtEntry) throws IOException {
        List<CheckRow> rows = new ArrayList<>();
        File jarFile = ownJar();

        // ----- Environment (information only) -----
        rows.add(info("Environment", "Java runtime", System.getProperty("java.version") + " ("
                + System.getProperty("java.vendor") + "), " + System.getProperty("os.name") + " "
                + System.getProperty("os.arch")));
        rows.add(info("Environment", "geo.jar loaded from", jarFile == null ? "(not a jar: classes folder)"
                : jarFile.getAbsolutePath() + " (" + (jarFile.length() / (1024 * 1024)) + " MB)"));
        rows.add(info("Environment", "context classloader when Stata called geo", contextLoaderAtEntry
                + "; geo switches it to its own loader for the duration of each call"));
        rows.add(info("Environment", "Java temporary folder (DuckDB unpacks its engine here)",
                System.getProperty("java.io.tmpdir")));
        rows.add(info("Environment", "work folder for the test GeoTIFF", workDir.toAbsolutePath().toString()));

        // ----- Packaging: what the shaded jar contains and where classes load from -----
        if (jarFile == null) {
            rows.add(new CheckRow("Packaging", "CHECK", "inspect geo.jar", Verdict.FAIL,
                    "geo was not loaded from a jar, so its packaging cannot be inspected"));
        } else {
            try (JarFile jar = new JarFile(jarFile)) {
                rows.addAll(packagingChecks(jar, jarFile));
            }
        }

        // ----- GeoTools -----
        rows.add(fromProbe("GeoTools", GeoToolsProbe.formats()));
        rows.add(fromProbe("GeoTools", GeoToolsProbe.epsg()));
        rows.add(fromProbe("GeoTools", GeoToolsProbe.roundTrip(workDir)));

        // ----- DuckDB -----
        rows.add(fromProbe("DuckDB", DuckDbProbe.platformSupported()));
        rows.add(fromProbe("DuckDB", DuckDbProbe.select42()));
        return rows;
    }

    private static List<CheckRow> packagingChecks(JarFile jar, File jarFile) {
        List<CheckRow> rows = new ArrayList<>();
        String shadedCommons = null;
        List<String> shadedClasses = new ArrayList<>();
        int unrelocatedCommons = 0;
        Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            String n = entries.nextElement().getName();
            if (n.startsWith(SHADED + COMMONS) && n.endsWith(".class") && !n.contains("$")) {
                if (shadedCommons == null) {
                    shadedCommons = n;
                }
                shadedClasses.add(n);
            }
            if (n.startsWith(COMMONS)) {
                unrelocatedCommons++;
            }
        }

        // 1. Relocated Apache Commons loads from geo.jar (round 2 conflict test, now with the real build)
        if (shadedCommons == null) {
            rows.add(new CheckRow("Packaging", "CHECK", "relocated Apache Commons loads from geo.jar",
                    Verdict.WARN, "no relocated Apache Commons classes in geo.jar, so nothing to test"));
        } else {
            rows.add(loadsFrom("Packaging", "relocated Apache Commons loads from geo.jar",
                    toClassName(shadedCommons), jarFile));
        }

        // 2. Side by side: a class Stata ships under its original name AND geo ships renamed.
        //    Both must load, each from its own jar (Phase 0 task 5, the round 2 conflict test).
        rows.add(sideBySide(shadedClasses, jarFile));

        // 3. No unrelocated Apache Commons inside geo.jar
        rows.add(new CheckRow("Packaging", "CHECK", "no unrelocated Apache Commons inside geo.jar",
                unrelocatedCommons == 0 ? Verdict.PASS : Verdict.FAIL,
                unrelocatedCommons == 0 ? "all Apache Commons classes are under " + SHADED.replace('/', '.')
                        : unrelocatedCommons + " entries under org/apache/commons/ were not relocated; Stata's copy would win"));

        // 4. JNI library kept its original name
        rows.add(loadsFrom("Packaging", "DuckDB driver keeps its original name (org.duckdb)",
                "org.duckdb.DuckDBDriver", jarFile));

        // 5. GeoTools service file merged, not overwritten
        String services = readEntry(jar, GT_SERVICE);
        if (services == null) {
            rows.add(new CheckRow("Packaging", "CHECK", "GeoTools raster-format service file", Verdict.FAIL,
                    GT_SERVICE + " is missing from geo.jar"));
        } else {
            long plugins = services.lines().map(String::trim).filter(l -> !l.isEmpty() && !l.startsWith("#")).count();
            boolean hasTiff = services.contains("GeoTiffFormatFactorySpi");
            rows.add(new CheckRow("Packaging", "CHECK", "GeoTools raster-format service file",
                    hasTiff ? Verdict.PASS : Verdict.FAIL,
                    plugins + " plugin line(s)" + (hasTiff ? ", including the GeoTIFF format" : "; GeoTIFF is missing")));
        }

        // 6. ImageN registry files present (merged by the AppendingTransformer)
        for (String reg : IMAGEN_REGISTRIES) {
            String text = readEntry(jar, reg);
            long lines = text == null ? 0
                    : text.lines().map(String::trim).filter(l -> !l.isEmpty() && !l.startsWith("#")).count();
            rows.add(new CheckRow("Packaging", "CHECK", "ImageN registry " + reg,
                    text == null ? Verdict.WARN : Verdict.PASS,
                    text == null ? "not present in geo.jar (no bundled library provides it)"
                            : lines + " registry line(s) present"));
        }
        return rows;
    }

    // ----- helpers -----

    /**
     * Looks for an Apache Commons class that Stata provides under its original name and geo ships
     * renamed, and loads both. PASS when both load, each from its own jar.
     */
    private static CheckRow sideBySide(List<String> shadedEntries, File jarFile) {
        String what = "Stata's copy and geo's renamed copy load side by side";
        int prefix = SHADED.length();
        for (String entry : shadedEntries) {
            String original = toClassName(entry.substring(prefix));
            Class<?> stataCopy;
            try {
                stataCopy = Class.forName(original, false, JavaContext.geoLoader());
            } catch (ClassNotFoundException | LinkageError e) {
                continue; // Stata does not ship this class; try the next one
            }
            String stataLoc = location(stataCopy);
            if (stataLoc.contains(jarFile.getName())) {
                return new CheckRow("Packaging", "CHECK", what, Verdict.FAIL, original
                        + " loads from geo.jar under its ORIGINAL name, so geo's copy is not renamed");
            }
            CheckRow ours = loadsFrom("Packaging", what, toClassName(entry), jarFile);
            if (ours.verdict() != Verdict.PASS) {
                return ours;
            }
            // Kept short: Stata's display wraps lines longer than 255 characters.
            return new CheckRow("Packaging", "CHECK", what, Verdict.PASS, original + ": Stata's copy from "
                    + jarName(stataLoc) + ", geo's renamed copy from " + jarFile.getName());
        }
        return new CheckRow("Packaging", "INFO", what, Verdict.INFO, "none of the "
                + shadedEntries.size() + " Apache Commons classes in geo.jar also exists in Stata's own jars,"
                + " so there is nothing that could collide");
    }

    private static CheckRow fromProbe(String section, Probe.Result r) {
        return new CheckRow(section, "CHECK", r.name(), r.ok() ? Verdict.PASS : Verdict.FAIL,
                r.detail() + " [" + r.millis() + " ms]");
    }

    private static CheckRow info(String section, String what, String detail) {
        return new CheckRow(section, "INFO", what, Verdict.INFO, detail);
    }

    private static CheckRow loadsFrom(String section, String what, String className, File jarFile) {
        try {
            Class<?> c = Class.forName(className, false, JavaContext.geoLoader());
            String loc = location(c);
            boolean fromGeo = loc.contains(jarFile.getName()) && !loc.contains("sfi-api");
            return new CheckRow(section, "CHECK", what, fromGeo ? Verdict.PASS : Verdict.FAIL,
                    className + " loads from " + loc);
        } catch (ClassNotFoundException | LinkageError e) {
            return new CheckRow(section, "CHECK", what, Verdict.FAIL, className + " could not be loaded: " + e);
        }
    }

    private static String readEntry(JarFile jar, String name) {
        JarEntry e = jar.getJarEntry(name);
        if (e == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(jar.getInputStream(e), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } catch (IOException ex) {
            return null;
        }
        return sb.toString();
    }

    private static String toClassName(String entry) {
        return entry.substring(0, entry.length() - ".class".length()).replace('/', '.');
    }

    private static File ownJar() {
        try {
            URL loc = DependencyCheck.class.getProtectionDomain().getCodeSource().getLocation();
            File f = new File(loc.toURI());
            return f.isFile() ? f : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String location(Class<?> c) {
        var cs = c.getProtectionDomain().getCodeSource();
        return cs == null || cs.getLocation() == null ? "(Java runtime)" : cs.getLocation().toString();
    }

    /** "file:/C:/Program%20Files/.../libdeps-core.jar" becomes "libdeps-core.jar". */
    private static String jarName(String location) {
        int slash = location.lastIndexOf('/');
        return slash >= 0 && slash < location.length() - 1 ? location.substring(slash + 1) : location;
    }

    private static String describeLoader(ClassLoader l) {
        if (l == null) {
            return "none (bootstrap)";
        }
        String s = l.getClass().getName();
        if (l == JavaContext.geoLoader()) {
            s += " (already geo's own loader)";
        }
        return s;
    }
}
