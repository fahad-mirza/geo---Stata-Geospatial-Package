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
1. Claude delivers the whole repository as a zip; extract it into the local clone (not a
   separate folder), replacing files.
2. Review the changed files in GitHub Desktop, commit, push.
3. GitHub Actions ("ci" workflow) builds and tests on 8 runner/Java combinations and uploads
   the artifact **geo-jar** (geo.jar, sfi-stub.jar, BUILD-INFO.txt) from the Windows x64 / Java 21
   runner.
4. Download the artifact and extract it OUTSIDE the clone (for example Downloads\geo-jar); never
   commit it. In a fresh Stata session, cd to the clone's tests\stata, run the do-file with the
   artifact folder as its argument, and paste the log back. Logs are ignored by Git, so a test
   run leaves nothing to commit.

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
- check_stubs.do on 23 Oct 2026 (StataNow 19.5): all 157 stubbed members match the real API,
  including `SFIException.getErrorCode()` (inferred from the FrameException page, now confirmed).

### Packaging rules (geo-dist/pom.xml)
- Relocation prefix: `org.fahadmirza.geo.shaded`.
- Relocated: Apache Commons, Apache FontBox, SLF4J (all bundled by Stata in libdeps-core.jar),
  JTS, and the units-of-measure libraries (javax.measure, tech.units, si.uom, systems.uom).
  CI fails if Apache Commons, FontBox or SLF4J appear anywhere in geo.jar under their original names.
- Never relocated: DuckDB (`org.duckdb`) and H3 (`com.uber.h3core`), because their native code
  finds Java classes by their original names. CI fails if either appears under the prefix.
- Not relocated by decision (see "Decisions"): GeoTools (`org.geotools`), ImageN
  (`org.eclipse.imagen`), ImageIO-Ext (`it.geosolutions`), and HSQLDB (`org.hsqldb`, the engine
  behind GeoTools' EPSG database). Stata bundles none of them.
- Merged: `META-INF/services/*` (ServicesResourceTransformer, which also relocates the class names
  inside them) and `META-INF/org.eclipse.imagen.registryFile.imagen` and
  `META-INF/registryFile.imagen` (AppendingTransformer), per the GeoTools Building FAQ.
- Removed: signature files `META-INF/*.SF|*.DSA|*.RSA|*.EC`, dependency `module-info.class`, and
  anything under `com/stata/` (second guard; sfi-stub is already excluded by its provided scope).
- CI inspects every geo.jar for these rules ("Check geo.jar contents" step).

### Pinned versions (all checked 23 Oct 2026)
| Item | Version | How it was checked |
| --- | --- | --- |
| DuckDB JDBC driver (`org.duckdb:duckdb_jdbc`) | 1.5.6.0, plus its `windows_arm64` classifier jar | Tag v1.5.6.0 in github.com/duckdb/duckdb-java (28 Sep 2026). 2.0.0.0 was tagged 21 Oct 2026; adopt it once it is two weeks old (from 4 Nov 2026). The main jar bundles native engines for Linux x64/ARM64, macOS universal and Windows x64; Windows ARM64 is a separate classifier jar (scripts/jdbc_maven_deploy.py). |
| GeoTools modules | `gt-geotiff`, `gt-epsg-hsql` (versions from the BOM) | API calls checked against the 35.2 source |
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

### Runtime rules learned in Phase 0
- **Context classloader.** Every javacall entry point that uses a library runs through
  `JavaContext.call(...)`, which sets the thread's context classloader to geo.jar's own loader and
  restores it afterwards. GeoTools, ImageIO and the units library find their plugins through that
  loader; under javacall it is chosen by Stata. `check_dependencies.do` records what Stata sets.
- **DuckDB driver.** Create `new org.duckdb.DuckDBDriver()` directly; do not rely on
  `java.sql.DriverManager`, which may not see drivers inside geo.jar when Stata calls geo.
- **DuckDB native engine.** On first use DuckDB copies `libduckdb_java.so_{os}_{arch}` from the jar
  into Java's temporary folder (`java.io.tmpdir`) and loads it from there. A locked-down machine
  that blocks loading code from the temporary folder would break vector features; the dependency
  check reports the folder used.
- **Return codes.** 198 for bad arguments, 5100 (Stata's own code for a Java exception) for
  failures; see `ReturnCodes.java`.
- **Stata's real constants** (recorded by check_stubs.do, 23 Oct 2026): Data.TYPE_BYTE 1, INT 2,
  LONG 3, FLOAT 4, DOUBLE 5, STR 100, STRL 101, UNKNOWN -1; SFIToolkit.RC_BREAK 1,
  RC_SYNTAX_ERROR 198, RC_GENERAL_EXCEPTION 5100, RC_GENERAL_ERROR 5101.

### CI runners (GitHub docs, "GitHub-hosted runners reference")
windows-latest (x64), windows-11-arm (ARM64), ubuntu-latest (x64), ubuntu-24.04-arm (ARM64),
macos-latest (Apple silicon ARM64), macos-15-intel (x64). Java 21 everywhere (what Stata 19
bundles), plus Java 17 on Windows x64 and Linux x64 (the minimum geo supports). Free and
unlimited for public repositories.

### Decisions
**23 Oct 2026: GeoTools, ImageN and ImageIO-Ext stay unrelocated** (was proposed amendment 1).
ImageN registry files list class names as plain text. The Shade plugin's AppendingTransformer
copies those files byte for byte without applying relocations (checked in the plugin's 3.6.1
source: `processResource` ignores its relocators), so relocating these packages would leave the
registry naming classes that no longer exist. Stata does not bundle these libraries, so round 2's
"Stata's copy wins" problem does not apply; the remaining risk (another Stata package bundling
GeoTools) is covered by task 7's two-jar test. Because GeoTools (LGPL 2.1) is no longer renamed,
its bytecode is only rewritten where it refers to the relocated libraries (JTS, units, Commons);
worth reviewing with the license decision.

**JUnit tests live in each module's `src/test/java`, not in `tests/java`.** That is Maven's
standard location, so tests run automatically on every runner. `tests/` keeps the Stata do-files
(`tests/stata`) and reference outputs (`tests/reference`).

### Proposed amendments (need your decision)
**Roadmap risk "Windows on ARM laptops: no DuckDB engine" can be closed.** DuckDB's Java driver
now publishes a Windows ARM64 native library as a separate classifier jar (duckdb-java
scripts/jdbc_maven_deploy.py, tag v1.5.6.0). geo bundles it from task 3, and the windows-11-arm
CI runner runs the DuckDB tests. If the run is green, the foundation document's verification
entry ("Windows x64 only") and the Phase 1 exit note should be updated in task 14.

**Handover task numbering.** The handover's first opening prompt and its note "If task 5 shows
ZSTD or LERC unsupported" call the real-GeoTIFF test task 5; the task table numbers it task 6 (task 5
is the library-conflict probe). Fix in the roadmap update (task 14).

### Open items noticed while building the skeleton
- No license file yet. A permissive license (Apache 2.0 or MIT) fits the permissive dependencies
  (DuckDB: MIT; JTS: EPL 2.0 / EDL 1.0; H3-Java: Apache 2.0); GeoTools is LGPL 2.1. Decide before
  the first public release, after the World Bank open-source policy check.
- `stata/geo.pkg` does not list geo.jar yet: a release step (later phase) will copy the built jar
  next to geo.ado so `net install` can fetch it.
