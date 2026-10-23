package org.fahadmirza.geo.stata.dev;

import com.stata.sfi.SFIToolkit;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.fahadmirza.geo.stata.dev.CheckRow.Verdict;

/**
 * Phase 0 task 4: confirms that every public member of the sfi-stub classes exists, with the same
 * signature, in Stata's real com.stata.sfi API. Called by tests/stata/check_stubs.do:
 *
 * <pre>javacall org.fahadmirza.geo.stata.dev.StubCheck run, jars("geo.jar") args("sfi-stub.jar" "report.tsv")</pre>
 *
 * <p>Why the stub jar is opened in its own classloader: javacall loads jars parent-first, so any
 * class named com.stata.sfi.* always resolves to Stata's real class (round 2 showed Stata's copy
 * wins). To see the stub versions, this class opens sfi-stub.jar in a separate classloader whose
 * parent is the Java platform loader, which knows nothing about Stata. The real classes come from
 * this class's own loader, which reaches Stata's jars through its parent.
 *
 * <p>Output: a tab-separated report (class, kind, member, verdict, detail) that the do-file reads
 * and prints one verdict per line.
 */
public final class StubCheck {
    private static final String SFI_PATH = "com/stata/sfi/";

    private StubCheck() {
    }

    /** javacall entry point. args[0] = path to sfi-stub.jar, args[1] = report file to write. */
    public static int run(String[] args) {
        if (args == null || args.length < 2) {
            SFIToolkit.errorln("StubCheck needs two arguments: the path to sfi-stub.jar and the report file");
            return 198;
        }
        try {
            List<CheckRow> rows = execute(new File(args[0]), StubCheck.class.getClassLoader());
            writeReport(rows, new File(args[1]));
            Map<Verdict, Integer> counts = count(rows);
            SFIToolkit.displayln("{txt}StubCheck compared " + rows.size() + " items: "
                    + counts.get(Verdict.PASS) + " pass, " + counts.get(Verdict.FAIL) + " fail, "
                    + counts.get(Verdict.WARN) + " warn, " + counts.get(Verdict.INFO) + " info");
            return 0;
        } catch (Exception e) {
            SFIToolkit.errorln("StubCheck failed: " + SFIToolkit.stackTraceToString(e));
            return 498;
        }
    }

    /**
     * Compares every public class in the stub jar with the class of the same name visible from
     * {@code realLoader}. Has no Stata dependency, so it can also run in tests.
     */
    public static List<CheckRow> execute(File stubJar, ClassLoader realLoader) throws IOException {
        if (!stubJar.isFile()) {
            throw new IOException("sfi-stub.jar not found at " + stubJar.getAbsolutePath());
        }
        List<CheckRow> rows = new ArrayList<>();
        URL[] urls = {stubJar.toURI().toURL()};
        try (URLClassLoader stubLoader = new URLClassLoader(urls, ClassLoader.getPlatformClassLoader());
             JarFile jar = new JarFile(stubJar)) {
            for (String name : stubClassNames(jar)) {
                Class<?> stub;
                try {
                    stub = Class.forName(name, false, stubLoader);
                } catch (ClassNotFoundException | LinkageError e) {
                    rows.add(new CheckRow(name, "CLASS", name, Verdict.FAIL,
                            "could not load the stub class: " + e));
                    continue;
                }
                if (!Modifier.isPublic(stub.getModifiers())) {
                    continue; // helpers such as com.stata.sfi.Stub are not part of Stata's API
                }
                Class<?> real;
                try {
                    real = Class.forName(name, false, realLoader);
                } catch (ClassNotFoundException | LinkageError e) {
                    rows.add(new CheckRow(name, "CLASS", name, Verdict.FAIL,
                            "class does not exist in the real API (or Stata's jars are not visible)"));
                    continue;
                }
                if (real.getClassLoader() == stubLoader) {
                    rows.add(new CheckRow(name, "CLASS", name, Verdict.FAIL,
                            "the 'real' class came from sfi-stub.jar, so the comparison is meaningless"));
                    continue;
                }
                rows.addAll(SignatureComparer.compare(stub, real, realLoader));
            }
        }
        return rows;
    }

    static List<String> stubClassNames(JarFile jar) {
        List<String> names = new ArrayList<>();
        Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            String e = entries.nextElement().getName();
            if (e.startsWith(SFI_PATH) && e.endsWith(".class") && !e.contains("module-info")) {
                names.add(e.substring(0, e.length() - ".class".length()).replace('/', '.'));
            }
        }
        Collections.sort(names);
        return names;
    }

    static void writeReport(List<CheckRow> rows, File out) throws IOException {
        File parent = out.getAbsoluteFile().getParentFile();
        if (parent != null) {
            Files.createDirectories(parent.toPath());
        }
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out.toPath(), StandardCharsets.UTF_8))) {
            w.println("classname\tkind\tmember\tverdict\tdetail");
            for (CheckRow r : rows) {
                w.println(r.toTsv());
            }
        }
    }

    static Map<Verdict, Integer> count(List<CheckRow> rows) {
        Map<Verdict, Integer> m = new EnumMap<>(Verdict.class);
        for (Verdict v : Verdict.values()) {
            m.put(v, 0);
        }
        for (CheckRow r : rows) {
            m.merge(r.verdict(), 1, Integer::sum);
        }
        return m;
    }
}
