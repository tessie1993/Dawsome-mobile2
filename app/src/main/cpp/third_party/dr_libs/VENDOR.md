# Vendored: dr_libs

Provenance manifest (review requirement: vendored code carries its origin
so upstream fixes/CVEs can be diffed).

- Upstream: https://github.com/mackron/dr_libs (`master`; upstream
  publishes no release tags - versions are embedded per header)
- Vendored: 2026-08-25, via raw.githubusercontent.com, unmodified
- Files and embedded versions:
  - `dr_wav.h`  — v0.14.6 (in-development header; version banner "TBD")
  - `dr_flac.h` — v0.13.4 (banner "TBD")
  - `dr_mp3.h`  — v0.7.4  (banner "TBD")
- License: dual "public domain (unlicense) OR MIT No Attribution" - full
  texts embedded at the end of each header. Blueprint §13 records the
  license posture.
- Local modifications: NONE. Update procedure: re-download all three
  headers from upstream master in one change, update this manifest's date
  and versions, re-run the host check.
- Consumed by: `media/AudioFileDecoder.cpp` (the single
  `DR_*_IMPLEMENTATION` translation unit).
