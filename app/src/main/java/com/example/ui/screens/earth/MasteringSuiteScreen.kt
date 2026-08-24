package com.example.ui.screens.earth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.earth.MacroCutoffKnob
import com.example.ui.components.earth.ParametricEqGraph
import com.example.ui.components.earth.PrecisionCrystalFader
import com.example.ui.components.earth.StereoLedLevelMeter
import com.example.ui.state.MasteringStateHolder
import com.example.ui.theme.earth.EarthColorTokens
import com.example.ui.theme.earth.EarthTheme
import com.example.ui.theme.earth.earthGlass

/**
 * Mastering Suite & FX Grid View (Earth.Design).
 */
@Composable
fun MasteringSuiteScreen(
    masteringStateHolder: MasteringStateHolder,
    modifier: Modifier = Modifier
) {
    val state by masteringStateHolder.state.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EarthColorTokens.BgObsidianDeep)
            .verticalScroll(scrollState)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // --- 1. Top Loudness & Metering Console (EBU R128) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .earthGlass(shape = RoundedCornerShape(6.dp), baseColor = EarthColorTokens.GlassSurface)
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LoudnessMetricBox(label = "INTEGRATED", value = "${state.integratedLufs} LUFS", isHighlight = true)
            LoudnessMetricBox(label = "SHORT-TERM", value = "${state.shortTermLufs} LUFS")
            LoudnessMetricBox(label = "MOMENTARY", value = "${state.momentaryLufs} LUFS")
            LoudnessMetricBox(label = "TRUE-PEAK", value = "${state.truePeakDb} dBTP", isHighlight = true)
            LoudnessMetricBox(label = "DYN RANGE", value = "DR${state.dynamicRange.toInt()}")
            LoudnessMetricBox(label = "PHASE CORR", value = "+${String.format("%.2f", state.stereoCorrelation)}")
        }

        // --- 2. Linear-Phase Parametric EQ+ ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .earthGlass(shape = RoundedCornerShape(6.dp), baseColor = EarthColorTokens.GlassSurface)
                .padding(8.dp)
        ) {
            Text(
                text = "LINEAR-PHASE MASTER EQ",
                style = EarthTheme.typography.sectionLabel,
                color = EarthColorTokens.AutumnHarvestGold
            )
            Spacer(modifier = Modifier.height(6.dp))
            ParametricEqGraph(
                modifier = Modifier.fillMaxWidth(),
                accentColor = EarthColorTokens.AutumnHarvestGold
            )
        }

        // --- 3. Multiband Dynamics & True-Peak Brickwall Limiter ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Multiband Compressor (Left)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .earthGlass(shape = RoundedCornerShape(6.dp), baseColor = EarthColorTokens.GlassSurface)
                    .padding(8.dp)
            ) {
                Text(
                    text = "4-BAND DYNAMICS",
                    style = EarthTheme.typography.sectionLabel,
                    color = EarthColorTokens.NatureEmerald
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    MacroCutoffKnob(value = 0.45f, label = "Low", onValueChange = {})
                    MacroCutoffKnob(value = 0.50f, label = "L-Mid", onValueChange = {})
                    MacroCutoffKnob(value = 0.55f, label = "H-Mid", onValueChange = {})
                    MacroCutoffKnob(value = 0.60f, label = "High", onValueChange = {})
                }
            }

            // Brickwall Limiter & Master Output (Right)
            Column(
                modifier = Modifier
                    .weight(0.9f)
                    .earthGlass(shape = RoundedCornerShape(6.dp), baseColor = EarthColorTokens.GlassSurface)
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "BRICKWALL LIMITER",
                    style = EarthTheme.typography.sectionLabel,
                    color = EarthColorTokens.EarthAmber
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PrecisionCrystalFader(
                        volumeDb = state.masterVolumeDb,
                        height = 130.dp,
                        onVolumeChange = { masteringStateHolder.setMasterVolume(it) }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    StereoLedLevelMeter(
                        levelL = 0.85f,
                        levelR = 0.83f,
                        height = 130.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun LoudnessMetricBox(
    label: String,
    value: String,
    isHighlight: Boolean = false
) {
    Column(
        modifier = Modifier
            .earthGlass(shape = RoundedCornerShape(4.dp), baseColor = EarthColorTokens.GlassEspresso)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = EarthTheme.typography.microBadge,
            fontSize = 7.sp,
            color = EarthColorTokens.TextSecondary
        )
        Text(
            text = value,
            style = EarthTheme.typography.paramValue,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (isHighlight) EarthColorTokens.AutumnHarvestGold else EarthColorTokens.TextPrimary
        )
    }
}
