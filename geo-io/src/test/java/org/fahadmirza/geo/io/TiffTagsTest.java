package org.fahadmirza.geo.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.List;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Writes small TIFFs with Java's built-in TIFF writer and checks TiffTags reads them correctly. */
class TiffTagsTest {

    @TempDir
    Path tmp;

    private File write(String name, String compression, boolean tiled) throws Exception {
        BufferedImage img = new BufferedImage(300, 200, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < 200; y++) {
            for (int x = 0; x < 300; x++) {
                img.getRaster().setSample(x, y, 0, (x + y) % 256);
            }
        }
        File f = tmp.resolve(name).toFile();
        ImageWriter w = ImageIO.getImageWritersByFormatName("tiff").next();
        ImageWriteParam p = w.getDefaultWriteParam();
        p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        p.setCompressionType(compression);
        if (tiled) {
            p.setTilingMode(ImageWriteParam.MODE_EXPLICIT);
            p.setTiling(128, 128, 0, 0);
        }
        try (ImageOutputStream out = ImageIO.createImageOutputStream(f)) {
            w.setOutput(out);
            w.write(null, new IIOImage(img, null, null), p);
        } finally {
            w.dispose();
        }
        return f;
    }

    @Test
    void readsTiledDeflateFile() throws Exception {
        TiffTags t = TiffTags.read(write("tiled.tif", "Deflate", true).toPath());
        assertEquals(300, t.width());
        assertEquals(200, t.height());
        assertTrue(t.tiled(), "should be tiled");
        assertEquals(128, t.tileWidth());
        assertEquals(128, t.tileHeight());
        assertTrue(t.compression() == 8 || t.compression() == 32946, "Deflate code, got " + t.compression());
        assertEquals(8, t.bitsPerSample());
        assertEquals(1, t.imageCount());
        // 300 x 200 in 128 x 128 tiles = 3 x 2 blocks
        List<java.awt.Rectangle> blocks = RasterFileProbe.blockLayout(t);
        assertEquals(6, blocks.size());
        assertEquals(300 - 256, blocks.get(2).width);
    }

    @Test
    void readsStripLzwFile() throws Exception {
        TiffTags t = TiffTags.read(write("strips.tif", "LZW", false).toPath());
        assertFalse(t.tiled(), "should be strips");
        assertEquals(5, t.compression());
        assertTrue(t.rowsPerStrip() >= 1 && t.rowsPerStrip() <= 200);
        int rows = RasterFileProbe.blockLayout(t).stream().mapToInt(r -> r.height).sum();
        assertEquals(200, rows, "strip bands must cover every row exactly once");
        assertTrue(t.describe().contains("LZW"));
    }

    @Test
    void manifestLinesWithQuotedCommasAreParsed() {
        assertEquals(List.of("a.tif", "Deflate + predictor, strips", "42"),
                RasterFileProbe.splitCsv("a.tif,\"Deflate + predictor, strips\",42"));
    }
}
