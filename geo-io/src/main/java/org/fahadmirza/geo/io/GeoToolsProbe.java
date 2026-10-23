package org.fahadmirza.geo.io;

import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.fahadmirza.geo.core.Probe;
import org.geotools.api.coverage.grid.Format;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.GridCoverageFactory;
import org.geotools.coverage.grid.io.GridFormatFinder;
import org.geotools.gce.geotiff.GeoTiffReader;
import org.geotools.gce.geotiff.GeoTiffWriter;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.geotools.referencing.crs.DefaultGeographicCRS;

/**
 * Phase 0 task 3: proves that GeoTools works from inside geo.jar. No Stata dependency, so the same
 * probes run as JUnit tests on every CI runner and through javacall inside Stata.
 *
 * <p>API calls checked against the GeoTools 35.2 source (GridFormatFinder, GridCoverageFactory,
 * GeoTiffWriter, GeoTiffReader, CRS, ReferencedEnvelope).
 */
public final class GeoToolsProbe {
    /** Side length of the test raster written by {@link #roundTrip(Path)}. */
    static final int SIZE = 64;

    private GeoToolsProbe() {
    }

    /**
     * GeoTools finds its raster format plugins through META-INF/services files. If shading had
     * overwritten those files instead of merging them, GeoTIFF would be missing from this list.
     */
    public static Probe.Result formats() {
        return Probe.run("GeoTools raster formats", () -> {
            List<String> names = Arrays.stream(GridFormatFinder.getFormatArray())
                    .map(Format::getName).sorted().collect(Collectors.toList());
            if (!names.contains("GeoTIFF")) {
                throw new Probe.Failure("GeoTIFF format not registered; found " + names
                        + " (META-INF/services files may not have been merged)");
            }
            return names.size() + " format(s) registered, including GeoTIFF: " + names;
        });
    }

    /** The EPSG database (gt-epsg-hsql) answers EPSG:4326, the code for latitude-longitude WGS 84. */
    public static Probe.Result epsg() {
        return Probe.run("EPSG database (EPSG:4326)", () -> {
            CoordinateReferenceSystem crs = CRS.decode("EPSG:4326");
            return "EPSG:4326 decodes to \"" + crs.getName() + "\"";
        });
    }

    /**
     * Writes a 64 x 64 floating-point GeoTIFF into {@code dir}, reads it back through GeoTools'
     * GeoTIFF reader (ImageIO-Ext underneath, ImageN operations on top) and compares all 4,096
     * values exactly, then deletes the file. This exercises the ImageN registry files that shading
     * must keep intact, and proves every file handle is released (Windows cannot delete open files).
     */
    public static Probe.Result roundTrip(Path dir) {
        return Probe.run("GeoTIFF write and read back", () -> {
            Files.createDirectories(dir);
            File file = Files.createTempFile(dir, "geo-probe-", ".tif").toFile();
            float[][] values = new float[SIZE][SIZE];
            for (int row = 0; row < SIZE; row++) {
                for (int col = 0; col < SIZE; col++) {
                    values[row][col] = (row * SIZE + col) * 0.5f + 0.25f;
                }
            }
            // Longitude 10..74 east, latitude -32..32: one-degree cells, WGS 84.
            ReferencedEnvelope env = new ReferencedEnvelope(10, 10 + SIZE, -SIZE / 2.0, SIZE / 2.0,
                    DefaultGeographicCRS.WGS84);
            GridCoverage2D written = new GridCoverageFactory().create("probe", values, env);

            GeoTiffWriter writer = new GeoTiffWriter(file);
            try {
                writer.write(written);
            } finally {
                writer.dispose();
            }

            GeoTiffReader reader = new GeoTiffReader(file);
            GridCoverage2D read = null;
            int mismatches = 0;
            String firstMismatch = "";
            String epsgText;
            try {
                read = reader.read();
                RenderedImage img = read.getRenderedImage();
                if (img.getWidth() != SIZE || img.getHeight() != SIZE) {
                    throw new Probe.Failure("read back " + img.getWidth() + " x " + img.getHeight()
                            + " pixels, expected " + SIZE + " x " + SIZE);
                }
                Raster data = img.getData();
                for (int row = 0; row < SIZE; row++) {
                    for (int col = 0; col < SIZE; col++) {
                        float got = data.getSampleFloat(img.getMinX() + col, img.getMinY() + row, 0);
                        if (Float.floatToIntBits(got) != Float.floatToIntBits(values[row][col])) {
                            if (mismatches == 0) {
                                firstMismatch = " first at row " + row + ", column " + col
                                        + ": wrote " + values[row][col] + ", read " + got;
                            }
                            mismatches++;
                        }
                    }
                }
                Integer epsg = CRS.lookupEpsgCode(read.getCoordinateReferenceSystem(), false);
                epsgText = epsg == null ? "coordinate system not matched to an EPSG code"
                        : "coordinate system EPSG:" + epsg;
            } finally {
                // Release the image's own file handle first (see Coverages), then the reader's.
                Coverages.release(read);
                reader.dispose();
            }
            if (mismatches > 0) {
                throw new Probe.Failure(mismatches + " of " + (SIZE * SIZE) + " values differ;" + firstMismatch);
            }

            // The file must now be closed. Windows refuses to delete an open file, so deleting it
            // proves geo released every handle (on Linux and macOS this step always succeeds).
            long bytes = file.length();
            try {
                Files.delete(file.toPath());
            } catch (java.io.IOException e) {
                throw new Probe.Failure("all values identical, but the file is still open after the"
                        + " image and reader were released, so it cannot be deleted or overwritten: " + e);
            }
            return "wrote and read " + SIZE + " x " + SIZE + " float values (" + bytes
                    + " bytes), all identical; " + epsgText;
        });
    }
}
