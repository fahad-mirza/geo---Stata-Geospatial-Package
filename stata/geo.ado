*! geo 0.0.1  23oct2026  Fahad Mirza
*! Stata geospatial package: rasters and vector boundaries to analysis variables.
*! SKELETON: only "geo version" works. Subcommands (geo read, geo zonal, ...) arrive in later phases.
program define geo
    version 17
    gettoken sub rest : 0, parse(" ,")
    if `"`sub'"' == "" | `"`sub'"' == "version" {
        javacall org.fahadmirza.geo.stata.GeoJava version, jars(geo.jar)
        exit
    }
    display as error `"geo: unknown subcommand {bf:`sub'}"'
    display as error "This development skeleton supports only {bf:geo version}."
    exit 198
end
