package org.fahadmirza.geo.io;

import java.awt.Rectangle;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.FileImageInputStream;

import it.geosolutions.imageioimpl.plugins.tiff.TIFFImageReaderSpi;
import org.fahadmirza.geo.core.Probe;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.gce.geotiff.GeoTiffReader;
import org.geotools.referencing.CRS;

/**
 * Phase 0 task 6: can geo read real GeoTIFFs, in every compression they come in, and how fast?
 * No Stata dependency: the same code runs in GitHub Actions (RasterCheckMain) and inside Stata
 * (geo-stata's RasterCheck).
 *
 * <p>For each file it (1) reads the structure from the TIFF header with {@link TiffTags};
 * (2) decodes every tile (or band of strips) through GeoTools' TIFF reader, ImageIO-Ext, once with
 * one thread and once with all threads, each thread using its own reader; (3) compares an exact
 * checksum between the two passes and, for generated test files, with the value recorded by the
 * generator; (4) opens the file through GeoTools' GeoTIFF reader to confirm the coordinate system.
 *
 * <p>The checksum is the sum of the raw bit patterns of band 1 (for floats) or of the values (for
 * integers), as a 64-bit integer. Integer sums do not depend on order, so the result is exact and
 * any difference means the decoder produced different numbers.
 */
public final class RasterFileProbe {
    /** Cells decoded per pass at most; larger files are sampled with evenly spaced blocks. */
    public static final long DEFAULT_MAX_CELLS = 64L * 1024 * 1024;

    /** How a file fared. */
    public enum Outcome {
        /** Decoded, identical across thread counts (and equal to the expected checksum, if any). */
        OK,
        /** The compression is one GeoTools' TIFF reader cannot decode; a clear finding, not a bug. */
        UNSUPPORTED,
        /** Decoded, but the numbers differ between passes or from the expected checksum. */
        WRONG,
        /** Could not be read for another reason (damaged file, not a TIFF, ...). */
        ERROR
    }

    /** Everything learned about one file. */
    public record Report(
            String file, long bytes, String structure, Outcome outcome, String decodeDetail,
            double mcellsOneThread, double mcellsAllThreads, int threads,
            boolean geotoolsOk, String geotoolsDetail) {

        /** Speed-up of all threads over one thread, or NaN if not measured. */
        public double speedup() {
            return mcellsOneThread > 0 ? mcellsAllThreads / mcellsOneThread : Double.NaN;
        }
    }

    private RasterFileProbe() {
    }

    // ------------------------------------------------------------------------------------------
    // Folder level
    // ------------------------------------------------------------------------------------------

    /**
     * Checks every .tif or .tiff file in {@code dir}, in name order. If the folder holds a
     * manifest.csv written by the test-raster generator, its expected checksums are used.
     *
     * @param threads  threads for the parallel pass; 0 means all logical cores Java sees
     */
    public static List<Report> checkFolder(Path dir, int threads, long maxCells) throws IOException {
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path p : ds) {
                String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                if (Files.isRegularFile(p) && (n.endsWith(".tif") || n.endsWith(".tiff"))) {
                    files.add(p);
                }
            }
        }
        Collections.sort(files);
        Map<String, Long> expected = readManifest(dir.resolve("manifest.csv"));
        List<Report> out = new ArrayList<>();
        for (Path f : files) {
            out.add(check(f, threads, maxCells, expected.get(f.getFileName().toString())));
        }
        return out;
    }

    /** Reads "file,...,expected_checksum" lines from the generator's manifest; empty if absent. */
    static Map<String, Long> readManifest(Path manifest) throws IOException {
        Map<String, Long> m = new HashMap<>();
        if (!Files.isRegularFile(manifest)) {
            return m;
        }
        try (BufferedReader r = Files.newBufferedReader(manifest, StandardCharsets.UTF_8)) {
            String header = r.readLine();
            if (header == null) {
                return m;
            }
            List<String> cols = splitCsv(header.trim());
            int fileCol = cols.indexOf("file");
            int sumCol = cols.indexOf("expected_checksum");
            if (fileCol < 0 || sumCol < 0) {
                return m;
            }
            String line;
            while ((line = r.readLine()) != null) {
                List<String> parts = splitCsv(line.trim());
                if (parts.size() > Math.max(fileCol, sumCol) && !parts.get(sumCol).isEmpty()) {
                    try {
                        m.put(parts.get(fileCol), Long.parseLong(parts.get(sumCol).trim()));
                    } catch (NumberFormatException e) {
                        // not a checksum (damaged line): compare across thread counts only
                    }
                }
            }
        }
        return m;
    }

    /** Splits one CSV line, honouring double-quoted fields that contain commas. */
    static List<String> splitCsv(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (c == ',' && !quoted) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }

    // ------------------------------------------------------------------------------------------
    // File level
    // ------------------------------------------------------------------------------------------

    /** Checks one file. {@code expectedChecksum} may be null (real files have none). */
    public static Report check(Path file, int threads, long maxCells, Long expectedChecksum) {
        int nThreads = threads > 0 ? threads : Runtime.getRuntime().availableProcessors();
        String name = file.getFileName().toString();
        long bytes;
        try {
            bytes = Files.size(file);
        } catch (IOException e) {
            bytes = -1;
        }

        // 1. Structure from the header (works even when decoding does not)
        TiffTags tags;
        try {
            tags = TiffTags.read(file);
        } catch (IOException e) {
            return new Report(name, bytes, "header not readable: " + e.getMessage(), Outcome.ERROR,
                    "not decoded", 0, 0, nThreads, false, "not attempted");
        }

        // 2-3. Decode with one thread, then all threads; compare checksums
        Outcome outcome;
        String detail;
        double one = 0;
        double all = 0;
        try {
            List<Rectangle> blocks = sampleBlocks(blockLayout(tags), tags, maxCells);
            long totalCells = 0;
            for (Rectangle r : blocks) {
                totalCells += (long) r.width * r.height;
            }
            Pass p1 = decode(file, blocks, 1);
            Pass pN = decode(file, blocks, nThreads);
            one = totalCells / (p1.seconds * 1e6);
            all = totalCells / (pN.seconds * 1e6);
            String coverage = blocks.size() == blockLayout(tags).size()
                    ? "all " + blocks.size() + " blocks"
                    : blocks.size() + " of " + blockLayout(tags).size() + " blocks (evenly spaced sample)";
            if (p1.checksum != pN.checksum) {
                outcome = Outcome.WRONG;
                detail = "checksums differ between 1 thread (" + p1.checksum + ") and " + nThreads
                        + " threads (" + pN.checksum + ")";
            } else if (expectedChecksum != null && expectedChecksum != p1.checksum) {
                outcome = Outcome.WRONG;
                detail = "decoded values differ from what the generator wrote (checksum " + p1.checksum
                        + ", expected " + expectedChecksum + ")";
            } else {
                outcome = Outcome.OK;
                detail = "decoded " + coverage + " (" + totalCells + " cells); identical with 1 and "
                        + nThreads + " threads"
                        + (expectedChecksum != null ? "; matches the generator's checksum exactly" : "");
            }
        } catch (Exception | LinkageError e) {
            String msg = Probe.describe(e);
            if (msg.contains("Unsupported compression")) {
                outcome = Outcome.UNSUPPORTED;
                detail = "GeoTools' TIFF reader (ImageIO-Ext) cannot decode " + tags.compressionName()
                        + " (TIFF compression code " + tags.compression() + ")";
            } else {
                outcome = Outcome.ERROR;
                detail = "could not decode: " + msg;
            }
        }

        // 4. GeoTools' GeoTIFF reader: coordinate system and size
        boolean gtOk;
        String gtDetail;
        GeoTiffReader reader = null;
        GridCoverage2D coverage = null;
        try {
            reader = new GeoTiffReader(file.toFile());
            coverage = reader.read();
            Integer epsg = CRS.lookupEpsgCode(coverage.getCoordinateReferenceSystem(), false);
            gtOk = true;
            gtDetail = "opens as " + coverage.getRenderedImage().getWidth() + " x "
                    + coverage.getRenderedImage().getHeight() + " grid, "
                    + (epsg != null ? "EPSG:" + epsg : "coordinate system without an EPSG code: "
                            + coverage.getCoordinateReferenceSystem().getName());
        } catch (Exception | LinkageError e) {
            gtOk = false;
            gtDetail = "GeoTools could not open it: " + Probe.describe(e);
        } finally {
            Coverages.release(coverage);
            if (reader != null) {
                reader.dispose();
            }
        }

        return new Report(name, bytes, tags.describe(), outcome, detail, one, all, nThreads, gtOk, gtDetail);
    }

    // ------------------------------------------------------------------------------------------
    // Decoding
    // ------------------------------------------------------------------------------------------

    private record Pass(long checksum, double seconds) {
    }

    /** Tiles for tiled files; for striped files, bands of whole strips of roughly 256K cells. */
    static List<Rectangle> blockLayout(TiffTags t) {
        List<Rectangle> out = new ArrayList<>();
        if (t.tiled()) {
            for (int y = 0; y < t.height(); y += t.tileHeight()) {
                for (int x = 0; x < t.width(); x += t.tileWidth()) {
                    out.add(new Rectangle(x, y, Math.min(t.tileWidth(), t.width() - x),
                            Math.min(t.tileHeight(), t.height() - y)));
                }
            }
        } else {
            int strip = Math.max(1, Math.min(t.rowsPerStrip(), t.height()));
            int wanted = Math.max(1, 262_144 / Math.max(1, t.width()));
            int rows = Math.max(strip, (wanted + strip - 1) / strip * strip);
            for (int y = 0; y < t.height(); y += rows) {
                out.add(new Rectangle(0, y, t.width(), Math.min(rows, t.height() - y)));
            }
        }
        return out;
    }

    /** All blocks, or an evenly spaced, deterministic subset if the file exceeds {@code maxCells}. */
    static List<Rectangle> sampleBlocks(List<Rectangle> all, TiffTags t, long maxCells) {
        long total = (long) t.width() * t.height();
        if (total <= maxCells || all.isEmpty()) {
            return all;
        }
        int step = (int) Math.ceil((double) total / maxCells);
        List<Rectangle> out = new ArrayList<>();
        for (int i = 0; i < all.size(); i += step) {
            out.add(all.get(i));
        }
        return out;
    }

    /** Decodes {@code blocks} with {@code threads} threads, each with its own reader. */
    private static Pass decode(Path file, List<Rectangle> blocks, int threads) throws Exception {
        long[] sums = new long[blocks.size()];
        List<ImageReader> readers = Collections.synchronizedList(new ArrayList<>());
        List<FileImageInputStream> streams = Collections.synchronizedList(new ArrayList<>());
        ThreadLocal<ImageReader> local = ThreadLocal.withInitial(() -> {
            try {
                FileImageInputStream in = new FileImageInputStream(file.toFile());
                streams.add(in);
                ImageReader r = new TIFFImageReaderSpi().createReaderInstance(null);
                r.setInput(in, false, true);
                readers.add(r);
                return r;
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
        ExecutorService pool = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "geo-raster-probe");
            t.setDaemon(true);
            return t;
        });
        long start = System.nanoTime();
        try {
            List<Future<?>> futures = new ArrayList<>(blocks.size());
            for (int i = 0; i < blocks.size(); i++) {
                final int idx = i;
                futures.add(pool.submit(() -> {
                    sums[idx] = blockChecksum(local.get(), blocks.get(idx));
                    return null;
                }));
            }
            for (Future<?> f : futures) {
                try {
                    f.get();
                } catch (ExecutionException e) {
                    Throwable c = e.getCause();
                    if (c instanceof IllegalStateException && c.getCause() != null) {
                        c = c.getCause();
                    }
                    if (c instanceof Exception) {
                        throw (Exception) c;
                    }
                    throw e;
                }
            }
        } finally {
            pool.shutdownNow();
            for (ImageReader r : readers) {
                r.dispose();
            }
            for (FileImageInputStream s : streams) {
                try {
                    s.close();
                } catch (IOException ignored) {
                    // closing a read-only stream; nothing to recover
                }
            }
        }
        double seconds = Math.max(1e-9, (System.nanoTime() - start) / 1e9);
        long total = 0;
        for (long s : sums) {   // fixed order (exact anyway: integer sums)
            total += s;
        }
        return new Pass(total, seconds);
    }

    /** Decodes one block and returns the checksum of its band-1 values. */
    static long blockChecksum(ImageReader reader, Rectangle block) throws IOException {
        ImageReadParam param = reader.getDefaultReadParam();
        param.setSourceRegion(block);
        Raster r = reader.read(0, param).getRaster();
        int w = r.getWidth();
        int h = r.getHeight();
        int x0 = r.getMinX();
        int y0 = r.getMinY();
        long sum = 0;
        switch (r.getDataBuffer().getDataType()) {
            case DataBuffer.TYPE_FLOAT: {
                float[] v = r.getSamples(x0, y0, w, h, 0, (float[]) null);
                for (float f : v) {
                    sum += Float.floatToRawIntBits(f);
                }
                break;
            }
            case DataBuffer.TYPE_DOUBLE: {
                double[] v = r.getSamples(x0, y0, w, h, 0, (double[]) null);
                for (double d : v) {
                    sum += Double.doubleToRawLongBits(d);
                }
                break;
            }
            default: {
                int[] v = r.getSamples(x0, y0, w, h, 0, (int[]) null);
                for (int i : v) {
                    sum += i;
                }
            }
        }
        return sum;
    }
}
