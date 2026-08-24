# Earth.Design — Foundations & Design Tokens

> **Mandatory Standard**: Every screen, component, dialog, and control in this DAW must exclusively use the **Earth-Tones Crystal Glassmorphism** design system. Non-glass surfaces, flat un-layered blocks, or discordant neon palettes outside the earth-tone spectrum are strictly prohibited.

---

## 1. Color Palette Matrix

The Earth.Design palette uses warm, organic earth tones on dark charcoal espresso glass backgrounds.

### 1.1 Base Glass & Surface Palette

| Token Name | Hex Code | Compose Value | Description / Usage |
| :--- | :--- | :--- | :--- |
| `BgObsidianDeep` | `#0E0D0C` | `Color(0xFF0E0D0C)` | App root viewport background |
| `GlassEspresso` | `#141210` | `Color(0xFF141210)` | Base glass container background (80% opacity) |
| `GlassSurface` | `#1A1815` | `Color(0xFF1A1815)` | Interactive glass card background (75% opacity) |
| `GlassSurfaceRaised` | `#23201C` | `Color(0xFF23201C)` | Popups, modals, dropdown menus (85% opacity) |
| `GlassBorderSubtle` | `#3D352B` | `Color(0x33FFFFFF)` | 1px hairline border for idle panels |
| `GlassBorderRim` | `#FF9E40` | `Color(0x4DFF9E40)` | Active container rim glow border |

### 1.2 Signature Earth Tones, Autumn Hues & Nature Greens

The palette is rooted in rich **Autumn foliage** (warm amber, burnt maple, rust, chestnut, harvest gold) harmonized with **Organic Nature Greens** (deep forest pine, moss sage, fern, olive grove) over dark espresso crystal glass.

#### A. Autumn Palette (Warm Harmonizers, Percussion, Leads & Dynamics)
| Token Name | Hex Code | Role / Instrument Association |
| :--- | :--- | :--- |
| `EarthAmber` (Primary) | `#FF7600` | Transport Play, Active Selection, Primary Encoders, Active Clips |
| `AutumnMapleAmber` | `#FFA24D` | Knob Indicator Arcs, Peak Glows, Transient Highlights |
| `AutumnRust` | `#D96B27` | Snare, Claps, Transient Audio, Saturation Satellites |
| `AutumnTerracotta` | `#C85A32` | Bass Synths, Distortion Modules, Mute Active States |
| `AutumnChestnut` | `#7B3F00` | Sub-bass, Low-end analog saturators, Acoustic drums |
| `AutumnHarvestGold` | `#D4AF37` | Lead synths, Master bus, Vocal leads, Solo Active States |
| `AutumnBurntSienna` | `#8B4513` | Sub-groups, Utility buses, Aux returns |
| `AutumnCrimsonMaple` | `#9B2C2C` | Record Arm, Clip Thresholds, Overdrive units |

#### B. Nature Greens Palette (Acoustic Channels, Space, Modulation & Filters)
| Token Name | Hex Code | Role / Instrument Association |
| :--- | :--- | :--- |
| `NatureForestPine` | `#1E3F20` | Audio Recordings, Live microphone inputs, Master ground |
| `NatureEmerald` | `#2E7D4E` | Acoustic Kits, Hi-Hats, In-Key Scale notes, Safe Meter range |
| `NatureMossSage` | `#6B8E23` | Pads, Reverb/Delay FX, Ambient soundscapes, Spatial modulators |
| `NatureFern` | `#4F7942` | Granular samplers, Waveform transients, LFO generators |
| `NatureSageLeaf` | `#8FBC8F` | Secondary sends, Auxiliary effects, Automation nodes |
| `NatureOliveGrove` | `#808000` | Polyphonic synths, Chords, FM synthesis operators |

### 1.3 Semantic & Metering Gradients

| Meter Range | Color Token | Hex Code | Threshold (dBFS) |
| :--- | :--- | :--- | :--- |
| **Safe / Acoustic Zone** | `MeterNatureGreen` | `#38A169` | $-\infty$ to $-12\text{ dB}$ (Forest Emerald) |
| **Nominal / Warm Zone** | `MeterAutumnAmber` | `#D97706` | $-12\text{ dB}$ to $-3\text{ dB}$ (Harvest Amber) |
| **Peak / Rust Warning** | `MeterAutumnRust` | `#DC2626` | $-3\text{ dB}$ to $0\text{ dB}$ (Crimson Rust) |
| **True-Peak Clip** | `MeterClipRed` | `#EF4444` | $> +0.1\text{ dBTP}$ (Instant red latch) |

---

## 2. Crystal Glassmorphism Specification

Every panel is treated as a physical crystal-glass pane with frosted blur and light refraction.

```
┌────────────────────────────────────────────────────────┐
│ 1px Inner Highlight (rgba(255, 255, 255, 0.12))        │
│ ┌────────────────────────────────────────────────────┐ │
│ │                                                    │ │
│ │   Frosted Backdrop: Blur 16dp - 24dp               │ │
│ │   Base Tint: #141210 @ 78% Opacity                 │ │
│ │   Content Layer (Dense Pro Controls)               │ │
│ │                                                    │ │
│ └────────────────────────────────────────────────────┘ │
│ 1px Outer Border (rgba(255, 118, 0, 0.20) / #3D352B)   │
└────────────────────────────────────────────────────────┘
  Subtle Drop Shadow: Blur 12dp, Offset (0, 4dp), Color #000000 @ 40%
```

### Elevation Levels

| Level | Blur Radius | Opacity | Border Token | Usage |
| :--- | :--- | :--- | :--- | :--- |
| **Level 0 (Flat)** | `0dp` | `100%` | None | Base workspace canvas |
| **Level 1 (Dock)** | `12dp` | `80%` | `GlassBorderSubtle` | Top transport, bottom footer bar |
| **Level 2 (Panel)** | `16dp` | `75%` | `GlassBorderSubtle` | Track headers, Arranger timeline |
| **Level 3 (Device)** | `20dp` | `82%` | `GlassBorderRim` (on select) | Modular Synth units, EQ+, Mixer strips |
| **Level 4 (Modal)** | `28dp` | `90%` | `GlassBorderRim` | Scale/Key dialogs, Preset browser |

---

## 3. Typography Hierarchy

The typographic system pairs **Outfit / Inter** for modern legibility with **JetBrains Mono** for numerical audio parameters.

| Style Role | Font Family | Size | Line Height | Weight | Usage |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `DisplayTime` | JetBrains Mono | `16sp` | `20sp` | Bold (700) | Master Timecode (`01:03:15:00`) |
| `BpmValue` | JetBrains Mono | `14sp` | `18sp` | SemiBold (600) | Tempo counter (`110.00 BPM`) |
| `TrackHeaderTitle`| Outfit / Inter | `12sp` | `14sp` | Medium (500) | Track names (`Kick`, `Polymer Synth`) |
| `SectionLabel` | Outfit / Inter | `11sp` | `13sp` | SemiBold (600) | Panel labels (`MODULAR SYNTH LAB`) |
| `ParamLabel` | Outfit / Inter | `10sp` | `12sp` | Regular (400) | Knob names (`Cutoff`, `Resonance`) |
| `ParamValue` | JetBrains Mono | `9sp` | `11sp` | Regular (400) | Precise value readouts (`-6.2 dB`, `1.8kHz`) |
| `MicroBadge` | Outfit / Inter | `8sp` | `10sp` | Bold (700) | Solo/Mute (`S`, `M`), Choke groups (`1`, `2`) |

---

## 4. Spacing & Micro-Grid Density

Pro-audio workflows require maximum density with zero wasted space.

- **Micro-Unit**: `2dp` (Hairline padding, meter gaps)
- **Base Grid**: `4dp` (Button padding, knob margins)
- **Standard Spacing**: `8dp` (Module spacing, header margins)
- **Container Inset**: `12dp` (Panel interior margins)

### Corner Radii

- **Micro Controls (S/M/Arm buttons)**: `2dp`
- **Clip Tiles & Fader Caps**: `4dp`
- **Device Modules & Track Cards**: `6dp`
- **Transport Bar & Modals**: `8dp`
