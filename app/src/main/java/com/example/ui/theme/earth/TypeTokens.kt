package com.example.ui.theme.earth

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

/**
 * Earth.Design font families — the pack's mandated faces (TOKENS.json
 * typography.fontFamilies), vendored as variable TTFs in res/font:
 * Outfit (primary, Inter behind it) + JetBrains Mono. Weight instancing
 * uses font variation settings (honored API 26+; API 24/25 fall back to
 * each file's default instance — legible, just single-weight).
 */
object EarthFonts {
    private fun variable(res: Int, weight: FontWeight) = Font(
        resId = res,
        weight = weight,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight))
    )

    val Primary = FontFamily(
        variable(R.font.outfit_variable, FontWeight.Normal),
        variable(R.font.outfit_variable, FontWeight.Medium),
        variable(R.font.outfit_variable, FontWeight.SemiBold),
        variable(R.font.outfit_variable, FontWeight.Bold),
        variable(R.font.inter_variable, FontWeight.Light)
    )

    val Mono = FontFamily(
        variable(R.font.jetbrains_mono_variable, FontWeight.Normal),
        variable(R.font.jetbrains_mono_variable, FontWeight.SemiBold),
        variable(R.font.jetbrains_mono_variable, FontWeight.Bold)
    )
}

/**
 * Earth.Design Typography Scale — Pro-Audio Precision & High Legibility.
 */
@Immutable
data class EarthTypography(
    val displayTime: TextStyle = TextStyle(
        fontFamily = EarthFonts.Mono,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.5.sp,
        color = EarthColorTokens.TextPrimary
    ),
    val bpmValue: TextStyle = TextStyle(
        fontFamily = EarthFonts.Mono,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        color = EarthColorTokens.EarthAmber
    ),
    val trackTitle: TextStyle = TextStyle(
        fontFamily = EarthFonts.Primary,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 14.sp,
        color = EarthColorTokens.TextPrimary
    ),
    val sectionLabel: TextStyle = TextStyle(
        fontFamily = EarthFonts.Primary,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 13.sp,
        letterSpacing = 0.8.sp,
        color = EarthColorTokens.TextSecondary
    ),
    val paramLabel: TextStyle = TextStyle(
        fontFamily = EarthFonts.Primary,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        lineHeight = 12.sp,
        color = EarthColorTokens.TextSecondary
    ),
    val paramValue: TextStyle = TextStyle(
        fontFamily = EarthFonts.Mono,
        fontWeight = FontWeight.Normal,
        fontSize = 9.sp,
        lineHeight = 11.sp,
        color = EarthColorTokens.TextPrimary
    ),
    val microBadge: TextStyle = TextStyle(
        fontFamily = EarthFonts.Primary,
        fontWeight = FontWeight.Bold,
        fontSize = 8.sp,
        lineHeight = 10.sp,
        color = EarthColorTokens.TextPrimary
    )
)
