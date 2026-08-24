# Repository Census

## Overview
- **App Name**: Modular Synthesizer (Mini DAW Workstation)
- **Framework**: Android / Jetpack Compose / Kotlin Coroutines & Flow / AudioTrack DSP
- **Entry Points**: `MainActivity.kt`, `MainDawScreen.kt`

## Inventory of Author Source Files

| Path / Group | Kind | Purpose | Connected Features | Status | Intended Treatment | Evidence |
|---|---|---|---|---|---|---|
| `synth/SynthesizerEngine.kt` | Source / Audio DSP | Real-time audio engine with dual VCOs, FM, RingMod, Filter, LFO, Reverb, Delay, Distortion, Drum Synthesizers | All audio playback, live synthesis, mixing | Working | Use | AudioTrack real-time PCM loop |
| `synth/Voice.kt` | Source / Audio DSP | Polyphonic voice allocator and per-note envelope generation | Synth keyboard & piano roll | Working | Use | ADSR & pitch calculation |
| `synth/DrumSynthesizer.kt` | Source / Audio DSP | 6 synthesized drum voices (Kick 808, Snare, HiHat Closed/Open, Clap, Tom) | Drum machine sequencer & velocity pads | Working | Use | Procedural drum synthesis |
| `synth/SynthEffects.kt` | Source / Audio DSP | Schroeder reverb, stereo ping-pong tape delay, soft-saturation overdrive | Master FX rack | Working | Use | DSP buffer processing |
| `synth/WavRecorder.kt` | Source / Audio IO | Real-time 44.1kHz 16-bit stereo PCM to WAV recorder & exporter | Master audio recording & playback | Working | Use | File write & Android MediaPlayer |
| `synth/SynthPatch.kt` | Source / Presets | Factory synth presets & project templates serialization | Presets & Demo project songs | Working | Use | 8 factory presets & 3 songs |
| `synth/SynthViewModel.kt` | Source / Architecture | Central state management, sequencer scheduler, transport, VU meter telemetry | Entire UI layer | Working | Use | StateFlows & coroutine timers |
| `ui/MainDawScreen.kt` | UI / Screen | Master DAW frame with transport bar, metronome, BPM tap, panic button, navigation dock | Global app structure | Working | Use | Navigation & transport |
| `ui/screens/SynthWorkspaceScreen.kt` | UI / Screen | Dual-VCO modular synth console, filter sweeps, LFO matrix, ADSR curves, piano keys & wheels | Synthesizer tab | Working | Use | Real-time parameter controls |
| `ui/screens/PianoRollScreen.kt` | UI / Screen | 32-step multi-octave piano roll sequencer for Lead & Bass with generator & transpose | Piano Roll tab | Working | Use | Interactive grid & riff generator |
| `ui/screens/DrumMachineScreen.kt` | UI / Screen | 16-step 6-instrument groovebox + MPC live velocity pads + tuning controls | Drum Machine tab | Working | Use | Step sequencer & pad triggers |
| `ui/screens/MixerScreen.kt` | UI / Screen | 4-channel studio mixing desk (Synth, Bass, Drums, Master) + Master FX rack | Mixer & FX tab | Working | Use | Faders, pans, VU meters, FX dials |
| `ui/screens/ArrangerScreen.kt` | UI / Screen | Multi-track project song loader, 4-bar matrix visualizer, live WAV master recorder | Arranger & Projects tab | Working | Use | Template loader & WAV export |
| `ui/components/Controls.kt` | UI / Components | Custom rotary dials, studio faders, LED VU meters, oscilloscope, wave badges | All screen controls | Working | Use | Hardware-style widgets |
