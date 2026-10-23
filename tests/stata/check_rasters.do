*! check_rasters.do  -  geo package, Phase 0 task 6 (real GeoTIFFs)
*! Reads every GeoTIFF in a folder through geo.jar and reports, per file, whether geo can decode
*! it, whether the numbers are identical with 1 thread and with all threads, and how fast.
*
* WHAT IT CHECKS FOR EACH .tif FILE
*   structure  compression, predictor, tiles or strips, data type (read from the file header,
*              so this works even for files geo cannot decode)
*   decoding   every tile (or band of strips) is decoded with 1 thread and again with all threads;
*              an exact checksum must match between the two passes. For the generated test files
*              it must also match the checksum the generator recorded in manifest.csv.
*              Files above 64 million cells are sampled with evenly spaced blocks.
*   GeoTools   the file opens through GeoTools' GeoTIFF reader with its coordinate system
*
* WHAT YOU NEED
*   1. The "geo-jar" artifact from a green "ci" run, extracted (for example Downloads\geo-jar).
*   2. A folder of GeoTIFFs, for example the "test-rasters" artifact from the "test-rasters"
*      workflow (generated files in every compression, plus manifest.csv), and/or real files:
*      get_real_rasters.do downloads public WorldPop and CHIRPS files; add VIIRS by hand.
*      Country-sized files are best; global files work too but are sampled.
*
* HOW TO RUN
*   1. Start a FRESH Stata session (Java keeps a jar loaded until Stata restarts).
*   2. cd to this folder in your clone (tests\stata).
*   3. do check_rasters.do "C:\Users\<you>\Downloads\geo-jar" "C:\Users\<you>\Downloads\test-rasters"
*      Optional third argument: number of threads for the parallel pass (default: all cores).
*   4. Paste check_rasters.log (written to this folder; Git ignores it) back into the conversation.
*
* VERDICTS (one line per file at the end)
*   PASS  reads correctly        WARN  geo cannot read this compression yet (a finding, not a bug)
*   FAIL  wrong numbers or an unexpected error; paste the log back

version 19
args jardir rasterdir threads

set more off
set linesize 255
capture log close rastcheck
log using "check_rasters.log", replace text name(rastcheck)

local npass = 0
local nwarn = 0
local nfail = 0

display as text "{hline 100}"
display as text "geo: reading real GeoTIFFs (Phase 0 task 6)"
display as text "Run on : `c(current_date)' `c(current_time)'"
display as text "Stata  : `c(stata_version)' `c(edition_real)', `c(os)' `c(machine_type)'"
display as text "{hline 100}"
capture noisily query java

* ---------------------------------------------------------------------------------------------
* Setup
* ---------------------------------------------------------------------------------------------
if `"`jardir'"' == "" | `"`rasterdir'"' == "" {
    display as error "Give two folders: the geo-jar artifact and the folder of GeoTIFFs, for example"
    display as error `"    do check_rasters.do "C:\Users\<you>\Downloads\geo-jar" "C:\Users\<you>\Downloads\test-rasters""'
    log close rastcheck
    exit
}
display as text _n "Artifact folder : `jardir'"
display as text "Raster folder   : `rasterdir'"
display as text "Threads         : " cond(`"`threads'"' == "", "all cores Java sees", `"`threads'"')

capture confirm file `"`jardir'/geo.jar"'
if _rc {
    display as error "FAIL  [setup] geo.jar not found in `jardir'"
    log close rastcheck
    exit
}
local files : dir `"`rasterdir'"' files "*.tif"
local files2 : dir `"`rasterdir'"' files "*.tiff"
if `"`files'`files2'"' == "" {
    display as error "FAIL  [setup] no .tif or .tiff files in `rasterdir'"
    log close rastcheck
    exit
}
display as text "PASS  [setup] geo.jar found, and the raster folder holds GeoTIFF files"
local ++npass
capture confirm file `"`rasterdir'/manifest.csv"'
if !_rc {
    display as text "      manifest.csv found: generated files will be compared with their recorded checksums"
}

capture confirm file `"`jardir'/BUILD-INFO.txt"'
if !_rc {
    display as text _n "Build information shipped with the jar:"
    type `"`jardir'/BUILD-INFO.txt"'
}
* Stale-jar guard: note it when this check last ran on the very same build, which usually means
* the new artifact was not downloaded or was extracted to a different folder.
tempname fh
local thisbuild ""
capture file open `fh' using `"`jardir'/BUILD-INFO.txt"', read text
if !_rc {
    file read `fh' line
    while r(eof) == 0 {
        if substr(`"`macval(line)'"', 1, 7) == "commit:" {
            local thisbuild = strtrim(substr(`"`macval(line)'"', 8, .))
        }
        file read `fh' line
    }
    file close `fh'
}
local lastfile `"`c(tmpdir)'/geo_last_build_check_rasters.txt"'
local lastbuild ""
local lastwhen ""
capture file open `fh' using `"`lastfile'"', read text
if !_rc {
    file read `fh' lastbuild
    file read `fh' lastwhen
    file close `fh'
}
if "`thisbuild'" != "" & "`thisbuild'" == "`lastbuild'" {
    display as result _n "NOTE  This check already ran on this same build (commit `thisbuild') on `lastwhen'."
    display as result "      If you expected a newer build, download geo-jar again from the latest green run"
    display as result "      and extract it into `jardir', replacing the old files."
}
capture file open `fh' using `"`lastfile'"', write text replace
if !_rc {
    file write `fh' "`thisbuild'" _n "`c(current_date)' `c(current_time)'" _n
    file close `fh'
}

* Put ONLY geo.jar on the ado-path, from a private folder (javacall's jars() searches the ado-path).
local stage `"`c(tmpdir)'/geo_rastcheck"'
capture mkdir `"`stage'"'
capture copy `"`jardir'/geo.jar"' `"`stage'/geo.jar"', replace
if _rc {
    display as error "FAIL  [setup] could not copy geo.jar to `stage' (return code " _rc ")."
    display as error "      Usually Java still holds an older geo.jar open: restart Stata and run this again."
    log close rastcheck
    exit
}
quietly adopath ++ `"`stage'"'

capture noisily javacall org.fahadmirza.geo.stata.GeoJava version, jars(geo.jar)

* ---------------------------------------------------------------------------------------------
* Read every file (in Java; results come back as a tab-separated report)
* ---------------------------------------------------------------------------------------------
tempfile reportbase
local report `"`reportbase'.tsv"'
display as text _n "Reading the files (each is decoded twice; large files take a while):"
capture noisily javacall org.fahadmirza.geo.stata.dev.RasterCheck run, jars(geo.jar) ///
    args(`"`rasterdir'"' `"`report'"' `"`threads'"')
local rc_check = _rc
capture confirm file `"`report'"'
if `rc_check' | _rc {
    display as error "FAIL  [read] the raster check did not run (return code `rc_check'); see the messages above"
    display as error _n "OVERALL: FAIL. Paste this log back into the conversation."
    quietly adopath - `"`stage'"'
    log close rastcheck
    exit
}

preserve
quietly import delimited using `"`report'"', delimiters(tab) varnames(1) ///
    stringcols(_all) encoding("utf-8") bindquote(nobind) clear

local N = _N
local current ""
forvalues i = 1/`N' {
    if kind[`i'] == "VERDICT" {
        continue
    }
    if classname[`i'] != "`current'" {
        local current = classname[`i']
        display as text _n "{hline 100}"
        display as text "`current'"
        display as text "{hline 100}"
    }
    local v = verdict[`i']
    if "`v'" == "INFO" {
        display as text "INFO  " member[`i'] ": " detail[`i']
    }
    else if "`v'" == "PASS" {
        display as text "PASS  " member[`i'] ": " detail[`i']
    }
    else if "`v'" == "WARN" {
        display as result "WARN  " as text member[`i'] ": " detail[`i']
    }
    else {
        display as error "FAIL  " as text member[`i'] ": " detail[`i']
    }
}

display as text _n "{hline 100}"
display as text "One verdict per file"
display as text "{hline 100}"
forvalues i = 1/`N' {
    if kind[`i'] != "VERDICT" {
        continue
    }
    local v = verdict[`i']
    if "`v'" == "PASS" {
        display as text "PASS  " classname[`i'] ": " detail[`i']
        local ++npass
    }
    else if "`v'" == "WARN" {
        display as result "WARN  " as text classname[`i'] ": " detail[`i']
        local ++nwarn
    }
    else {
        display as error "FAIL  " as text classname[`i'] ": " detail[`i']
        local ++nfail
    }
}
restore
quietly adopath - `"`stage'"'
capture erase `"`report'"'

* ---------------------------------------------------------------------------------------------
* Overall verdict
* ---------------------------------------------------------------------------------------------
display as text _n "{hline 100}"
display as text "Files: `=`npass'-1' read correctly, `nwarn' not readable yet, `nfail' failed"
if `nfail' > 0 {
    display as error "OVERALL: FAIL. `nfail' file(s) gave wrong numbers or an unexpected error. Paste this log back."
}
else if `nwarn' > 0 {
    display as result "OVERALL: PASS WITH FINDINGS. Every readable file decodes correctly; `nwarn' file(s) use a compression geo cannot read yet."
}
else {
    display as result "OVERALL: PASS. Every file decodes correctly and identically at any thread count."
}
display as text "{hline 100}"
log close rastcheck
display as text "Log written to: `c(pwd)'/check_rasters.log"
