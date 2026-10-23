*! check_stubs.do  -  geo package, Phase 0 task 4
*! Confirms that every member of the sfi-stub stand-in classes exists, with the same
*! signature, in Stata's real Java API (com.stata.sfi).
*
* WHY THIS CHECK EXISTS
*   StataCorp's sfi-api.jar is never committed to the public repository. GitHub Actions compiles
*   geo against "sfi-stub": stand-in classes that copy the method signatures from Stata's online
*   Java API pages. If a stand-in ever differs from the real class, geo.jar would compile in the
*   cloud and then fail inside Stata. This do-file catches that on your machine.
*
* WHAT YOU NEED
*   The "geo-jar" artifact from a green run of the "ci" workflow (GitHub, Actions tab), extracted
*   to a folder. It holds geo.jar, sfi-stub.jar and BUILD-INFO.txt.
*
* HOW TO RUN
*   1. Start a FRESH Stata session (Java keeps a jar loaded until Stata restarts).
*   2. cd to the folder holding this do-file, for example
*        cd "C:\Users\<you>\Documents\GitHub\stata-geo\tests\stata"
*   3. Run it with the artifact folder as the argument, for example
*        do check_stubs.do "C:\Users\<you>\Downloads\geo-jar"
*   4. Paste check_stubs.log (written to the current folder) back into the conversation.
*
* WHAT IT PRINTS (one verdict line per check, then one OVERALL line)
*   PASS  identical to the real API
*   FAIL  code compiled against the stub would break inside Stata; the stub must be fixed
*   WARN  works, but something differs (usually which exceptions a method declares)
*   INFO  recorded for later tasks (the values of Stata's real constants)
*
* HOW IT WORKS
*   javacall loads geo.jar; geo's StubCheck class opens sfi-stub.jar in a separate classloader
*   (so it sees the stand-ins instead of Stata's classes), compares every public member by
*   reflection without calling anything, and writes a tab-separated report that this do-file reads.
*   sfi-stub.jar is never placed on the ado-path; only geo.jar is, from a private temporary folder.

version 19
args jardir

set more off
set linesize 255
capture log close stubcheck
log using "check_stubs.log", replace text name(stubcheck)

local npass = 0
local nwarn = 0
local nfail = 0

display as text "{hline 100}"
display as text "geo: sfi-stub versus Stata's real Java API (Phase 0 task 4)"
display as text "Run on : `c(current_date)' `c(current_time)'"
display as text "Stata  : `c(stata_version)' `c(edition_real)', `c(os)' `c(machine_type)'"
display as text "{hline 100}"
capture noisily query java

* ---------------------------------------------------------------------------------------------
* Setup checks: are the jars where we expect them?
* ---------------------------------------------------------------------------------------------
if `"`jardir'"' == "" {
    local jardir `"`c(pwd)'"'
    display as text _n "No folder given, so looking for the jars in the current folder."
}
display as text _n "Artifact folder: `jardir'"

foreach f in geo.jar sfi-stub.jar {
    capture confirm file `"`jardir'/`f'"'
    if _rc {
        display as error "FAIL  [setup] `f' not found in that folder"
        local ++nfail
    }
    else {
        display as text "PASS  [setup] `f' found"
        local ++npass
    }
}
if `nfail' > 0 {
    display as error _n "OVERALL: FAIL. Extract the geo-jar artifact and give its folder, for example:"
    display as error `"    do check_stubs.do "C:\Users\<you>\Downloads\geo-jar""'
    log close stubcheck
    exit
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
local lastfile `"`c(tmpdir)'/geo_last_build_check_stubs.txt"'
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

* Put ONLY geo.jar on the ado-path (javacall's jars() option searches the ado-path),
* using a private folder so sfi-stub.jar can never be picked up by Stata.
local stage `"`c(tmpdir)'/geo_stubcheck"'
capture mkdir `"`stage'"'
capture copy `"`jardir'/geo.jar"' `"`stage'/geo.jar"', replace
if _rc {
    display as error "FAIL  [setup] could not copy geo.jar to `stage' (return code " _rc ")."
    display as error "      Usually Java still holds an older geo.jar open: restart Stata and run this again."
    log close stubcheck
    exit
}
quietly adopath ++ `"`stage'"'

* ---------------------------------------------------------------------------------------------
* Load check: does geo.jar load and run?
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
* Stub check: every public member of every stand-in class, compared with the real class
* ---------------------------------------------------------------------------------------------
tempfile reportbase
local report `"`reportbase'.tsv"'
display as text _n "Comparing sfi-stub.jar with Stata's real classes:"
capture noisily javacall org.fahadmirza.geo.stata.dev.StubCheck run, jars(geo.jar) ///
    args(`"`jardir'/sfi-stub.jar"' `"`report'"')
local rc_check = _rc
capture confirm file `"`report'"'
if `rc_check' | _rc {
    display as error "FAIL  [stubs] the comparison did not run (return code `rc_check'); see the messages above"
    local ++nfail
    display as error _n "OVERALL: FAIL. Paste this log back into the conversation."
    quietly adopath - `"`stage'"'
    log close stubcheck
    exit
}

preserve
quietly import delimited using `"`report'"', delimiters(tab) varnames(1) ///
    stringcols(_all) encoding("utf-8") bindquote(nobind) clear

display as text _n "{hline 100}"
display as text "One line per stubbed class and member (class, then signature):"
display as text "{hline 100}"
local N = _N
forvalues i = 1/`N' {
    local v = verdict[`i']
    if "`v'" == "INFO" {
        continue
    }
    if "`v'" == "PASS" {
        display as text "PASS  " classname[`i'] "  " member[`i']
        if kind[`i'] == "CLASS" {
            display as text "      " detail[`i']
        }
        local ++npass
    }
    else if "`v'" == "WARN" {
        display as result "WARN  " as text classname[`i'] "  " member[`i']
        display as text "      " detail[`i']
        local ++nwarn
    }
    else {
        display as error "FAIL  " as text classname[`i'] "  " member[`i']
        display as error "      " detail[`i']
        local ++nfail
    }
}

quietly count if verdict == "INFO"
if r(N) > 0 {
    display as text _n "Values of Stata's real constants (not stubbed; recorded for later tasks):"
    forvalues i = 1/`N' {
        if verdict[`i'] == "INFO" {
            display as text "INFO  " classname[`i'] "  " member[`i'] "  " detail[`i']
        }
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
    display as error "OVERALL: FAIL. `nfail' item(s) do not match Stata's real API. Paste this log back so the stubs can be fixed."
}
else if `nwarn' > 0 {
    display as result "OVERALL: PASS WITH WARNINGS. Every stubbed member exists in Stata's real API; `nwarn' differ only in declared exceptions or modifiers."
}
else {
    display as result "OVERALL: PASS. Every stubbed member exists in Stata's real API with the same signature."
}
display as text "{hline 100}"
log close stubcheck
display as text "Log written to: `c(pwd)'/check_stubs.log"
