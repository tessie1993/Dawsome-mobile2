# Build Workflow (standing rules)

The user-defined loop this project is built under. Survives context
compaction — reread after any summary. Specs in `docs/spec/` are
READ-ONLY references; `docs/ARCHITECTURE.md` is the living class-diagram
map; `docs/BUILD_LOG.md` is the newest-first hand-off record.

## Per-feature loop

1. **Think + research first.** Before each feature: understand what it is
   and does, then search online DSP codebases / C++ audio literature for
   the best approach, algorithms and libraries.
2. **Build class by class.** Before each class pick the best approach.
   Elegant OOP, Android best practices, high-performance audio quality.
   Features stay cohesive — shared infrastructure over silos.
3. **Reread every class after writing it** to catch mistakes.
4. **Update `docs/ARCHITECTURE.md` after each class/chunk** — affected
   classes only, no full rescans. Run the sweep (both directions +
   duplicate detection: map blocks, relationship lines, same-name source
   declarations). Duplicates: keep the one most accurate copy.
5. **Walk the data flow** after each class/feature to verify correctness.
6. UI: Earth V2 design system (morphic glass); builder decides when each
   feature gets its UI; UI classes go into the map too.
7. **Commit at natural points, regularly.** Before each commit reread the
   full diff for mistakes.

## Review gate (HARD RULE)

Between each commit, a **reviewer agent** — expert-level DSP + Android
application developer, AAA bar — reviews the work:

- The reviewer READS the work only: no compiling, no testing, no builds.
- It gives harsh, specific feedback; the builder uses it to understand
  each mistake and why, and improves the work.
- **Only a PASS from the reviewer allows the commit / continuing.**
  Feedback rounds repeat (same agent, context intact) until pass.

## Audio stack: Oboe

The engine's device I/O is **Google Oboe** (prefab `com.google.oboe:oboe`,
version pinned in `gradle/libs.versions.toml`; linked as `oboe::oboe` in
`cpp/CMakeLists.txt`). `engine/OboeDriver` owns the streams per blueprint
D2/D5: output stream is the clock master (LowLatency, Exclusive with
Shared fallback, float, stereo, device-native rate); input is opened
callback-less at the output's rate and drained non-blocking into the
InputJitterRing; route/rate loss only flags `needsReopen` — the D5
re-prepare sequence runs off-thread.

Rules when touching the driver or anything Oboe-facing:

- Follow Oboe's documented callback discipline: nothing in `onAudioReady`
  may block, allocate, lock, or log; bursts are sub-chunked to
  `kMaxBlock`.
- Every NEW Oboe API the driver starts using MUST be mirrored in
  `host_shims/oboe/Oboe.h` with the REAL Oboe signatures (raw-pointer
  callbacks, chainable builder, ResultWithValue) — the host check is only
  trustworthy while the shim stays API-faithful. The Android build never
  sees the shim.
- Oboe version bumps: check the release notes for callback/API changes,
  update the shim surface, and re-run the host check.

## Verification posture

- No test suites unless the user explicitly asks.
- The engine ships in the APK since the audio-core bring-up: every commit
  must keep `dawcore_hostcheck` compiling clean
  (`cmake --build <hostbuild>` — zero warnings is the bar). This is a
  build-integrity gate, not testing.

## Cadence with the user

- **Pause at each feature commit** so the user can merge on GitHub.
- On merge: restart the branch from `main` (same name
  `claude/codebase-cleanup-architecture-uh76nw`), next feature gets a
  **new PR**.
- At ~30% context remaining: finish the current feature, update the map,
  commit, write the BUILD_LOG hand-off, pause and wait.
