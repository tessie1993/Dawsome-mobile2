package com.example.ui.theme.earth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Earth.Design Crystal Glass Modifiers & Elevation Tokens.
 * Exclusively enforces the Earth-Tones Glassmorphism visual standard.
 */
@Immutable
data class GlassElevation(
    val blurRadius: Dp,
    val bgOpacity: Float,
    val borderBrush: Brush,
    val shadowElevation: Dp = 0.dp
)

object EarthGlassTokens {
    val Level1Dock = GlassElevation(
        blurRadius = 12.dp,
        bgOpacity = 0.80f,
        borderBrush = Brush.verticalGradient(
            colors = listOf(Color(0x33FF7600), Color(0x0A3D352B))
        ),
        shadowElevation = 4.dp
    )

    val Level2Panel = GlassElevation(
        blurRadius = 16.dp,
        bgOpacity = 0.75f,
        borderBrush = Brush.verticalGradient(
            colors = listOf(Color(0x333D352B), Color(0x1A141210))
        ),
        shadowElevation = 6.dp
    )

    val Level3Device = GlassElevation(
        blurRadius = 20.dp,
        bgOpacity = 0.82f,
        borderBrush = Brush.verticalGradient(
            colors = listOf(Color(0x66FF7600), Color(0x223D352B))
        ),
        shadowElevation = 8.dp
    )

    val Level4Modal = GlassElevation(
        blurRadius = 28.dp,
        bgOpacity = 0.90f,
        borderBrush = Brush.verticalGradient(
            colors = listOf(Color(0x80FF7600), Color(0x33FF9E40))
        ),
        shadowElevation = 16.dp
    )
}

/**
 * Base modifier to apply authentic Earth-Tones Crystal Glassmorphism.
 */
fun Modifier.earthGlass(
    elevation: GlassElevation = EarthGlassTokens.Level2Panel,
    shape: Shape = RoundedCornerShape(6.dp),
    baseColor: Color = EarthColorTokens.GlassEspresso,
    borderColor: Color? = null
): Modifier = this
    .shadow(elevation = elevation.shadowElevation, shape = shape, clip = false)
    .clip(shape)
    .background(baseColor.copy(alpha = elevation.bgOpacity))
    .border(
        width = 1.dp,
        brush = borderColor?.let { Brush.verticalGradient(listOf(it, it)) } ?: elevation.borderBrush,
        shape = shape
    )

/**
 * Earth Amber Glow Glass (Active playing clip, selected track, primary transport).
 */
fun Modifier.amberGlass(
    shape: Shape = RoundedCornerShape(4.dp),
    glowOpacity: Float = 0.40f
): Modifier = this.earthGlass(
    elevation = EarthGlassTokens.Level3Device,
    shape = shape,
    baseColor = EarthColorTokens.GlassSurface,
    borderColor = EarthColorTokens.EarthAmber.copy(alpha = glowOpacity)
)

/**
 * Earth Terracotta Glass (Snare, bass, mute, aggressive dynamics).
 */
fun Modifier.terracottaGlass(
    shape: Shape = RoundedCornerShape(4.dp)
): Modifier = this.earthGlass(
    elevation = EarthGlassTokens.Level2Panel,
    shape = shape,
    baseColor = EarthColorTokens.AutumnTerracotta.copy(alpha = 0.25f),
    borderColor = EarthColorTokens.AutumnTerracotta.copy(alpha = 0.50f)
)

/**
 * Earth Forest & Moss Sage Glass (Audio tracks, acoustic channels, reverb/space).
 */
fun Modifier.mossGlass(
    shape: Shape = RoundedCornerShape(4.dp)
): Modifier = this.earthGlass(
    elevation = EarthGlassTokens.Level2Panel,
    shape = shape,
    baseColor = EarthColorTokens.NatureMossSage.copy(alpha = 0.22f),
    borderColor = EarthColorTokens.NatureMossSage.copy(alpha = 0.45f)
)

/**
 * Earth Ochre Gold Glass (Lead synths, master bus, vocal track).
 */
fun Modifier.ochreGlass(
    shape: Shape = RoundedCornerShape(4.dp)
): Modifier = this.earthGlass(
    elevation = EarthGlassTokens.Level2Panel,
    shape = shape,
    baseColor = EarthColorTokens.AutumnHarvestGold.copy(alpha = 0.22f),
    borderColor = EarthColorTokens.AutumnHarvestGold.copy(alpha = 0.45f)
)
