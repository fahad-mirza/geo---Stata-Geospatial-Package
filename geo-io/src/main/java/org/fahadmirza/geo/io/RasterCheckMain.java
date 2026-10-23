package org.fahadmirza.geo.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Locale;

/**
 * Command-line entry for GitHub Actions (test-rasters workflow):
 * {@code java -cp geo.jar org.fahadmirza.geo.io.RasterCheckMain <folder> [threads]}.
 * Prints one line per file and writes a table to the run's summary page. Exits with 1 only if a
 * file decodes to wrong numbers or fails for an unexpected reason; an unsupported compression is a
 * finding, reported but not a build failure.
 */
public final class RasterCheckMain {

    private RasterCheckMain() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("usage: RasterCheckMain <folder> [threads]");
            System.exit(2);
        }
        int threads = args.length > 1 ? Integer.parseInt(args[1]) : 0;
        List<RasterFileProbe.Report> reports = RasterFileProbe.checkFolder(Paths.get(args[0]), threads,
                RasterFileProbe.DEFAULT_MAX_CELLS);
        StringBuilder md = new StringBuilder();
        md.append("### geo: GeoTIFF decoding (").append(System.getProperty("os.name")).append(' ')
                .append(System.getProperty("os.arch")).append(", Java ").append(System.getProperty("java.version"))
                .append(")\n\n| File | Structure | Result | 1 thread (M cells/s) | All threads (M cells/s) | GeoTools |\n")
                .append("| --- | --- | --- | --- | --- | --- |\n");
        boolean bad = false;
        for (RasterFileProbe.Report r : reports) {
            System.out.printf(Locale.ROOT, "%-34s %-11s %s | %s | 1 thread %.0f, %d threads %.0f M cells/s | %s%n",
                    r.file(), r.outcome(), r.structure(), r.decodeDetail(), r.mcellsOneThread(), r.threads(),
                    r.mcellsAllThreads(), r.geotoolsDetail());
            md.append(String.format(Locale.ROOT, "| %s | %s | %s: %s | %.0f | %.0f (%d threads) | %s |%n",
                    r.file(), r.structure(), r.outcome(), r.decodeDetail(), r.mcellsOneThread(),
                    r.mcellsAllThreads(), r.threads(), r.geotoolsDetail()));
            bad |= r.outcome() == RasterFileProbe.Outcome.WRONG || r.outcome() == RasterFileProbe.Outcome.ERROR;
        }
        if (reports.isEmpty()) {
            System.out.println("No .tif files found in " + args[0]);
            bad = true;
        }
        String summary = System.getenv("GITHUB_STEP_SUMMARY");
        if (summary != null && !summary.isEmpty()) {
            Files.writeString(Path.of(summary), md.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        System.exit(bad ? 1 : 0);
    }
}
