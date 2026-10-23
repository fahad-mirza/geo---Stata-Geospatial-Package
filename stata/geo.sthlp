{smcl}
{* *! version 0.0.1  23oct2026}{...}
{title:Title}

{phang}
{bf:geo} {hline 2} Geospatial analysis for Stata: rasters and vector boundaries to analysis variables
(development skeleton)


{title:Syntax}

{p 8 16 2}
{cmd:geo version}


{title:Description}

{pstd}
{cmd:geo} will turn rasters (GeoTIFF, later NetCDF, GRIB and Zarr) and vector boundaries into
analysis variables inside Stata: zonal statistics, point and buffer extraction, and spatial joins.
Its engine is written in Java and runs through Stata's Java integration, so nothing needs to be
installed besides Stata.

{pstd}
This is the Phase 0 skeleton. Only {cmd:geo version} works: it reports the package version, the
build (the Git commit that produced {bf:geo.jar}), the Java version, the platform, the number of
logical cores and the maximum Java heap.


{title:Requirements}

{pstd}
Stata with Java 17 or later (Stata 18 and later; Stata 17 only after it was updated to Java 17).
The minimum version will be checked at load time from Phase 0 task 12.


{title:Missing values in results}

{pstd}
{cmd:geo} records why a result is missing: {bf:.a} no valid pixels, {bf:.b} outside the raster,
{bf:.c} invalid or empty geometry, {bf:.} anything else.


{title:Author}

{pstd}
Fahad Mirza
