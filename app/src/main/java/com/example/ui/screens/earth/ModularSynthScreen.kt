package com.example.ui.screens.earth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.earth.AdsrEnvelopeGraph
import com.example.ui.components.earth.MacroCutoffKnob
import com.example.ui.components.earth.ParametricEqGraph
import com.example.ui.state.DeviceRackStateHolder
import com.example.ui.theme.earth.EarthColorTokens
import com.example.ui.theme.earth.EarthTheme
import com.example.ui.theme.earth.earthGlass

/**
 * Modular Synthesizer & Sound Design Lab (Earth.Design).
 */
@Composable
fun ModularSynthScreen(
    deviceRackStateHolder: DeviceRackStateHolder,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    var cutoff by remember { mutableStateOf(0.65f) }
    var resonance by remember { mutableStateOf(0.40f) }
    var wtPosition by remember { mutableStateOf(0.35f) }
    var fmDepth by remember { mutableStateOf(0.20f) }
    var drive by remember { mutableStateOf(0.15f) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EarthColorTokens.BgObsidianDeep)
            .verticalScroll(scrollState)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // --- 1. Dual Wavetable Oscillators & 3D Holographic Terrain ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .earthGlass(shape = RoundedCornerShape(6.dp), baseColor = EarthColorTokens.GlassSurface)
                .padding(8.dp)
        ) {
            Text(
                text = "WAVETABLE LAB (3D MORPHING)",
                style = EarthTheme.typography.sectionLabel,
                color = EarthColorTokens.EarthAmber
            )
            Spacer(modifier = Modifier.height(6.dp))

            // 3D Holographic Mesh Canvas
            Wavetable3DVisualizer(
                position = wtPosition,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Encoders Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MacroCutoffKnob(
                    value = wtPosition,
                    label = "WT Pos",
                    displayValue = "${(wtPosition * 100).toInt()}%",
                    onValueChange = { wtPosition = it }
                )
                MacroCutoffKnob(
                    value = fmDepth,
                    label = "FM Mod",
                    displayValue = "${(fmDepth * 100).toInt()}%",
                    accentColor = EarthColorTokens.AutumnTerracotta,
                    onValueChange = { fmDepth = it }
                )
                MacroCutoffKnob(
                    value = drive,
                    label = "Drive",
                    displayValue = "${(drive * 100).toInt()}%",
                    accentColor = EarthColorTokens.AutumnRust,
                    onValueChange = { drive = it }
                )
            }
        }

        // --- 2. Dual Ladder Filter & ADSR Envelope ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Filter Module (Left)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .earthGlass(shape = RoundedCornerShape(6.dp), baseColor = EarthColorTokens.GlassSurface)
                    .padding(8.dp)
            ) {
                Text(
                    text = "MOOG 24dB LADDER",
                    style = EarthTheme.typography.sectionLabel,
                    color = EarthColorTokens.NatureEmerald
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    MacroCutoffKnob(
                        value = cutoff,
                        label = "Cutoff",
                        displayValue = "${(cutoff * 16).toInt()} kHz",
                        onValueChange = { cutoff = it }
                    )
                    MacroCutoffKnob(
                        value = resonance,
                        label = "Reso",
                        displayValue = String.format("%.2f", resonance * 8f),
                        onValueChange = { resonance = it }
                    )
                }
            }

            // Envelope Module (Right)
            Column(
                modifier = Modifier
                    .weight(1.2f)
                    .earthGlass(shape = RoundedCornerShape(6.dp), baseColor = EarthColorTokens.GlassSurface)
                    .padding(8.dp)
            ) {
                Text(
                    text = "AMP ADSR ENVELOPE",
                    style = EarthTheme.typography.sectionLabel,
                    color = EarthColorTokens.AutumnMapleAmber
                )
                Spacer(modifier = Modifier.height(6.dp))
                AdsrEnvelopeGraph(
                    attack = 0.15f,
                    decay = 0.35f,
                    sustain = 0.60f,
                    release = 0.45f,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // --- 3. Parametric EQ+ ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .earthGlass(shape = RoundedCornerShape(6.dp), baseColor = EarthColorTokens.GlassSurface)
                .padding(8.dp)
        ) {
            Text(
                text = "PARAMETRIC EQ+ (8-BAND SPECTRUM)",
                style = EarthTheme.typography.sectionLabel,
                color = EarthColorTokens.AutumnHarvestGold
            )
            Spacer(modifier = Modifier.height(6.dp))
            ParametricEqGraph(
                modifier = Modifier.fillMaxWidth(),
                accentColor = EarthColorTokens.AutumnHarvestGold
            )
        }
    }
}

@Composable
private fun Wavetable3DVisualizer(
    position: Float,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .earthGlass(shape = RoundedCornerShape(4.dp), baseColor = EarthColorTokens.BgObsidianDeep)
    ) {
        val lines = 7
        val points = 32

        for (line in 0 until lines) {
            val linePercent = line.toFloat() / (lines - 1)
            val yOffset = (linePercent * size.height * 0.7f) + (size.height * 0.15f)
            val isCurrentPos = kotlin.math.abs(linePercent - position) < 0.15f

            val path = Path()
            for (p in 0..points) {
                val normX = p.toFloat() / points
                val x = normX * size.width
                val wave = kotlin.math.sin((normX * 4 * Math.PI) + (line * 0.8)) * (14.dp.toPx() * (1f - linePercent * 0.3f))
                val y = yOffset - wave.toFloat()

                if (p == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }

            drawPath(
                path = path,
                color = if (isCurrentPos) EarthColorTokens.EarthAmber else EarthColorTokens.NatureMossSage.copy(alpha = 0.4f),
                style = Stroke(width = if (isCurrentPos) 2.dp.toPx() else 1.dp.toPx())
            )
        }
    }
}
