*! check_dependencies.do  -  geo package, Phase 0 task 3 (and the packaging part of task 5)
*! Proves that the shaded geo.jar carries working copies of GeoTools and DuckDB inside Stata.
*
* WHAT IT CHECKS
*   Packaging  relocated (renamed) Apache Commons loads from geo.jar, not from Stata's own copy;
*              DuckDB kept its original package name (its native code needs it);
*              GeoTools' plugin list (META-INF/services) and ImageN's registry files are intact.
*   GeoTools   the GeoTIFF format is registered; the EPSG database answers EPSG:4326;
*              a small GeoTIFF is written and read back with every value identical.
*   DuckDB     a native engine for this machine is inside geo.jar; DuckDB answers SELECT 42.
*   Paths      the report and the test GeoTIFF go to a folder whose name contains spaces,
*              to check that paths with spaces survive the trip from Stata into Java.
*
* WHAT YOU NEED
*   The "geo-jar" artifact from a green run of the "ci" workflow, extracted to a folder.
*
* HOW TO RUN
*   1. Start a FRESH Stata session (Java keeps a jar loaded until Stata restarts).
*   2. cd to this folder in your clone, for example
*        cd "C:\Users\<you>\...\GitHub\geo - Stata Geospatial Package\tests\stata"
*   3. do check_dependencies.do "C:\Users\<you>\Downloads\geo-jar"
*   4. Paste check_dependencies.log (written to this folder; Git ignores it) back into the conversation.
*
* VERDICTS
*   PASS  works      FAIL  broken; paste the log back      WARN  works with a caveat
*   INFO  facts recorded for later (Java version, temporary folder, where classes load from)

version 19
args jardir

set more off
set linesize 255
capture log close depcheck
log using "check_dependencies.log", replace text name(depcheck)

local npass = 0
local nwarn = 0
local nfail = 0

display as text "{hline 100}"
display as text "geo: GeoTools and DuckDB inside geo.jar (Phase 0 task 3)"
display as text "Run on : `c(current_date)' `c(current_time)'"
display as text "Stata  : `c(stata_version)' `c(edition_real)', `c(os)' `c(machine_type)'"
display as text "{hline 100}"
capture noisily query java

* ---------------------------------------------------------------------------------------------
* Setup
* ---------------------------------------------------------------------------------------------
if `"`jardir'"' == "" {
    local jardir `"`c(pwd)'"'
    display as text _n "No folder given, so looking for geo.jar in the current folder."
}
display as text _n "Artifact folder: `jardir'"

capture confirm file `"`jardir'/geo.jar"'
if _rc {
    display as error "FAIL  [setup] geo.jar not found in that folder"
    display as error _n "OVERALL: FAIL. Extract the geo-jar artifact and give its folder, for example:"
    display as error `"    do check_dependencies.do "C:\Users\<you>\Downloads\geo-jar""'
    log close depcheck
    exit
}
display as text "PASS  [setup] geo.jar found"
local ++npass

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
local lastfile `"`c(tmpdir)'/geo_last_build_check_dependencies.txt"'
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
local stage `"`c(tmpdir)'/geo_depcheck"'
capture mkdir `"`stage'"'
capture copy `"`jardir'/geo.jar"' `"`stage'/geo.jar"', replace
if _rc {
    display as error "FAIL  [setup] could not copy geo.jar to `stage' (return code " _rc ")."
    display as error "      Usually Java still holds an older geo.jar open: restart Stata and run this again."
    log close depcheck
    exit
}
quietly adopath ++ `"`stage'"'

* A work folder whose name contains spaces: the report and the test GeoTIFF are written here.
local work `"`c(tmpdir)'/geo check folder with spaces"'
capture mkdir `"`work'"'
local report `"`work'/dependency report.tsv"'
capture erase `"`report'"'

* ---------------------------------------------------------------------------------------------
* Load check
* ---------------------------------------------------------------------------------------------
display as text _n "Loading geo.jar and asking it for its version:"
capture noisily javacall org.fahadmirza.geo.stata.GeoJava version, jars(geo.jar)
if _rc {
    display as error "FAIL  [load] geo.jar did not load or run (return code " _rc ")"
    local ++nfail
}
else {
    display as text "PASS  [load] geo.jar loaded and ran"
    local ++npass
}

* ---------------------------------------------------------------------------------------------
* Dependency checks (run in Java; results come back as a tab-separated report)
* ---------------------------------------------------------------------------------------------
display as text _n "Running the dependency checks (the first DuckDB and EPSG calls take a few seconds):"
capture noisily javacall org.fahadmirza.geo.stata.dev.DependencyCheck run, jars(geo.jar) ///
    args(`"`report'"' `"`work'"')
local rc_check = _rc
capture confirm file `"`report'"'
local rc_file = _rc
if `rc_check' | `rc_file' {
    if `rc_check' == 0 {
        display as error "FAIL  [paths] Java ran but no report appeared at:"
        display as error `"      `report'"'
        display as error "      The path with spaces was probably split into pieces on the way into Java."
    }
    else {
        display as error "FAIL  [checks] the dependency checks did not run (return code `rc_check'); see the messages above"
    }
    local ++nfail
    display as error _n "OVERALL: FAIL. Paste this log back into the conversation."
    quietly adopath - `"`stage'"'
    log close depcheck
    exit
}
display as text "PASS  [paths] a report path containing spaces reached Java intact"
local ++npass

preserve
quietly import delimited using `"`report'"', delimiters(tab) varnames(1) ///
    stringcols(_all) encoding("utf-8") bindquote(nobind) clear

local N = _N
local section ""
forvalues i = 1/`N' {
    if classname[`i'] != "`section'" {
        local section = classname[`i']
        display as text _n "{hline 100}"
        display as text "`section'"
        display as text "{hline 100}"
    }
    local v = verdict[`i']
    if "`v'" == "INFO" {
        display as text "INFO  " member[`i'] ": " detail[`i']
    }
    else if "`v'" == "PASS" {
        display as text "PASS  " member[`i']
        display as text "      " detail[`i']
        local ++npass
    }
    else if "`v'" == "WARN" {
        display as result "WARN  " as text member[`i']
        display as text "      " detail[`i']
        local ++nwarn
    }
    else {
        display as error "FAIL  " as text member[`i']
        display as error "      " detail[`i']
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
display as text "Checks: `npass' pass, `nwarn' warn, `nfail' fail"
if `nfail' > 0 {
    display as error "OVERALL: FAIL. `nfail' check(s) failed. Paste this log back into the conversation."
}
else if `nwarn' > 0 {
    display as result "OVERALL: PASS WITH WARNINGS. GeoTools and DuckDB work inside geo.jar; `nwarn' caveat(s) above."
}
else {
    display as result "OVERALL: PASS. GeoTools and DuckDB work inside geo.jar, and the packaging rules hold."
}
display as text "{hline 100}"
log close depcheck
display as text "Log written to: `c(pwd)'/check_dependencies.log"
