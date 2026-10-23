package org.fahadmirza.geo.io;

import java.awt.image.RenderedImage;

import org.eclipse.imagen.PlanarImage;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.image.util.ImageUtilities;

/**
 * Releasing rasters read through GeoTools, so the file on disk is closed.
 *
 * <p>Why this exists (GeoTools 35.2 source, found by the Windows CI failure of 23 Oct 2026):
 * {@code GeoTiffReader.read()} opens a second handle on the file and gives it to the image it
 * returns, which reads pixels lazily through it. {@code GeoTiffReader.dispose()} closes only the
 * reader's own handle, and {@code GridCoverage2D.dispose(true)} only calls {@code image.dispose()}.
 * The second handle is closed only by {@code ImageUtilities.disposePlanarImageChain}, which walks
 * the image and closes the input stream held by its "ImageRead" operation. Without it the file
 * stays open: harmless on Linux and macOS, but on Windows the user can then not delete or
 * overwrite their own raster while Stata is running.
 *
 * <p>Rule: every coverage geo reads is released with {@link #release(GridCoverage2D)}, in a
 * finally block, before the reader itself is disposed.
 */
public final class Coverages {

    private Coverages() {
    }

    /** Closes the file handle held by the coverage's image, then disposes the coverage. Null-safe. */
    public static void release(GridCoverage2D coverage) {
        if (coverage == null) {
            return;
        }
        RenderedImage image = coverage.getRenderedImage();
        if (image instanceof PlanarImage planar) {
            ImageUtilities.disposePlanarImageChain(planar);
        }
        coverage.dispose(true);
    }
}
