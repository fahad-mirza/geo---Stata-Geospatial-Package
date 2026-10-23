"""Generate the Phase 0 task 6 test GeoTIFFs: one synthetic raster saved in every compression
that matters for real data, plus manifest.csv with an exact checksum for each file.

Run by .github/workflows/test-rasters.yml (GitHub Actions); never needs to run on your machine.
Usage: python make_test_rasters.py <output-folder>

The raster is 2048 x 2048 cells of 0.01 degrees (a 20 x 20 degree box, EPSG:4326), with a smooth
pattern plus noise and a 128 x 128 block of nodata in one corner, in several data types and band
counts. All compressions except JPEG are lossless (LERC with MAX_Z_ERROR=0), so those files must
decode to exactly the numbers written; the JPEG file has no expected checksum.

Checksum of band 1 (must match RasterFileProbe in geo-io): for 32-bit floats, the sum of the raw
32-bit patterns read as signed integers; for 64-bit floats, the sum of the raw 64-bit patterns with
64-bit wrap-around; for integer types, the sum of the values; as a 64-bit integer.
Integer sums do not depend on the order of addition, so the comparison is exact.
"""
import csv
import os
import sys

import numpy as np
import rasterio
from rasterio.transform import from_origin
from rasterio.shutil import copy as rio_copy

N = 2048
TILE = ["TILED=YES", "BLOCKXSIZE=512", "BLOCKYSIZE=512"]

# (file name, source array, driver, creation options, lossless?, plain-language note)
# Source arrays: f32 float32, f32nan float32 with NaN as nodata, f64 float64, i16 int16,
# u16 uint16, i32 int32, u8 uint8, rgb three uint8 bands. The checksum covers band 1 only.
VARIANTS = [
    ("float32_none.tif", "f32", "GTiff", TILE + ["COMPRESS=NONE"], True, "uncompressed"),
    ("float32_lzw.tif", "f32", "GTiff", TILE + ["COMPRESS=LZW"], True, "LZW"),
    ("float32_lzw_pred3.tif", "f32", "GTiff", TILE + ["COMPRESS=LZW", "PREDICTOR=3"], True, "LZW + floating-point predictor"),
    ("float32_deflate.tif", "f32", "GTiff", TILE + ["COMPRESS=DEFLATE"], True, "Deflate"),
    ("float32_deflate_pred3.tif", "f32", "GTiff", TILE + ["COMPRESS=DEFLATE", "PREDICTOR=3"], True, "Deflate + floating-point predictor"),
    ("float32_deflate_strips.tif", "f32", "GTiff", ["TILED=NO", "COMPRESS=DEFLATE", "PREDICTOR=3"], True, "Deflate + predictor; strips instead of tiles"),
    ("float32_zstd.tif", "f32", "GTiff", TILE + ["COMPRESS=ZSTD"], True, "ZSTD"),
    ("float32_zstd_pred3.tif", "f32", "GTiff", TILE + ["COMPRESS=ZSTD", "PREDICTOR=3"], True, "ZSTD + floating-point predictor"),
    ("float32_lerc.tif", "f32", "GTiff", TILE + ["COMPRESS=LERC", "MAX_Z_ERROR=0"], True, "LERC (lossless)"),
    ("float32_lerc_deflate.tif", "f32", "GTiff", TILE + ["COMPRESS=LERC_DEFLATE", "MAX_Z_ERROR=0"], True, "LERC + Deflate (lossless)"),
    ("float32_lerc_zstd.tif", "f32", "GTiff", TILE + ["COMPRESS=LERC_ZSTD", "MAX_Z_ERROR=0"], True, "LERC + ZSTD (lossless)"),
    ("float32_cog_deflate.tif", "f32", "COG", ["COMPRESS=DEFLATE", "PREDICTOR=YES", "BLOCKSIZE=512"], True, "Cloud-Optimized GeoTIFF; Deflate"),
    ("float32_cog_zstd.tif", "f32", "COG", ["COMPRESS=ZSTD", "PREDICTOR=YES", "BLOCKSIZE=512"], True, "Cloud-Optimized GeoTIFF; ZSTD"),
    ("float32_nan_nodata_zstd_pred3.tif", "f32nan", "GTiff", TILE + ["COMPRESS=ZSTD", "PREDICTOR=3"], True, "NaN (not a number) as nodata; ZSTD + predictor"),
    ("float32_bigtiff_deflate.tif", "f32", "GTiff", TILE + ["COMPRESS=DEFLATE", "PREDICTOR=3", "BIGTIFF=YES"], True, "BigTIFF layout; Deflate + predictor"),
    ("float64_deflate_pred3.tif", "f64", "GTiff", TILE + ["COMPRESS=DEFLATE", "PREDICTOR=3"], True, "64-bit float; Deflate + floating-point predictor"),
    ("int16_deflate_pred2.tif", "i16", "GTiff", TILE + ["COMPRESS=DEFLATE", "PREDICTOR=2"], True, "16-bit signed integers; Deflate + horizontal predictor"),
    ("uint16_deflate_pred2.tif", "u16", "GTiff", TILE + ["COMPRESS=DEFLATE", "PREDICTOR=2"], True, "16-bit unsigned integers; Deflate + horizontal predictor"),
    ("int32_zstd_pred2.tif", "i32", "GTiff", TILE + ["COMPRESS=ZSTD", "PREDICTOR=2"], True, "32-bit signed integers; ZSTD + horizontal predictor"),
    ("uint8_lzw.tif", "u8", "GTiff", TILE + ["COMPRESS=LZW"], True, "8-bit unsigned integers; LZW"),
    ("rgb_uint8_deflate.tif", "rgb", "GTiff", TILE + ["COMPRESS=DEFLATE", "PREDICTOR=2"], True, "3 bands of 8-bit integers; Deflate + predictor"),
    ("rgb_uint8_jpeg.tif", "rgb", "GTiff", TILE + ["COMPRESS=JPEG", "JPEG_QUALITY=90"], False, "3 bands; JPEG (lossy: compared across thread counts only)"),
    ("rgb_uint8_webp_lossless.tif", "rgb", "GTiff", TILE + ["COMPRESS=WEBP", "WEBP_LOSSLESS=TRUE"], True, "3 bands; WebP (lossless)"),
    ("rgb_uint8_jxl_lossless.tif", "rgb", "GTiff", TILE + ["COMPRESS=JXL", "JXL_LOSSLESS=YES"], True, "3 bands; JPEG XL (lossless)"),
]


def make_arrays():
    rng = np.random.default_rng(20261023)
    y, x = np.mgrid[0:N, 0:N].astype(np.float64)
    base = np.sin(x / 97.0) * np.cos(y / 131.0) * 50.0 + 100.0 + rng.normal(0.0, 0.5, (N, N))
    corner = (slice(0, 128), slice(0, 128))  # a 128 x 128 block of nodata in one corner

    f32 = base.astype(np.float32); f32[corner] = -9999.0
    f32nan = base.astype(np.float32); f32nan[corner] = np.nan
    f64 = (base + rng.normal(0.0, 1e-6, (N, N))); f64[corner] = -9999.0
    i16 = np.clip(np.round(base * 10.0), -32767, 32767).astype(np.int16); i16[corner] = -32768
    u16 = np.clip(np.round(base * 100.0), 0, 65534).astype(np.uint16); u16[corner] = 65535
    i32 = np.round(base * 1000.0).astype(np.int32); i32[corner] = -2147483648
    u8 = np.clip(np.round(base), 0, 254).astype(np.uint8); u8[corner] = 255
    rgb = np.stack([u8, np.clip(np.round(base * 1.5) - 40, 0, 254).astype(np.uint8),
                    np.clip(250 - np.round(base), 0, 254).astype(np.uint8)])
    # (array, nodata, data type name for rasterio)
    return {"f32": (f32, -9999.0, "float32"), "f32nan": (f32nan, float("nan"), "float32"),
            "f64": (f64, -9999.0, "float64"), "i16": (i16, -32768, "int16"),
            "u16": (u16, 65535, "uint16"), "i32": (i32, -2147483648, "int32"),
            "u8": (u8, 255, "uint8"), "rgb": (rgb, None, "uint8")}


def checksum(arr):
    """Band-1 checksum, identical to RasterFileProbe.blockChecksum in geo-io."""
    band1 = arr[0] if arr.ndim == 3 else arr
    if band1.dtype == np.float32:
        return int(band1.view(np.int32).astype(np.int64).sum())
    if band1.dtype == np.float64:
        # Java adds the 64-bit patterns in a long, which wraps around on overflow; do the same.
        total = 0
        for v in band1.view(np.int64).ravel().tolist():
            total = (total + v + 2**63) % 2**64 - 2**63
        return total
    return int(band1.astype(np.int64).sum())


def options_dict(opts):
    return dict(o.split("=", 1) for o in opts)


def main(out):
    os.makedirs(out, exist_ok=True)
    arrays = make_arrays()
    transform = from_origin(30.0, 10.0, 0.01, 0.01)  # west, north, cell width, cell height
    rows = []
    for name, source, driver, opts, lossless, note in VARIANTS:
        arr, nodata, dtype = arrays[source]
        bands = arr.shape[0] if arr.ndim == 3 else 1
        path = os.path.join(out, name)
        profile = dict(width=N, height=N, count=bands, dtype=dtype, crs="EPSG:4326", transform=transform)
        if nodata is not None:
            profile["nodata"] = nodata
        if bands == 3:
            profile["photometric"] = "RGB" if "JPEG" not in " ".join(opts) else "YCBCR"
        data = arr if bands > 1 else arr[np.newaxis, ...]
        try:
            if driver == "GTiff":
                with rasterio.open(path, "w", driver="GTiff", **profile, **options_dict(opts)) as dst:
                    dst.write(data)
            else:  # COG: write a plain GeoTIFF first, then let GDAL's COG driver copy it
                tmp = path + ".tmp.tif"
                with rasterio.open(tmp, "w", driver="GTiff", **profile) as dst:
                    dst.write(data)
                rio_copy(tmp, path, driver="COG", **options_dict(opts))
                os.remove(tmp)
            with rasterio.open(path) as src:  # read back with GDAL itself
                back = src.read()
                comp = src.compression.name if src.compression else "none"
            if lossless and not np.array_equal(back.view(np.uint8), data.view(np.uint8)):
                raise RuntimeError("GDAL did not read back identical values from a lossless file")
            expected = checksum(back if not lossless else data)
            rows.append(dict(file=name, data_type=dtype, bands=bands, note=note, created="yes",
                             gdal_compression=comp, lossless="yes" if lossless else "no",
                             expected_checksum=expected if lossless else "",
                             size_bytes=os.path.getsize(path), error=""))
            print(f"created  {name:36s} {os.path.getsize(path):>12,d} bytes  {note}")
        except Exception as e:  # a compression this GDAL build lacks: record it and continue
            rows.append(dict(file=name, data_type=dtype, bands=bands, note=note, created="no",
                             gdal_compression="", lossless="yes" if lossless else "no",
                             expected_checksum="", size_bytes="", error=str(e).replace(",", ";")))
            print(f"SKIPPED  {name:36s} {e}")
            for leftover in (path, path + ".tmp.tif"):
                if os.path.exists(leftover):
                    os.remove(leftover)
    with open(os.path.join(out, "manifest.csv"), "w", newline="") as f:
        w = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
        w.writeheader()
        w.writerows(rows)
    print(f"GDAL {rasterio.__gdal_version__}, rasterio {rasterio.__version__}: "
          f"{sum(r['created'] == 'yes' for r in rows)} of {len(rows)} files created")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "test-rasters")
