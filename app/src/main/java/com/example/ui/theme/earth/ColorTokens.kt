package com.example.ui.theme.earth

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Earth.Design Color Tokens — Autumn Hues, Nature Greens & Crystal Glass Palette.
 */
object EarthColorTokens {
    // Base Obsidian & Glass Backgrounds
    val BgObsidianDeep = Color(0xFF0E0D0C)
    val GlassEspresso = Color(0xFF141210)
    val GlassSurface = Color(0xFF1A1815)
    val GlassSurfaceRaised = Color(0xFF23201C)

    // Glass Borders & Rim Highlights
    val GlassBorderSubtle = Color(0x333D352B)
    val GlassBorderHighlight = Color(0x14FFFFFF)   // 8% white per TOKENS.json
    val GlassBorderRimAmber = Color(0x66FF7600)

    // Autumn Hues (Warm Harmonizers, Percussion, Leads & Dynamics)
    val EarthAmber = Color(0xFFFF7600)              // Primary / Play / Selection / Active Clips
    val AutumnMapleAmber = Color(0xFFFFA24D)        // Knob Halos / Peak Arc / Transients
    val AutumnRust = Color(0xFFD96B27)              // Snare / Claps / Transient Audio
    val AutumnTerracotta = Color(0xFFC85A32)        // Bass Synths / Distortion / Mute
    val AutumnChestnut = Color(0xFF7B3F00)          // Sub Bass / Low-End Analog / Drums
    val AutumnHarvestGold = Color(0xFFD4AF37)       // Lead Synths / Master Bus / Vocals / Solo
    val AutumnBurntSienna = Color(0xFF8B4513)       // Sub-groups / Aux Returns
    val AutumnCrimsonMaple = Color(0xFF9B2C2C)      // Record Arm / Clip Thresholds / Overdrive

    // Nature Greens (Acoustic Channels, Space, Modulation & Filters)
    val NatureForestPine = Color(0xFF1E3F20)        // Audio Recordings / Live Mic / Master Ground
    val NatureEmerald = Color(0xFF2E7D4E)           // Acoustic Kits / Hi-Hats / In-Key Scale
    val NatureMossSage = Color(0xFF6B8E23)          // Pads / Reverb/Delay FX / Soundscapes
    val NatureFern = Color(0xFF4F7942)              // Granular Samplers / LFO Generators
    val NatureSageLeaf = Color(0xFF8FBC8F)          // Secondary Sends / Aux FX / Automation
    val NatureOliveGrove = Color(0xFF808000)        // Polyphonic Synths / Chords / FM Operators

    // Semantic Metering Gradients
    val MeterNatureGreen = Color(0xFF38A169)        // -inf to -12 dB (Forest Emerald)
    val MeterAutumnAmber = Color(0xFFD97706)        // -12 dB to -3 dB (Harvest Amber)
    val MeterAutumnRust = Color(0xFFDC2626)         // -3 dB to 0 dB (Crimson Rust)
    val MeterClipRed = Color(0xFFEF4444)            // > +0.1 dBTP (Instant Red Clip)

    // UI State & Text
    val TextPrimary = Color(0xFFEDE8E1)
    val TextSecondary = Color(0xFFA89F91)
    val TextDisabled = Color(0xFF5E574D)
    val IconMuted = Color(0xFF8C8273)
}

@Immutable
data class EarthColors(
    val bgObsidian: Color = EarthColorTokens.BgObsidianDeep,
    val glassBase: Color = EarthColorTokens.GlassEspresso,
    val glassSurface: Color = EarthColorTokens.GlassSurface,
    val primaryAmber: Color = EarthColorTokens.EarthAmber,
    val autumnRust: Color = EarthColorTokens.AutumnRust,
    val autumnTerracotta: Color = EarthColorTokens.AutumnTerracotta,
    val autumnHarvestGold: Color = EarthColorTokens.AutumnHarvestGold,
    val natureForestPine: Color = EarthColorTokens.NatureForestPine,
    val natureEmerald: Color = EarthColorTokens.NatureEmerald,
    val natureMossSage: Color = EarthColorTokens.NatureMossSage,
    val natureOliveGrove: Color = EarthColorTokens.NatureOliveGrove,
    val borderSubtle: Color = EarthColorTokens.GlassBorderSubtle,
    val textPrimary: Color = EarthColorTokens.TextPrimary,
    val textSecondary: Color = EarthColorTokens.TextSecondary
)
