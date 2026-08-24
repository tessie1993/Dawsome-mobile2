package com.example.ui.theme.earth

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Composition Locals for Earth.Design Theme.
 */
val LocalEarthColors = staticCompositionLocalOf { EarthColors() }
val LocalEarthTypography = staticCompositionLocalOf { EarthTypography() }

private val DarkColorScheme = darkColorScheme(
    primary = EarthColorTokens.EarthAmber,
    secondary = EarthColorTokens.NatureMossSage,
    tertiary = EarthColorTokens.AutumnTerracotta,
    background = EarthColorTokens.BgObsidianDeep,
    surface = EarthColorTokens.GlassEspresso,
    onPrimary = EarthColorTokens.BgObsidianDeep,
    onSecondary = EarthColorTokens.TextPrimary,
    onBackground = EarthColorTokens.TextPrimary,
    onSurface = EarthColorTokens.TextPrimary
)

/**
 * Master Earth.Design Theme Provider for Jetpack Compose.
 */
@Composable
fun EarthTheme(
    colors: EarthColors = EarthColors(),
    typography: EarthTypography = EarthTypography(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalEarthColors provides colors,
        LocalEarthTypography provides typography
    ) {
        MaterialTheme(
            colorScheme = DarkColorScheme,
            content = content
        )
    }
}

/**
 * Accessor object for EarthTheme tokens in Compose UI.
 */
object EarthTheme {
    val colors: EarthColors
        @Composable
        @ReadOnlyComposable
        get() = LocalEarthColors.current

    val typography: EarthTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalEarthTypography.current
}
