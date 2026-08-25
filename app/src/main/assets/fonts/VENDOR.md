# Vendored fonts (Earth.Design V2 mandated faces)

Provenance manifest, per the project's vendoring standard
(third_party/dr_libs/VENDOR.md precedent; review cycle-2 finding).

- Upstream: https://github.com/google/fonts (`main`), vendored 2026-08-25
  via raw.githubusercontent.com, UNMODIFIED variable TTFs:
  - `res/font/outfit_variable.ttf`  — ofl/outfit/Outfit[wght].ttf
    (c) 2021 The Outfit Project Authors
  - `res/font/inter_variable.ttf`   — ofl/inter/Inter[opsz,wght].ttf
    (c) 2020 The Inter Project Authors
  - `res/font/jetbrains_mono_variable.ttf` — ofl/jetbrainsmono/
    JetBrainsMono[wght].ttf, (c) 2020 The JetBrains Mono Project Authors
- License: SIL Open Font License 1.1 for all three. The OFL requires the
  copyright notice + license text to accompany distribution — the
  `OFL-*.txt` files in THIS directory ship inside the APK (assets/) to
  satisfy that for release builds.
- Consumed by: `ui/theme/earth/TypeTokens.kt` (EarthFonts), mandated by
  `docs/spec/Earth.Design/TOKENS.json` typography.fontFamilies.
- Update procedure: re-download the TTF + its OFL.txt together, refresh
  this manifest's date.
