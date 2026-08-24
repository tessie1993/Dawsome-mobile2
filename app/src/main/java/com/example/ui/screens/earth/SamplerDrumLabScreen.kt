package com.example.ui.screens.earth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.synth.domain.DrumPadType
import com.example.ui.components.earth.AdsrEnvelopeGraph
import com.example.ui.components.earth.InteractiveWaveformCanvas
import com.example.ui.components.earth.MacroCutoffKnob
import com.example.ui.components.earth.VelocityDrumPad
import com.example.ui.state.DeviceRackStateHolder
import com.example.ui.theme.earth.EarthColorTokens
import com.example.ui.theme.earth.EarthTheme
import com.example.ui.theme.earth.earthGlass

/**
 * Sampler & Drum Machine Lab (Earth.Design).
 */
@Composable
fun SamplerDrumLabScreen(
    deviceRackStateHolder: DeviceRackStateHolder,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var selectedPad by remember { mutableStateOf(DrumPadType.KICK) }
    var padTune by remember { mutableStateOf(0.5f) }
    var padFilter by remember { mutableStateOf(0.85f) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EarthColorTokens.BgObsidianDeep)
            .verticalScroll(scrollState)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // --- Upper: Audio Waveform Slicer ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .earthGlass(shape = RoundedCornerShape(6.dp), baseColor = EarthColorTokens.GlassSurface)
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TRANSIENT SLICER & SAMPLER",
                    style = EarthTheme.typography.sectionLabel,
                    color = EarthColorTokens.NatureEmerald
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(EarthColorTokens.AutumnRust)
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "SLICE TO DRUM RACK",
                        style = EarthTheme.typography.microBadge,
                        color = EarthColorTokens.TextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            InteractiveWaveformCanvas(
                modifier = Modifier.fillMaxWidth(),
                accentColor = EarthColorTokens.NatureEmerald
            )
        }

        // --- Lower: 16-Pad Drum Matrix & Pad Inspector ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 4x4 Drum Matrix (Left)
            Column(
                modifier = Modifier
                    .weight(1.3f)
                    .earthGlass(shape = RoundedCornerShape(6.dp), baseColor = EarthColorTokens.GlassSurface)
                    .padding(8.dp)
            ) {
                Text(
                    text = "16-PAD VELOCITY DRUM RACK",
                    style = EarthTheme.typography.sectionLabel,
                    color = EarthColorTokens.AutumnRust
                )
                Spacer(modifier = Modifier.height(6.dp))

                val allPads = DrumPadType.entries.toTypedArray()
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(allPads) { pad ->
                        VelocityDrumPad(
                            pad = pad,
                            isTriggered = pad == selectedPad,
                            onTrigger = {
                                selectedPad = pad
                            }
                        )
                    }
                }
            }

            // Pad Inspector (Right)
            Column(
                modifier = Modifier
                    .weight(0.9f)
                    .earthGlass(shape = RoundedCornerShape(6.dp), baseColor = EarthColorTokens.GlassSurface)
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "${selectedPad.displayName.uppercase()} PARAMS",
                    style = EarthTheme.typography.sectionLabel,
                    color = EarthColorTokens.AutumnMapleAmber,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    MacroCutoffKnob(
                        value = padTune,
                        label = "Tune",
                        displayValue = "${((padTune - 0.5f) * 24).toInt()} st",
                        onValueChange = { padTune = it }
                    )
                    MacroCutoffKnob(
                        value = padFilter,
                        label = "Filter",
                        displayValue = "${(padFilter * 16).toInt()} kHz",
                        onValueChange = { padFilter = it }
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                AdsrEnvelopeGraph(
                    attack = 0.05f,
                    decay = 0.40f,
                    sustain = 0.0f,
                    release = 0.20f,
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = EarthColorTokens.AutumnRust
                )
            }
        }
    }
}
