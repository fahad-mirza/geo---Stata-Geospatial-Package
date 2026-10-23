# geo: geospatial analysis for Stata

**Status: Phase 0 (Foundation), pre-release. Not ready for use.**

geo will turn rasters (GeoTIFF; later NetCDF, GRIB, Zarr) and vector boundaries (Shapefile,
GeoJSON, GeoPackage, GeoParquet) into analysis variables inside Stata: zonal statistics, point and
buffer extraction, spatial joins, with results that match R (`exactextractr`, `terra`, `sf`) and
Python (`exactextract`, `geopandas`) within a relative tolerance of 1e-10.

The engine is Java (DuckDB spatial for vectors, GeoTools for rasters, JTS for geometry), called
through Stata's Java integration, so nothing needs installing besides Stata.

## Repository layout
| Folder | Contents |
| --- | --- |
| `sfi-stub/` | compile-only stand-ins for Stata's Java API (never shipped) |
| `geo-core/` | engine foundations: version, missing-value codes, tolerances |
| `geo-io/` | reading rasters and vectors (from Phase 0 task 3) |
| `geo-analysis/` | zonal statistics and extraction |
| `geo-stata/` | the only code that talks to Stata |
| `geo-dist/` | builds the single jar Stata loads, `geo.jar` |
| `stata/` | the Stata command (`geo.ado`), help file, package files |
| `tests/stata/` | Stata do-file checks |
| `.github/workflows/` | GitHub Actions builds and tests |

Builds run on GitHub Actions on Windows, macOS and Linux (x64 and ARM64). Development rules are in
[DEV_NOTES.md](DEV_NOTES.md).

Author: Fahad Mirza.
