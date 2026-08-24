# Earth.Design — Pro-Audio UI Component Suite

> **Comprehensive specifications for every interactive control, encoder, fader, pad, clip, and canvas in the Earth.Design system.**

---

## Component Index

1. [Rotary Encoders & Modulation Knobs](#1-rotary-encoders--modulation-knobs)
2. [Transport & Global Header Controls](#2-transport--global-header-controls)
3. [Channel Faders, Toggles & Level Meters](#3-channel-faders-toggles--level-meters)
4. [Clip Launcher Matrix](#4-clip-launcher-matrix)
5. [Track Headers & Arranger Canvas](#5-track-headers--arranger-canvas)
6. [Modular DSP Device Containers](#6-modular-dsp-device-containers)
7. [Velocity Drum Pads & Step Sequencer](#7-velocity-drum-pads--step-sequencer)

---

## 1. Rotary Encoders & Modulation Knobs

![Encoders Sheet](assets/earth_glass_knobs_controls_sheet_1787594229817.jpg)

### 1.1 Macro Cutoff Knob
- **Dimensions**: `48dp × 48dp` (Touch target `56dp × 56dp`).
- **Body**: Convex dark crystal glass dial with subtle radial gradient (`#1F1C18` to `#100E0D`) and 1px metallic crystal rim (`rgba(255, 255, 255, 0.15)`).
- **Indicator**:
  - Unipolar Amber LED arc (`#FF7600`) tracing clockwise from 7 o'clock to current value.
  - Luminous dot indicator (`#FFA24D`) at the active rotational angle.
- **Modulation Depth Ring**: Outer secondary crystal ring displaying automated or LFO-driven modulation range in amber/terracotta.

### 1.2 Bi-Directional Pan Knob
- **Dimensions**: `36dp × 36dp`.
- **Center-Detent**: 12 o'clock center resting position ($0\text{ Pan}$).
- **Dual-Color Arc**: Left pan turns Emerald Green (`#2E7D4E`), Right pan turns Amber (`#FF7600`).
- **Interaction**: Double-tap resets instantly to exact center.

### 1.3 Micro-Encoder Matrix
- **Dimensions**: `24dp × 24dp` per encoder.
- **Use Case**: Compact send dials ($A/B$), compressor attack/release, EQ Q-factors.
- **Design**: Thin 2px glowing perimeter ring with numerical readout popup on touch.

---

## 2. Transport & Global Header Controls

![Buttons & Faders Sheet](assets/earth_glass_buttons_faders_sheet_1787594246978.jpg)

### 2.1 Transport Button Group
- **Play Button**:
  - `36dp × 36dp` rounded square (`CornerRadius 6dp`).
  - Active state: Radiant Amber Fill (`#FF7600`) with glowing play triangle icon (`#FFFFFF`).
  - Idle state: Dark crystal glass with amber icon.
- **Stop Button**: Dark crystal glass with crisp white square icon (`#D5CEC5`).
- **Record Button**: Dark crystal glass with glowing Red circle (`#DC2626`). Flashes pulse during count-in and active recording.
- **Loop Toggle**: Amber outline when engaged with bidirectional loop arrows.
- **Metronome**: Icon toggle with pulsing flash on downbeats.

### 2.2 Time & Tempo Readout
- **Container**: `120dp × 32dp` frosted glass pill with 1px border.
- **BPM Field**: Interactive touch-scrub field (`110.00 BPM`) with fine step adjustments ($\pm 0.1$).
- **Timecode Display**: `00:00:15:22` (Bars.Beats.Subdivisions.Ticks) in crisp JetBrains Mono.

---

## 3. Channel Faders, Toggles & Level Meters

### 3.1 Precision Crystal Volume Fader
- **Fader Groove**: 2dp recessed vertical track in dark espresso glass.
- **Fader Cap**: `28dp × 16dp` faceted metallic crystal slider cap with horizontal amber center indicator line.
- **Value Popup**: Floating tooltip displaying exact dB value (e.g. `-2.3 dB`) during touch drag.
- **Scale**: Logarithmic pro-audio response ($-\infty\text{ dB}$ to $+6.0\text{ dB}$ with $0\text{ dB}$ unity detent).

### 3.2 Solo, Mute, Record Arm Toggles
- **Solo (S)**: Rounded pill button. When active: Amber background (`#FF7600`), dark text.
- **Mute (M)**: Rounded pill button. When active: Terracotta background (`#C85A32`), white text.
- **Record Arm (A)**: Rounded pill button with red dot. When active: Crimson background (`#DC2626`).

### 3.3 Multi-Segment Stereo LED Level Meters
- **Resolution**: 24-segment stereo vertical bar graph.
- **Gradient Zones**:
  - $-\infty$ to $-12\text{ dB}$: Forest Emerald (`#2E7D4E`)
  - $-12\text{ dB}$ to $-3\text{ dB}$: Warm Amber (`#FF7600`)
  - $-3\text{ dB}$ to $0\text{ dB}$: Terracotta (`#C85A32`)
  - $> 0\text{ dB}$: Red Clip Latch (`#EF4444`, stays lit for 2.0s).

---

## 4. Clip Launcher Matrix

![Clips & Pads Sheet](assets/earth_glass_clips_pads_sheet_1787594262166.jpg)

### 4.1 Rectangular Crystal Clip Tiles
- **Dimensions**: Compact `44dp × 24dp` (Desktop/Tablet) or `56dp × 32dp` (Mobile).
- **Color Coding**: Track-inherited earth tone (Moss, Amber, Terracotta, Ochre).
- **States**:
  - **Idle**: Translucent frosted glass tint (30% opacity) with centered play triangle.
  - **Queued**: Blinking corner quantization indicator (synced to project tempo).
  - **Playing**: Radiant 1px outer amber glow with animated progress fill bar across the tile bottom.
  - **Empty Slot**: Dark recessed glass outline with subtle plus icon on hover.

---

## 5. Track Headers & Arranger Canvas

### 5.1 Compact Track Headers
- **Layout**: 1px glass card containing:
  - Left Color Accent Bar (`4dp` width).
  - Track Name (`12sp Medium`).
  - Solo / Mute / Arm micro-toggles.
  - Mini Horizontal Volume Slider with embedded dB meter.

### 5.2 Arranger Waveform & Automation Canvas
- **Waveform Rendering**: Real-time high-resolution audio waveform with distinct transient spikes rendered in track accent color.
- **MIDI Note Blocks**: Crisp rounded bars with velocity-proportional transparency.
- **Automation Lanes**: Thin 1.5px multi-node bezier curve with interactive circular drag handles and smooth tension adjustment.

---

## 6. Modular DSP Device Containers

- **Header Bar**: Device Icon, Device Name (e.g. `Polymer Synth`, `Parametric EQ+`), Preset Selector, Power Toggle, Fold Arrow, Close Button.
- **Device Body**: Modular container with 1px crystal border and custom DSP interactive views:
  - **3D Wavetable Viewport**: Real-time holographic mesh visualizing active wavetable position and morphing.
  - **ADSR Graph**: Multi-stage curve with draggable Attack, Decay, Sustain, Release nodes.
  - **Parametric EQ+ Canvas**: 8-band interactive frequency curve with real-time FFT spectrum background.

---

## 7. Velocity Drum Pads & Step Sequencer

- **4x4 Drum Matrix**: 16 velocity-sensitive crystal pads with color tags, choke group badges (`1`-`4`), and touch pressure feedback.
- **16-Step Velocity Sequencer**: Dense step trigger sliders with probability percentage bars.
