package org.fahadmirza.geo.io;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.Map;

/**
 * Reads the structure of a TIFF or GeoTIFF file straight from its header: compression, predictor,
 * tile or strip layout, data type. It uses no imaging library, so it works even for files that
 * GeoTools cannot decode, which is exactly when the information matters.
 *
 * <p>Reads the first image (IFD, "image file directory") only, and counts the further images,
 * which in GeoTIFFs are usually overviews (reduced-resolution copies). Supports classic TIFF and
 * BigTIFF, little- and big-endian. Tag numbers from the TIFF 6.0 specification; ZSTD (50000) and
 * LERC (34887) codes as registered by GDAL.
 */
public record TiffTags(
        boolean bigTiff, int width, int height, int tileWidth, int tileHeight, int rowsPerStrip,
        int compression, int predictor, int bitsPerSample, int sampleFormat, int samplesPerPixel,
        int imageCount) {

    private static final Map<Integer, String> COMPRESSION = Map.ofEntries(
            Map.entry(1, "none"), Map.entry(5, "LZW"), Map.entry(6, "JPEG (old)"), Map.entry(7, "JPEG"),
            Map.entry(8, "Deflate"), Map.entry(32946, "Deflate (old code)"), Map.entry(32773, "PackBits"),
            Map.entry(34887, "LERC"), Map.entry(34925, "LZMA"), Map.entry(50000, "ZSTD"),
            Map.entry(50001, "WebP"), Map.entry(50002, "JPEG XL"));

    /** True if the image is stored in tiles (rectangular blocks) rather than strips (bands of rows). */
    public boolean tiled() {
        return tileWidth > 0 && tileHeight > 0;
    }

    /** Plain-language compression name, for example "ZSTD", with the TIFF code if unknown. */
    public String compressionName() {
        return COMPRESSION.getOrDefault(compression, "unknown (TIFF code " + compression + ")");
    }

    /** Plain-language predictor (a pre-processing step that helps compression). */
    public String predictorName() {
        switch (predictor) {
            case 1: return "no predictor";
            case 2: return "horizontal predictor (for integers)";
            case 3: return "floating-point predictor";
            default: return "predictor code " + predictor;
        }
    }

    /** Data type, for example "32-bit float" or "16-bit signed integer". */
    public String dataTypeName() {
        String kind;
        switch (sampleFormat) {
            case 2: kind = "signed integer"; break;
            case 3: kind = "float"; break;
            default: kind = "unsigned integer";
        }
        return bitsPerSample + "-bit " + kind + (samplesPerPixel > 1 ? ", " + samplesPerPixel + " bands" : "");
    }

    /** One line describing the file, for reports. */
    public String describe() {
        String layout = tiled() ? tileWidth + " x " + tileHeight + " tiles"
                : "strips of " + rowsPerStrip + " row(s)";
        String overviews = imageCount > 1 ? ", " + (imageCount - 1) + " overview(s)" : "";
        return width + " x " + height + " cells, " + dataTypeName() + ", " + compressionName() + ", "
                + predictorName() + ", " + layout + (bigTiff ? ", BigTIFF" : "") + overviews;
    }

    /** Reads the tags of the first image in {@code file}. */
    public static TiffTags read(Path file) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r");
             FileChannel ch = raf.getChannel()) {
            ByteBuffer head = read(ch, 0, 16);
            ByteOrder order;
            if (head.get(0) == 'I' && head.get(1) == 'I') {
                order = ByteOrder.LITTLE_ENDIAN;
            } else if (head.get(0) == 'M' && head.get(1) == 'M') {
                order = ByteOrder.BIG_ENDIAN;
            } else {
                throw new IOException("not a TIFF file (no II or MM byte-order mark)");
            }
            head.order(order);
            int magic = Short.toUnsignedInt(head.getShort(2));
            boolean big;
            long ifd;
            if (magic == 42) {
                big = false;
                ifd = Integer.toUnsignedLong(head.getInt(4));
            } else if (magic == 43) {
                big = true;
                ifd = head.getLong(8);
            } else {
                throw new IOException("not a TIFF file (version number " + magic + ")");
            }

            int[] v = new int[11];
            // width, height, tileW, tileH, rowsPerStrip, compression, predictor, bits, sampleFormat, spp
            v[4] = Integer.MAX_VALUE;
            v[5] = 1;
            v[6] = 1;
            v[7] = 1;
            v[8] = 1;
            v[9] = 1;
            int images = 0;
            long next = ifd;
            int guard = 0;
            while (next != 0 && guard++ < 64) {
                images++;
                long after = readIfd(ch, next, big, order, images == 1 ? v : null);
                next = after;
            }
            int rows = v[4] == Integer.MAX_VALUE ? v[1] : v[4];
            return new TiffTags(big, v[0], v[1], v[2], v[3], rows, v[5], v[6], v[7], v[8], v[9], images);
        }
    }

    /** Reads one IFD; fills {@code v} if not null; returns the offset of the next IFD (0 = none). */
    private static long readIfd(FileChannel ch, long offset, boolean big, ByteOrder order, int[] v)
            throws IOException {
        int countSize = big ? 8 : 2;
        int entrySize = big ? 20 : 12;
        ByteBuffer cb = read(ch, offset, countSize).order(order);
        long count = big ? cb.getLong(0) : Short.toUnsignedInt(cb.getShort(0));
        if (count <= 0 || count > 10_000) {
            throw new IOException("damaged TIFF directory (" + count + " entries)");
        }
        ByteBuffer entries = read(ch, offset + countSize, (int) count * entrySize).order(order);
        if (v != null) {
            for (int i = 0; i < count; i++) {
                int base = i * entrySize;
                int tag = Short.toUnsignedInt(entries.getShort(base));
                int type = Short.toUnsignedInt(entries.getShort(base + 2));
                int slot = slotFor(tag);
                if (slot < 0) {
                    continue;
                }
                int typeSize;
                switch (type) {
                    case 3: typeSize = 2; break;   // SHORT
                    case 4: typeSize = 4; break;   // LONG
                    case 16: typeSize = 8; break;  // LONG8 (BigTIFF)
                    default: continue;
                }
                long n = big ? entries.getLong(base + 4) : Integer.toUnsignedLong(entries.getInt(base + 4));
                int valuePos = base + (big ? 12 : 8);
                // Values that do not fit in the entry (for example BitsPerSample of a 3-band file)
                // are stored elsewhere and the entry holds their offset; read the first one there.
                ByteBuffer src = entries;
                int at = valuePos;
                if (n * typeSize > (big ? 8 : 4)) {
                    long pos = big ? entries.getLong(valuePos) : Integer.toUnsignedLong(entries.getInt(valuePos));
                    src = read(ch, pos, typeSize).order(order);
                    at = 0;
                }
                long value;
                switch (typeSize) {
                    case 2: value = Short.toUnsignedInt(src.getShort(at)); break;
                    case 4: value = Integer.toUnsignedLong(src.getInt(at)); break;
                    default: value = src.getLong(at);
                }
                v[slot] = (int) Math.min(value, Integer.MAX_VALUE);
            }
        }
        ByteBuffer nb = read(ch, offset + countSize + count * entrySize, big ? 8 : 4).order(order);
        return big ? nb.getLong(0) : Integer.toUnsignedLong(nb.getInt(0));
    }

    private static int slotFor(int tag) {
        switch (tag) {
            case 256: return 0;  // ImageWidth
            case 257: return 1;  // ImageLength
            case 322: return 2;  // TileWidth
            case 323: return 3;  // TileLength
            case 278: return 4;  // RowsPerStrip
            case 259: return 5;  // Compression
            case 317: return 6;  // Predictor
            case 258: return 7;  // BitsPerSample
            case 339: return 8;  // SampleFormat
            case 277: return 9;  // SamplesPerPixel
            default: return -1;
        }
    }

    private static ByteBuffer read(FileChannel ch, long pos, int len) throws IOException {
        ByteBuffer b = ByteBuffer.allocate(len);
        while (b.hasRemaining()) {
            if (ch.read(b, pos + b.position()) < 0) {
                throw new IOException("file ends early (truncated TIFF)");
            }
        }
        b.flip();
        return b;
    }
}
