/**
 * Reading spatial data, with no Stata dependency.
 *
 * <p>Phase 0 task 3: {@link org.fahadmirza.geo.io.GeoToolsProbe} and
 * {@link org.fahadmirza.geo.io.DuckDbProbe} prove that GeoTools and DuckDB work from inside geo.jar.
 * Planned: GeoTIFF reading through GeoTools (512 x 512 processing tiles, reading only tiles that
 * intersect the polygons, a decoded-tile cache of about 1 GB at the default 4 GB heap); vector
 * files through DuckDB spatial; WKB encode and decode.
 */
package org.fahadmirza.geo.io;
