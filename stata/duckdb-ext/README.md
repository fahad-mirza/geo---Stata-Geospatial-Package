# DuckDB extensions (placeholder)

DuckDB's `spatial` and `httpfs` extensions will live here, one file per platform, so geo can load
them from a local path without internet access (DuckDB docs: "Advanced installation methods").

Nothing is here yet. Phase 0 task 3 decides whether the extension files are committed here or
downloaded by a GitHub Actions workflow and attached to releases; they are large binaries.
DuckDB ships native engines for Windows x64, Linux x64 and ARM64, and macOS only, so
Windows on ARM gets no vector features (Phase 1 detects and reports this).
