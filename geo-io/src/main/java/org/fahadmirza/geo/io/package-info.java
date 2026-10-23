/**
 * Reading spatial data, with no Stata dependency. Empty in the skeleton.
 *
 * <p>Planned: GeoTIFF through GeoTools (512 x 512 processing tiles, reading only tiles that
 * intersect the polygons, a decoded-tile cache of about 1 GB at the default 4 GB heap);
 * vector files through DuckDB spatial (JDBC); WKB encode and decode.
 */
package org.fahadmirza.geo.io;
