package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.synth.*
import com.example.ui.components.*

@Composable
fun SessionViewScreen(
    viewModel: SynthViewModel,
    modifier: Modifier = Modifier
) {
    val isPlaying by viewModel.isPlaying.collectAsState()
    val playbackPosition by viewModel.playbackPosition.collectAsState()
    val scenes by viewModel.scenes.collectAsState()
    val activeSceneIndex by viewModel.activeSceneIndex.collectAsState()
    val activePlayingClips by viewModel.activePlayingClips.collectAsState()

    val vuSynth by viewModel.vuSynth.collectAsState()
    val vuBass by viewModel.vuBass.collectAsState()
    val vuDrum by viewModel.vuDrum.collectAsState()
    val vuMaster by viewModel.vuMaster.collectAsState()

    val engine = viewModel.engine

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AbletonBgDark)
            .padding(8.dp)
    ) {
        // Ableton Session View Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AbletonPanel, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .border(1.dp, AbletonBorder, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(if (isPlaying) AbletonGreen else Color.Gray, CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "ABLETON SESSION MATRIX",
                    color = AbletonOrange,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { viewModel.stopAllClipsIfAny() },
                    colors = ButtonDefaults.buttonColors(containerColor = AbletonSurface),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.height(28.dp).testTag("stop_all_clips_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop All Clips",
                        tint = Color.Red,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("STOP ALL CLIPS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = StudioTextPrimary)
                }
            }
        }

        // Clip Launch Matrix & Scenes
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(AbletonSurface)
                .border(1.dp, AbletonBorder)
        ) {
            // Track Column Headers
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AbletonHeader)
                        .padding(vertical = 4.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Lead Track Header
                    TrackHeaderCell(
                        trackNumber = "1",
                        trackName = "LEAD SYNTH",
                        color = AbletonTrackLead,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))

                    // Bass Track Header
                    TrackHeaderCell(
                        trackNumber = "2",
                        trackName = "BASSLINE",
                        color = AbletonTrackBass,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))

                    // Drums Track Header
                    TrackHeaderCell(
                        trackNumber = "3",
                        trackName = "DRUMS",
                        color = AbletonTrackDrums,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))

                    // Master Scene Header
                    Box(
                        modifier = Modifier
                            .width(80.dp)
                            .height(24.dp)
                            .background(AbletonPanel, RoundedCornerShape(3.dp))
                            .border(1.dp, AbletonBorder, RoundedCornerShape(3.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "MASTER",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = AbletonTrackMaster
                        )
                    }
                }
            }

            // Scene Rows
            itemsIndexed(scenes) { sceneIdx, scene ->
                val isSceneActive = activeSceneIndex == sceneIdx && isPlaying

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isSceneActive) AbletonPanel.copy(alpha = 0.8f) else Color.Transparent)
                        .padding(horizontal = 4.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Lead Clip Slot
                    val leadClip = scene.clips[SessionTrackType.LEAD]
                    val isLeadPlaying = activePlayingClips[SessionTrackType.LEAD] == sceneIdx && isPlaying
                    ClipSlotCell(
                        clip = leadClip,
                        isPlaying = isLeadPlaying,
                        trackColor = AbletonTrackLead,
                        onTrigger = { viewModel.triggerClip(SessionTrackType.LEAD, sceneIdx) },
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    // 2. Bass Clip Slot
                    val bassClip = scene.clips[SessionTrackType.BASS]
                    val isBassPlaying = activePlayingClips[SessionTrackType.BASS] == sceneIdx && isPlaying
                    ClipSlotCell(
                        clip = bassClip,
                        isPlaying = isBassPlaying,
                        trackColor = AbletonTrackBass,
                        onTrigger = { viewModel.triggerClip(SessionTrackType.BASS, sceneIdx) },
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    // 3. Drums Clip Slot
                    val drumClip = scene.clips[SessionTrackType.DRUMS]
                    val isDrumPlaying = activePlayingClips[SessionTrackType.DRUMS] == sceneIdx && isPlaying
                    ClipSlotCell(
                        clip = drumClip,
                        isPlaying = isDrumPlaying,
                        trackColor = AbletonTrackDrums,
                        onTrigger = { viewModel.triggerClip(SessionTrackType.DRUMS, sceneIdx) },
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    // Scene Launch Button
                    SceneLaunchCell(
                        sceneName = scene.name,
                        isActive = isSceneActive,
                        bpm = scene.bpm,
                        onLaunch = { viewModel.triggerScene(sceneIdx) },
                        modifier = Modifier.width(80.dp)
                    )
                }
            }

            // Stop Clips Row
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TrackStopClipButton(
                        onClick = { viewModel.stopTrackClip(SessionTrackType.LEAD) },
                        trackColor = AbletonTrackLead,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))

                    TrackStopClipButton(
                        onClick = { viewModel.stopTrackClip(SessionTrackType.BASS) },
                        trackColor = AbletonTrackBass,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))

                    TrackStopClipButton(
                        onClick = { viewModel.stopTrackClip(SessionTrackType.DRUMS) },
                        trackColor = AbletonTrackDrums,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))

                    Box(
                        modifier = Modifier
                            .width(80.dp)
                            .height(24.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(AbletonHeader)
                            .clickable { viewModel.stopAllClipsIfAny() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("STOP ALL", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Ableton Bottom Mixer Strips (Channel Strips)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .background(AbletonPanel, RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                .border(1.dp, AbletonBorder, RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                .padding(6.dp)
        ) {
            // Track 1 Channel Strip (Lead)
            var leadMute by remember { mutableStateOf(engine.isSynthMuted) }
            var leadSolo by remember { mutableStateOf(false) }
            var leadVol by remember { mutableStateOf(engine.synthVolume) }
            var leadPan by remember { mutableStateOf(engine.synthPan) }

            AbletonChannelStrip(
                trackNumber = "1",
                trackName = "LEAD",
                color = AbletonTrackLead,
                volume = leadVol,
                onVolumeChange = {
                    leadVol = it
                    engine.synthVolume = it
                },
                pan = leadPan,
                onPanChange = {
                    leadPan = it
                    engine.synthPan = it
                },
                isMuted = leadMute,
                onMuteToggle = {
                    leadMute = !leadMute
                    engine.isSynthMuted = leadMute
                },
                isSolo = leadSolo,
                onSoloToggle = {
                    leadSolo = !leadSolo
                },
                vuLevel = vuSynth,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(4.dp))

            // Track 2 Channel Strip (Bass)
            var bassMute by remember { mutableStateOf(engine.isBassMuted) }
            var bassSolo by remember { mutableStateOf(false) }
            var bassVol by remember { mutableStateOf(engine.bassVolume) }
            var bassPan by remember { mutableStateOf(engine.bassPan) }

            AbletonChannelStrip(
                trackNumber = "2",
                trackName = "BASS",
                color = AbletonTrackBass,
                volume = bassVol,
                onVolumeChange = {
                    bassVol = it
                    engine.bassVolume = it
                },
                pan = bassPan,
                onPanChange = {
                    bassPan = it
                    engine.bassPan = it
                },
                isMuted = bassMute,
                onMuteToggle = {
                    bassMute = !bassMute
                    engine.isBassMuted = bassMute
                },
                isSolo = bassSolo,
                onSoloToggle = {
                    bassSolo = !bassSolo
                },
                vuLevel = vuBass,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(4.dp))

            // Track 3 Channel Strip (Drums)
            var drumMute by remember { mutableStateOf(engine.isDrumMuted) }
            var drumSolo by remember { mutableStateOf(false) }
            var drumVol by remember { mutableStateOf(engine.drumVolume) }
            var drumPan by remember { mutableStateOf(engine.drumPan) }

            AbletonChannelStrip(
                trackNumber = "3",
                trackName = "DRUMS",
                color = AbletonTrackDrums,
                volume = drumVol,
                onVolumeChange = {
                    drumVol = it
                    engine.drumVolume = it
                },
                pan = drumPan,
                onPanChange = {
                    drumPan = it
                    engine.drumPan = it
                },
                isMuted = drumMute,
                onMuteToggle = {
                    drumMute = !drumMute
                    engine.isDrumMuted = drumMute
                },
                isSolo = drumSolo,
                onSoloToggle = {
                    drumSolo = !drumSolo
                },
                vuLevel = vuDrum,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(4.dp))

            // Master Channel Strip
            var masterVol by remember { mutableStateOf(engine.masterVolume) }

            AbletonMasterStrip(
                volume = masterVol,
                onVolumeChange = {
                    masterVol = it
                    engine.masterVolume = it
                },
                vuLevel = vuMaster,
                modifier = Modifier.width(80.dp)
            )
        }
    }
}

// Extension on SynthViewModel for stopping all clips
private fun SynthViewModel.stopAllClipsIfAny() {
    stopTrackClip(SessionTrackType.LEAD)
    stopTrackClip(SessionTrackType.BASS)
    stopTrackClip(SessionTrackType.DRUMS)
    stopTransport()
}

@Composable
fun TrackHeaderCell(
    trackNumber: String,
    trackName: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(24.dp)
            .background(AbletonSurface, RoundedCornerShape(3.dp))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(color, RoundedCornerShape(2.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(trackNumber, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = trackName,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = StudioTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ClipSlotCell(
    clip: SessionClip?,
    isPlaying: Boolean,
    trackColor: Color,
    onTrigger: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val hasClip = clip != null
    val clipName = clip?.name ?: "Empty Slot"

    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(
                if (isPlaying) trackColor.copy(alpha = 0.35f * pulseAlpha)
                else if (hasClip) AbletonPanel
                else AbletonSurface.copy(alpha = 0.5f)
            )
            .border(
                width = if (isPlaying) 1.5.dp else 1.dp,
                color = if (isPlaying) AbletonGreen else if (hasClip) trackColor.copy(alpha = 0.4f) else AbletonBorder,
                shape = RoundedCornerShape(4.dp)
            )
            .clickable { onTrigger() }
            .padding(horizontal = 6.dp, vertical = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Play Icon Button / Status Indicator
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(
                            if (isPlaying) AbletonGreen else if (hasClip) trackColor else Color(0xFF4A4E5A),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.GraphicEq else Icons.Default.PlayArrow,
                        contentDescription = "Trigger Clip",
                        tint = Color.Black,
                        modifier = Modifier.size(12.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = clipName,
                    fontSize = 10.sp,
                    fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium,
                    color = if (isPlaying) Color.White else if (hasClip) StudioTextPrimary else StudioTextSecondary.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun SceneLaunchCell(
    sceneName: String,
    isActive: Boolean,
    bpm: Float,
    onLaunch: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (isActive) AbletonGreen.copy(alpha = 0.25f) else AbletonPanel)
            .border(
                1.dp,
                if (isActive) AbletonGreen else AbletonBorder,
                RoundedCornerShape(4.dp)
            )
            .clickable { onLaunch() }
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = sceneName,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) AbletonGreen else StudioTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${bpm.toInt()} BPM",
                    fontSize = 8.sp,
                    color = StudioTextSecondary
                )
            }

            Box(
                modifier = Modifier
                    .size(18.dp)
                    .background(if (isActive) AbletonGreen else AbletonOrange, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Launch Scene",
                    tint = Color.Black,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

@Composable
fun TrackStopClipButton(
    onClick: () -> Unit,
    trackColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(24.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(AbletonSurface)
            .border(1.dp, AbletonBorder, RoundedCornerShape(3.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(Color.Red.copy(alpha = 0.8f), RoundedCornerShape(2.dp))
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("STOP", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = StudioTextSecondary)
        }
    }
}

@Composable
fun AbletonChannelStrip(
    trackNumber: String,
    trackName: String,
    color: Color,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    pan: Float,
    onPanChange: (Float) -> Unit,
    isMuted: Boolean,
    onMuteToggle: () -> Unit,
    isSolo: Boolean,
    onSoloToggle: () -> Unit,
    vuLevel: Float,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(AbletonSurface, RoundedCornerShape(4.dp))
            .border(1.dp, AbletonBorder, RoundedCornerShape(4.dp))
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Track Header Tag
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(color.copy(alpha = 0.2f), RoundedCornerShape(2.dp))
                .padding(vertical = 2.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(trackNumber, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = color)
            Spacer(modifier = Modifier.width(4.dp))
            Text(trackName, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
        }

        // Small Pan Knob
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("PAN", fontSize = 7.sp, color = StudioTextSecondary)
            Text(
                if (pan < -0.05f) "L${(pan * -50).toInt()}" else if (pan > 0.05f) "R${(pan * 50).toInt()}" else "C",
                fontSize = 7.sp,
                color = StudioTextPrimary
            )
        }

        Slider(
            value = pan,
            onValueChange = onPanChange,
            valueRange = -1f..1f,
            modifier = Modifier.height(18.dp)
        )

        // Fader & VU Meter
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Volume Slider
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Slider(
                    value = volume,
                    onValueChange = onVolumeChange,
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("${(volume * 100).toInt()}%", fontSize = 8.sp, color = color)
            }

            Spacer(modifier = Modifier.width(4.dp))

            // VU Bar
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF141720), RoundedCornerShape(2.dp))
                    .border(0.5.dp, Color(0xFF282E44), RoundedCornerShape(2.dp)),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(vuLevel.coerceIn(0f, 1f))
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Red, Color.Yellow, Color.Green)
                            ),
                            RoundedCornerShape(2.dp)
                        )
                )
            }
        }

        // Mute & Solo Buttons (Ableton Style: Yellow M, Blue S)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Mute Button
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(22.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (!isMuted) AbletonYellow else AbletonHeader)
                    .clickable { onMuteToggle() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "M",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (!isMuted) Color.Black else Color.Gray
                )
            }

            // Solo Button
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(22.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (isSolo) AbletonBlue else AbletonHeader)
                    .clickable { onSoloToggle() },
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

@Composable
fun AbletonMasterStrip(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    vuLevel: Float,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(AbletonSurface, RoundedCornerShape(4.dp))
            .border(1.dp, AbletonTrackMaster.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(AbletonTrackMaster.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                .padding(vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("MASTER", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = AbletonTrackMaster)
        }

        // Master Fader
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f)
        ) {
            Slider(
                value = volume,
                onValueChange = onVolumeChange,
                valueRange = 0f..1f,
                modifier = Modifier.fillMaxWidth()
            )
            Text("${(volume * 100).toInt()}%", fontSize = 8.sp, color = AbletonTrackMaster)
        }

        // Master VU
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .background(Color(0xFF141720), RoundedCornerShape(2.dp))
                .border(0.5.dp, AbletonBorder, RoundedCornerShape(2.dp)),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(vuLevel.coerceIn(0f, 1f))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Green, Color.Yellow, Color.Red)
                        ),
                        RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}
