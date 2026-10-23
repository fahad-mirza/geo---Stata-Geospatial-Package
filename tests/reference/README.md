# Reference outputs (placeholder)

Phase 0 task 13 adds `.github/workflows/reference.yml`, which computes reference results with R
(exactextractr, terra, sf) and Python (exactextract, rasterstats, geopandas) for the test rasters
and commits them here. geo must match them within a relative tolerance of 1e-10.

Small test GeoTIFFs may also live here; large ones are downloaded by a script, never committed.
