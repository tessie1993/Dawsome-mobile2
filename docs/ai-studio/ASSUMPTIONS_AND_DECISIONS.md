# Assumptions and Architecture Decisions

## Architectural Decisions

1. **Audio Synthesis Architecture**:
   - Implemented direct high-performance low-latency PCM audio generation using Kotlin Coroutines and Android `AudioTrack` configured for 44.1 kHz, 16-bit stereo.
   - Built procedural synthesis algorithms for all melodic and percussive voices without relying on external sample files, ensuring immediate offline startup, zero external latency, and infinite parametric control.

2. **State Management & Telemetry**:
   - Applied MVVM architecture with `SynthViewModel` acting as the single source of truth for transport timing, step sequencing, preset loading, and live VU meter peak computations.
   - Utilized Kotlin `StateFlow` and Compose state collection to maintain smooth 60fps UI animations without introducing audio thread jitter.

3. **Consolidation**:
   - Legacy exploratory files (`SynthScreen.kt` and `PianoRoll.kt`) were retired and consolidated into dedicated, modular workstation screens (`SynthWorkspaceScreen.kt`, `PianoRollScreen.kt`, `DrumMachineScreen.kt`, `MixerScreen.kt`, `ArrangerScreen.kt`) with unified theme components in `Controls.kt`.
