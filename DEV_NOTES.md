# DEV_NOTES: rules for developing geo

This file is versioned with the code so the rules travel with every fork. Part 1 is a copy of the
Claude Project instructions. Part 2 records repository-specific decisions, pinned versions and
proposed amendments. The source of truth for test results and the roadmap remains the document
"Stata Geospatial Package: Foundation Tests and Roadmap".

---

## Part 1. Project instructions (copy)

### What this is
A Stata package that turns rasters (GeoTIFF, later NetCDF/GRIB/Zarr) and vector boundaries into
analysis variables inside Stata: zonal statistics, point and buffer extraction, spatial joins.
Java engine called through the Stata Function Interface (com.stata.sfi). Audience: development
economists and the wider Stata community.

### Locked decisions (do not reopen without new evidence)
- **Engine:** Java 17 base code, optional Java 21 classes via a multi-release jar.
- **Libraries:** DuckDB spatial (JDBC) for vector work; GeoTools 34+ for raster reading; JTS for
  geometry; netCDF-Java later for climate data.
- **Commands:** one Stata dispatcher command `geo` with subcommands (geo read, geo zonal, ...).
- **Parallelism:** dedicated thread pool; `threads()` defaults to all logical cores; work split
  into fixed-order chunks so results are identical across thread counts.
- **Writing to Stata:** create variables BEFORE setting observations; computed statistics stored
  as double; Java validates ranges and UTF-8 byte widths before every storeNumFast/storeStrfFast
  call (these fail silently); truncate only at character boundaries.
- **Missing values:** .a no valid pixels; .b outside raster; .c invalid/empty geometry.
- **Geometry:** WKB in a binary strL column `_geometry`; CRS stored as a dataset characteristic;
  Java-side cache keyed by datasignature for very large layers.
- **Rasters:** 512x512 processing tiles; read only tiles intersecting the polygons; decoded-tile
  cache ~1 GB at the default 4 GB heap; cell-area correction for latitude/longitude rasters.
- **Packaging:** relocate (shade) pure-Java dependencies under a package-specific prefix; NEVER
  relocate JNI-based libraries (DuckDB, H3); merge META-INF/services and ImageN registry files with
  the Maven Shade plugin's transformers.
- **Validation:** identical results across runs and thread counts; within relative tolerance 1e-10
  of exactextract / terra / geopandas reference outputs.
- **Platforms:** engine has no SFI dependency (all com.stata.sfi calls in a thin outer layer) so
  engine tests run on Windows, macOS and Linux via GitHub Actions; the Stata-facing layer is
  tested on Windows.

### Developer environment (hard constraints)
- No Claude Code and no local installs: the work machine is locked down.
- Local tools: Stata 19.5 MP (bundled JDK 21, which includes javac), GitHub Desktop, a web
  browser, a text editor. Nothing else.
- All builds and non-Stata tests run on GitHub Actions: Maven, JDK 17/21, R, Python, GDAL.
  Build artifacts (geo.jar) are downloaded from the Actions run page.
- StataCorp's sfi-api.jar is never committed. CI compiles against a small compile-only stub module
  (sfi-stub) with matching signatures; a Stata check confirms the stubs match the real API.
- Repository changes are delivered as a zip of the whole repository snapshot, extracted over the
  local clone, then committed and pushed with GitHub Desktop, with a list of changed files.
- No terminal commands: any step that needs one goes into a GitHub Actions workflow or a Stata
  do-file.

### How to work
- Explain shorthand, codes and jargon in plain terms every time; never refer to something only by
  a code or label.
- Verify claims against official documentation (Stata Java API, GeoTools, DuckDB) and cite it; say
  clearly when something is unverified.
- Prefer full, runnable code blocks over partial edits.
- When delivering a code package or kit, provide the full zip of the whole kit, including the test
  do-file.
- Any Stata-side check is a self-contained do-file that logs to a file and prints a clear verdict
  per check.
- The roadmap phases and exit criteria are the plan of record; any change is flagged as a proposed
  amendment with its reason.

---

## Part 2. Repository notes

### The working loop
1. Claude delivers the whole repository as a zip; extract it over the local clone.
2. Review the changed files in GitHub Desktop, commit, push.
3. GitHub Actions ("ci" workflow) builds and tests on 8 runner/Java combinations and uploads
   the artifact **geo-jar** (geo.jar, sfi-stub.jar, BUILD-INFO.txt) from the Windows x64 / Java 21
   runner.
4. Download the artifact, run the relevant do-file in a fresh Stata session, paste the log back.

A jar stays loaded for the whole Stata session: always restart Stata after replacing geo.jar.

### Module rules
| Module | May depend on | May use com.stata.sfi? |
| --- | --- | --- |
| sfi-stub | nothing | it *is* the stand-in |
| geo-core | nothing | no |
| geo-io | geo-core (+ GeoTools, DuckDB from task 3) | no |
| geo-analysis | geo-core, geo-io | no |
| geo-stata | all of the above; sfi-stub as *provided* | **yes, the only one** |
| geo-dist | all engine modules | no code; builds geo.jar |

### sfi-stub rules
- Signatures are copied from the official Stata 19 Java API pages
  (https://www.stata.com/java/api19/com/stata/sfi/package-summary.html), checked 23 Oct 2026.
  Each method carries Stata's thread-safety note as a comment ("thread-safe" = Stata's @ThreadSafe,
  callable from worker threads; "synchronized" = Stata's @Synchronized, one thread at a time).
- **No constants in stubs.** Java copies the value of a compile-time constant (such as
  `Data.TYPE_DOUBLE`) into the calling class, so a wrong stub value would ship silently inside
  geo.jar. `StubRulesTest` enforces this in CI. `check_stubs.do` prints the real values.
- Add a member to a stub only when geo-stata needs it, then rerun `tests/stata/check_stubs.do`.
- SFIToolkit is stubbed partially (String-based display methods and utilities); the DisplayMode
  and Object overloads are left out until needed.
- `SFIException.getErrorCode()` and its constructor are inferred from the FrameException page and
  are unverified until check_stubs.do runs.

### Packaging rules (geo-dist/pom.xml)
- Relocation prefix: `org.fahadmirza.geo.shaded`.
- Relocated now: Apache Commons, Apache FontBox, SLF4J (all bundled by Stata in
  libdeps-core.jar), JTS, and the units-of-measure libraries (javax.measure, tech.units, si.uom,
  systems.uom). With no third-party library bundled yet, these rename nothing until task 3.
- Never relocated: DuckDB (`org.duckdb`) and H3 (`com.uber.h3core`), because their native code
  finds Java classes by their original names. CI fails if either appears under the prefix.
- Merged: `META-INF/services/*` (ServicesResourceTransformer, which also relocates the class names
  inside them) and `META-INF/org.eclipse.imagen.registryFile.imagen` and
  `META-INF/registryFile.imagen` (AppendingTransformer), per the GeoTools Building FAQ.
- Removed: signature files `META-INF/*.SF|*.DSA|*.RSA|*.EC`, dependency `module-info.class`, and
  anything under `com/stata/` (second guard; sfi-stub is already excluded by its provided scope).
- CI inspects every geo.jar for these rules ("Check geo.jar contents" step).

### Pinned versions (all checked 23 Oct 2026)
| Item | Version | How it was checked |
| --- | --- | --- |
| GeoTools BOM (`gt-bom`) | 35.2 | Latest tagged stable release in github.com/geotools/geotools; used by GeoServer Cloud 3.0.2 and MapFish Print 2.5.2 releases (Oct 2026). Docs "latest" is 36.x, which has no stable tag yet. |
| Maven | 3.9.11 (via Maven Wrapper 3.3.4) | Apache tags; wrapper scripts copied from github.com/apache/maven-wrapper tag maven-wrapper-3.3.4 |
| maven-shade-plugin | 3.6.1 | Version used in the GeoTools Building FAQ |
| maven-compiler-plugin | 3.14.1 | Apache tag |
| maven-surefire-plugin | 3.6.0 | Apache tag |
| maven-jar-plugin | 3.4.2 | Apache tag |
| maven-enforcer-plugin | 3.6.2 | Apache tag |
| JUnit | 5.14.4 | junit-team tag r5.14.4 |
| actions/checkout | v6.1.0 | GitHub tag |
| actions/setup-java | v6.0.1 (Azul Zulu, as bundled with Stata) | GitHub tag, Sep 2026 |
| actions/upload-artifact | v7.0.1 | GitHub tag, Apr 2026 |

### CI runners (GitHub docs, "GitHub-hosted runners reference")
windows-latest (x64), windows-11-arm (ARM64), ubuntu-latest (x64), ubuntu-24.04-arm (ARM64),
macos-latest (Apple silicon ARM64), macos-15-intel (x64). Java 21 everywhere (what Stata 19
bundles), plus Java 17 on Windows x64 and Linux x64 (the minimum geo supports). Free and
unlimited for public repositories.

### Proposed amendments (need your decision)

**1. GeoTools-family relocation is deferred to task 3.**
*Change:* do not relocate `org.geotools`, `org.eclipse.imagen` (ImageN) and `it.geosolutions`
(ImageIO-Ext, GeoTools' TIFF reader) until task 3 proves it works.
*Reason:* ImageN registry files list class names as plain text. The Shade plugin's
AppendingTransformer copies those files byte for byte without applying relocations (checked in the
plugin's 3.6.1 source: `processResource` ignores its relocators), so relocating these packages
would leave the registry naming classes that no longer exist, and raster operations would fail to
register. Stata does not bundle GeoTools, so round 2's "Stata's copy wins" problem does not apply
to them; the remaining risk (another Stata package bundling GeoTools) is covered by task 7's
two-jar test. Options for task 3: (a) keep them unrelocated, or (b) write a small custom transformer
that rewrites class names inside the registry files. Separately, GeoTools is LGPL-licensed;
relocation rewrites its bytecode, which may add obligations when redistributing (not legal advice;
worth checking together with the World Bank open-source policy check).

**2. JUnit tests live in each module's `src/test/java`, not in `tests/java`.**
*Reason:* that is Maven's standard location, so tests run automatically on every runner without
extra build configuration. `tests/` keeps the Stata do-files (`tests/stata`) and reference
outputs (`tests/reference`).

### Open items noticed while building the skeleton
- No license file yet. A permissive license (Apache 2.0 or MIT) fits the permissive dependencies
  (DuckDB: MIT; JTS: EPL 2.0 / EDL 1.0; H3-Java: Apache 2.0); GeoTools is LGPL 2.1. Decide before
  the first public release, after the World Bank open-source policy check.
- `stata/geo.pkg` does not list geo.jar yet: a release step (later phase) will copy the built jar
  next to geo.ado so `net install` can fetch it.
