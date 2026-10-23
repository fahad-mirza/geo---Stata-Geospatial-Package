*! get_real_rasters.do  -  geo package, Phase 0 task 6 helper
*! Downloads a few real, public GeoTIFFs into a folder, for check_rasters.do to read.
*! Files already in the folder are not downloaded again.
*
* WHAT IT DOWNLOADS (all public, no login)
*   WorldPop 2020 population, Timor-Leste, 100 m (8.6 MB)            link verified 23 Oct 2026
*   WorldPop 2020 population, Kenya, 100 m, constrained (33 MB)       link verified 23 Oct 2026
*   CHIRPS rainfall, Africa, monthly, December 2017 (.tif.gz, ~4 MB)  file name verified; folder
*   CHIRPS rainfall, global, monthly, January 2020 (.tif.gz)          follows CHIRPS' standard layout
*   With the option "large": WorldPop 2020 global mosaic, 1 km (829 MB)  link verified 23 Oct 2026
*   CHIRPS files arrive gzip-compressed; this do-file unpacks them to .tif with Stata's own Java.
*
* NOT DOWNLOADED: VIIRS nighttime lights. The Colorado School of Mines' Earth Observation Group
*   requires a free account (NASA's Black Marble version needs an Earthdata login). Download one
*   country-sized VIIRS GeoTIFF in your browser and put it in the same folder.
*
* HOW TO RUN (any Stata session)
*   cd to this folder in your clone (tests\stata), then
*     do get_real_rasters.do "C:\Users\<you>\Downloads\real-rasters"
*     do get_real_rasters.do "C:\Users\<you>\Downloads\real-rasters" large     (adds the 829 MB file)
*   Then read them:
*     do check_rasters.do "C:\Users\<you>\Downloads\geo-jar" "C:\Users\<you>\Downloads\real-rasters"
*   You can also put the real files in the same folder as the generated test-rasters.
*
* IF DOWNLOADS FAIL ON AN OFFICE NETWORK
*   Stata may need your organisation's web proxy: see "help netio" (set httpproxy ...), or
*   download the files in your browser from the links printed below and put them in the folder.

version 19
args folder large

set more off
set linesize 255
capture log close getrast
log using "get_real_rasters.log", replace text name(getrast)

if `"`folder'"' == "" {
    display as error "Give the folder to download into, for example"
    display as error `"    do get_real_rasters.do "C:\Users\<you>\Downloads\real-rasters""'
    log close getrast
    exit
}
capture mkdir `"`folder'"'
display as text "{hline 100}"
display as text "geo: downloading real GeoTIFFs for the Phase 0 task 6 check"
display as text "Folder : `folder'"
display as text "Run on : `c(current_date)' `c(current_time)'"
display as text "{hline 100}"

* name | URL. Names are what the files are saved as.
local n = 0
local ++n
local name`n' "worldpop_tls_2020_100m.tif"
local url`n'  "https://data.worldpop.org/GIS/Population/Global_2000_2020/2020/TLS/tls_ppp_2020.tif"
local ++n
local name`n' "worldpop_ken_2020_100m_constrained.tif"
local url`n'  "https://worldpop-public-data.soton.ac.uk/GIS/Population/Global_2000_2020_Constrained/2020/maxar_v1/KEN/ken_ppp_2020_constrained.tif"
local ++n
local name`n' "chirps_africa_monthly_2017_12.tif.gz"
local url`n'  "https://data.chc.ucsb.edu/products/CHIRPS-2.0/africa_monthly/tifs/chirps-v2.0.2017.12.tif.gz"
local ++n
local name`n' "chirps_global_monthly_2020_01.tif.gz"
local url`n'  "https://data.chc.ucsb.edu/products/CHIRPS-2.0/global_monthly/tifs/chirps-v2.0.2020.01.tif.gz"
if "`large'" == "large" {
    local ++n
    local name`n' "worldpop_global_2020_1km.tif"
    local url`n'  "https://data.worldpop.org/GIS/Population/Global_2000_2020/2020/0_Mosaicked/ppp_2020_1km_Aggregated.tif"
}

* ---------------------------------------------------------------------------------------------
* Download (skipping files already present)
* ---------------------------------------------------------------------------------------------
forvalues i = 1/`n' {
    local final = subinstr("`name`i''", ".gz", "", .)
    capture confirm file `"`folder'/`final'"'
    if !_rc {
        display as text "HAVE  `final' (already in the folder)"
        continue
    }
    display as text "GET   `name`i''" _n "      from `url`i''"
    capture copy "`url`i''" `"`folder'/`name`i''"', replace
    if _rc {
        display as error "FAIL  `name`i'' could not be downloaded (return code " _rc ")."
        display as error "      Open the link above in your browser; if it works there, Stata may need the proxy setting."
        capture erase `"`folder'/`name`i''"'
    }
}

* ---------------------------------------------------------------------------------------------
* Unpack .tif.gz files with Stata's Java (Stata's copy cannot unzip gzip files)
* ---------------------------------------------------------------------------------------------
global GEO_RASTER_FOLDER `"`folder'"'
java:
    import java.io.*;
    import java.nio.file.*;
    import java.util.zip.GZIPInputStream;
    import com.stata.sfi.*;

    Path dir = Paths.get(Macro.getGlobal("GEO_RASTER_FOLDER"));
    try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.tif.gz")) {
        for (Path gz : ds) {
            String n = gz.getFileName().toString();
            Path tif = dir.resolve(n.substring(0, n.length() - 3));
            if (Files.exists(tif)) { Files.deleteIfExists(gz); continue; }
            try (InputStream in = new GZIPInputStream(new BufferedInputStream(Files.newInputStream(gz)))) {
                Files.copy(in, tif);
                Files.delete(gz);
                SFIToolkit.displayln("UNZIP " + n + " -> " + tif.getFileName());
            } catch (IOException e) {
                Files.deleteIfExists(tif);
                SFIToolkit.errorln("FAIL  " + n + " could not be unpacked (" + e.getMessage()
                        + "); it is probably not a gzip file (for example an error page from a proxy)");
            }
        }
    } catch (IOException e) {
        SFIToolkit.errorln("FAIL  could not list " + dir + ": " + e);
    }
end
macro drop GEO_RASTER_FOLDER

* ---------------------------------------------------------------------------------------------
* Verify: each file must start with a TIFF signature ("II" or "MM"), not an HTML error page
* ---------------------------------------------------------------------------------------------
display as text _n "{hline 100}"
display as text "One verdict per file"
display as text "{hline 100}"
local ok = 0
local bad = 0
tempname fh
forvalues i = 1/`n' {
    local final = subinstr("`name`i''", ".gz", "", .)
    capture confirm file `"`folder'/`final'"'
    if _rc {
        display as error "FAIL  `final': not in the folder (download or unpacking failed; see above)"
        local ++bad
        continue
    }
    file open `fh' using `"`folder'/`final'"', read binary
    file read `fh' %1bu b1
    file read `fh' %1bu b2
    file close `fh'
    if (`b1' == 73 & `b2' == 73) | (`b1' == 77 & `b2' == 77) {
        display as text "PASS  `final' is a TIFF file"
        local ++ok
    }
    else {
        display as error "FAIL  `final' is not a TIFF file (probably an error page); deleting it"
        erase `"`folder'/`final'"'
        local ++bad
    }
}
display as text _n "{hline 100}"
if `bad' == 0 {
    display as result "OVERALL: PASS. `ok' real GeoTIFF file(s) ready in `folder'."
}
else {
    display as result "OVERALL: `ok' file(s) ready, `bad' missing. check_rasters.do will read whatever is there."
}
display as text "Add a VIIRS nighttime-lights GeoTIFF by hand if you have one (see the header of this do-file)."
display as text "{hline 100}"
log close getrast
