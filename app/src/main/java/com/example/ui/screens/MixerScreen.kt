package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.synth.SynthViewModel
import com.example.ui.components.*

@Composable
fun MixerScreen(
    viewModel: SynthViewModel,
    modifier: Modifier = Modifier
) {
    val vuSynth by viewModel.vuSynth.collectAsState()
    val vuBass by viewModel.vuBass.collectAsState()
    val vuDrum by viewModel.vuDrum.collectAsState()
    val vuMaster by viewModel.vuMaster.collectAsState()

    var soloTrack by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AbletonBgDark)
            .verticalScroll(rememberScrollState())
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ==========================================
        // 1. ABLETON MULTI-TRACK MIXING CONSOLE
        // ==========================================
        Surface(
            color = AbletonSurface,
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(1.dp, AbletonBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(AbletonOrange)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "ABLETON MULTI-TRACK MIXING DESK",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AbletonOrange,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Text(
                        text = "STEREO PEAK METERS",
                        fontSize = 8.sp,
                        color = StudioTextSecondary
                    )
                }

                // 4 Ableton Channel Strips Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Track 1: SYNTH LEAD
                    AbletonMixerStrip(
                        trackNumber = "1",
                        title = "LEAD",
                        volume = viewModel.engine.synthVolume,
                        onVolumeChange = { viewModel.engine.synthVolume = it },
                        pan = viewModel.engine.synthPan,
                        onPanChange = { viewModel.engine.synthPan = it },
                        isMuted = viewModel.engine.isSynthMuted,
                        onToggleMute = { viewModel.engine.isSynthMuted = !viewModel.engine.isSynthMuted },
                        isSolo = soloTrack == "1",
                        onToggleSolo = { soloTrack = if (soloTrack == "1") null else "1" },
                        level = vuSynth,
                        trackColor = AbletonOrange,
                        modifier = Modifier.weight(1f)
                    )

                    // Track 2: SUB BASS
                    AbletonMixerStrip(
                        trackNumber = "2",
                        title = "BASS",
                        volume = viewModel.engine.bassVolume,
                        onVolumeChange = { viewModel.engine.bassVolume = it },
                        pan = viewModel.engine.bassPan,
                        onPanChange = { viewModel.engine.bassPan = it },
                        isMuted = viewModel.engine.isBassMuted,
                        onToggleMute = { viewModel.engine.isBassMuted = !viewModel.engine.isBassMuted },
                        isSolo = soloTrack == "2",
                        onToggleSolo = { soloTrack = if (soloTrack == "2") null else "2" },
                        level = vuBass,
                        trackColor = AbletonBlue,
                        modifier = Modifier.weight(1f)
                    )

                    // Track 3: DRUM RACK
                    AbletonMixerStrip(
                        trackNumber = "3",
                        title = "DRUMS",
                        volume = viewModel.engine.drumVolume,
                        onVolumeChange = { viewModel.engine.drumVolume = it },
                        pan = viewModel.engine.drumPan,
                        onPanChange = { viewModel.engine.drumPan = it },
                        isMuted = viewModel.engine.isDrumMuted,
                        onToggleMute = { viewModel.engine.isDrumMuted = !viewModel.engine.isDrumMuted },
                        isSolo = soloTrack == "3",
                        onToggleSolo = { soloTrack = if (soloTrack == "3") null else "3" },
                        level = vuDrum,
                        trackColor = AbletonYellow,
                        modifier = Modifier.weight(1f)
                    )

                    // Track 4: MASTER BUS
                    AbletonMixerStrip(
                        trackNumber = "M",
                        title = "MASTER",
                        volume = viewModel.engine.masterVolume,
                        onVolumeChange = { viewModel.engine.masterVolume = it },
                        pan = 0f,
                        onPanChange = {},
                        isMuted = false,
                        onToggleMute = {},
                        isSolo = false,
                        onToggleSolo = {},
                        level = vuMaster,
                        trackColor = AbletonPurple,
                        modifier = Modifier.weight(1f),
                        isMaster = true
                    )
                }
            }
        }

        // ==========================================
        // 2. ABLETON AUDIO FX MACRO RACK
        // ==========================================
        AbletonMacroRackDevice(
            viewModel = viewModel,
            modifier = Modifier.fillMaxWidth()
        )

        // ==========================================
        // 3. ABLETON LFO MODULATOR
        // ==========================================
        AbletonLfoDevice(
            viewModel = viewModel,
            modifier = Modifier.fillMaxWidth()
        )

        // ==========================================
        // 4. ABLETON DEVICE CHAIN (DSP Audio FX)
        // ==========================================
        AbletonDeviceChain(
            viewModel = viewModel,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun AbletonMixerStrip(
    trackNumber: String,
    title: String,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    pan: Float,
    onPanChange: (Float) -> Unit,
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    isSolo: Boolean,
    onToggleSolo: () -> Unit,
    level: Float,
    trackColor: Color,
    modifier: Modifier = Modifier,
    isMaster: Boolean = false
) {
    Column(
        modifier = modifier
            .background(AbletonPanel, RoundedCornerShape(4.dp))
            .border(1.dp, AbletonBorder, RoundedCornerShape(4.dp))
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Track Color Bar & Name
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(trackColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$trackNumber $title",
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                maxLines = 1
            )
        }

        // Pan Knob with Ableton LCD readout
        if (!isMaster) {
            val panText = when {
                pan < -0.05f -> "${(-pan * 50).toInt()}L"
                pan > 0.05f -> "${(pan * 50).toInt()}R"
                else -> "C"
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                RotaryKnob(
                    value = pan,
                    onValueChange = onPanChange,
                    range = -1f..1f,
                    label = "Pan",
                    accentColor = trackColor,
                    size = 32.dp
                )
                Text(
                    text = panText,
                    fontSize = 7.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = AbletonYellow
                )
            }
        } else {
            Spacer(Modifier.height(42.dp))
        }

        // Fader + VU Meter Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StudioFader(
                value = volume,
                onValueChange = onVolumeChange,
                label = "",
                height = 110.dp,
                accentColor = trackColor
            )
            Spacer(Modifier.width(3.dp))
            LedVuMeter(
                level = level,
                height = 110.dp,
                segments = 14
            )
        }

        // Ableton Track Activator (Yellow square) & Solo (Blue S) Row
        if (!isMaster) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Track Activator [ 1 ]
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(22.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (!isMuted) AbletonYellow else Color(0xFF22252B))
                        .clickable { onToggleMute() }
                        .testTag("track_activator_$trackNumber"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = trackNumber,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (!isMuted) Color.Black else Color.Gray
                    )
                }

                // Solo Button [ S ]
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(22.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (isSolo) AbletonBlue else Color(0xFF22252B))
                        .clickable { onToggleSolo() }
                        .testTag("track_solo_$trackNumber"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "S",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSolo) Color.White else Color.Gray
                    )
                }
            }
        }
    }
}
