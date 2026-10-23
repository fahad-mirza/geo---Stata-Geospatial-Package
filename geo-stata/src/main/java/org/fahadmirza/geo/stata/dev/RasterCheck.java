package org.fahadmirza.geo.stata.dev;

import com.stata.sfi.SFIToolkit;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.fahadmirza.geo.io.RasterFileProbe;
import org.fahadmirza.geo.io.RasterFileProbe.Outcome;
import org.fahadmirza.geo.io.RasterFileProbe.Report;
import org.fahadmirza.geo.stata.JavaContext;
import org.fahadmirza.geo.stata.ReturnCodes;
import org.fahadmirza.geo.stata.dev.CheckRow.Verdict;

/**
 * Phase 0 task 6 inside Stata: decodes every GeoTIFF in a folder through geo.jar, with 1 thread
 * and with all threads. Called by tests/stata/check_rasters.do:
 *
 * <pre>javacall org.fahadmirza.geo.stata.dev.RasterCheck run, jars(geo.jar) args("folder" "report.tsv" "threads")</pre>
 *
 * <p>Writes the usual tab-separated report. Per file: INFO structure, CHECK decoding, CHECK
 * GeoTools, and a final VERDICT row that the do-file prints as the one-line verdict for the file.
 */
public final class RasterCheck {

    private RasterCheck() {
    }

    /** args[0] = folder of .tif files, args[1] = report file, args[2] = threads (optional, 0 = all). */
    public static int run(String[] args) {
        if (args == null || args.length < 2) {
            SFIToolkit.errorln("RasterCheck needs a folder of .tif files and a report file");
            return ReturnCodes.SYNTAX;
        }
        try {
            Path dir = Paths.get(args[0]);
            if (!Files.isDirectory(dir)) {
                SFIToolkit.errorln("RasterCheck: folder not found: " + dir);
                return ReturnCodes.SYNTAX;
            }
            int threads = args.length > 2 && !args[2].isBlank() ? Integer.parseInt(args[2].trim()) : 0;
            List<Report> reports = JavaContext.call(
                    () -> RasterFileProbe.checkFolder(dir, threads, RasterFileProbe.DEFAULT_MAX_CELLS));
            List<CheckRow> rows = toRows(reports);
            StubCheck.writeReport(rows, new File(args[1]));
            SFIToolkit.displayln("{txt}RasterCheck read " + reports.size() + " GeoTIFF file(s) from " + dir);
            return 0;
        } catch (Exception e) {
            SFIToolkit.errorln("RasterCheck failed: " + SFIToolkit.stackTraceToString(e));
            return ReturnCodes.JAVA_EXCEPTION;
        }
    }

    static List<CheckRow> toRows(List<Report> reports) {
        List<CheckRow> rows = new ArrayList<>();
        for (Report r : reports) {
            String f = r.file();
            rows.add(new CheckRow(f, "INFO", "structure", Verdict.INFO,
                    r.structure() + "; " + String.format(Locale.ROOT, "%.1f MB", r.bytes() / 1048576.0)));
            Verdict dv = verdictOf(r.outcome());
            String speed = r.outcome() == Outcome.OK
                    ? String.format(Locale.ROOT, " | %.0f M cells/s with 1 thread, %.0f with %d threads (%.1fx)",
                            r.mcellsOneThread(), r.mcellsAllThreads(), r.threads(), r.speedup())
                    : "";
            rows.add(new CheckRow(f, "CHECK", "decode every block", dv, r.decodeDetail() + speed));
            rows.add(new CheckRow(f, "CHECK", "GeoTools GeoTIFF reader", r.geotoolsOk() ? Verdict.PASS : Verdict.WARN,
                    r.geotoolsDetail()));

            Verdict overall = dv;
            if (overall == Verdict.PASS && !r.geotoolsOk()) {
                overall = Verdict.WARN;
            }
            String summary;
            switch (r.outcome()) {
                case OK:
                    summary = String.format(Locale.ROOT, "reads correctly; %.0f M cells/s with %d threads",
                            r.mcellsAllThreads(), r.threads());
                    break;
                case UNSUPPORTED:
                    summary = "not readable yet: " + r.decodeDetail();
                    break;
                case WRONG:
                    summary = "WRONG VALUES: " + r.decodeDetail();
                    break;
                default:
                    summary = "error: " + r.decodeDetail();
            }
            rows.add(new CheckRow(f, "VERDICT", "verdict", overall, summary));
        }
        return rows;
    }

    private static Verdict verdictOf(Outcome o) {
        switch (o) {
            case OK: return Verdict.PASS;
            case UNSUPPORTED: return Verdict.WARN;
            default: return Verdict.FAIL;
        }
    }
}
