package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.synth.SynthViewModel
import com.example.ui.theme.*

@Composable
fun AbletonTransportBar(
    viewModel: SynthViewModel,
    modifier: Modifier = Modifier
) {
    val isPlaying by viewModel.isPlaying.collectAsState()
    val bpm by viewModel.bpm.collectAsState()
    val isMetronomeOn by viewModel.isMetronomeOn.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val isLoopEnabled by viewModel.isLoopEnabled.collectAsState()
    val currentProjectName by viewModel.currentProjectName.collectAsState()

    var showMenu by remember { mutableStateOf(false) }

    Surface(
        color = PulseGridHeader,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, PulseGridBorderDark)
            .testTag("pulsegrid_transport_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // ==========================================
            // Left Cluster: [≡] PULSEGRID | Forest Pulse
            // ==========================================
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1.2f)
            ) {
                // Menu / Drawer button
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(PulseGridPanel)
                        .border(1.dp, PulseGridBorder, RoundedCornerShape(4.dp))
                        .clickable { viewModel.toggleBrowserDrawer() }
                        .testTag("toggle_browser_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Menu",
                        tint = PulseGridTextPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Brand Title
                Text(
                    text = "PULSEGRID",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    color = Color.White
                )

                // Divider
                Box(
                    modifier = Modifier
                        .height(14.dp)
                        .width(1.dp)
                        .background(PulseGridBorder)
                )

                // Project Name (clickable for Save / Project manager)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(PulseGridBg)
                        .clickable { viewModel.openSaveDialog() }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (currentProjectName.isNotBlank()) currentProjectName else "Forest Pulse",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = PulseGridTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Export MIDI button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(PulseGridBg)
                        .clickable { viewModel.showToast("Exporting MIDI to internal storage...") }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "Export MIDI",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = PulseGridActive,
                        maxLines = 1
                    )
                }
            }

            // ==========================================
            // Center Cluster: BPM | 4/4 | [Play] [Stop] [Rec] [Loop] [Metro] [Undo]
            // ==========================================
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1.5f)
            ) {
                // BPM Display / Control
                Box(
                    modifier = Modifier
                        .height(28.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(PulseGridBg)
                        .border(1.dp, PulseGridBorder, RoundedCornerShape(3.dp))
                        .clickable { viewModel.updateBpm(if (bpm >= 140f) 120f else bpm + 2f) }
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = String.format("%.2f", bpm),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        color = PulseGridTextPrimary
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Time Signature (4/4)
                Box(
                    modifier = Modifier
                        .height(28.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(PulseGridBg)
                        .border(1.dp, PulseGridBorder, RoundedCornerShape(3.dp))
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "4/4",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        color = PulseGridTextSecondary
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Play Button
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isPlaying) PulseGridActive else PulseGridSurface)
                        .clickable { viewModel.togglePlay() }
                        .testTag("play_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = if (isPlaying) Color.Black else PulseGridTextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Stop Button
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(PulseGridSurface)
                        .border(1.dp, PulseGridBorder, RoundedCornerShape(4.dp))
                        .clickable { viewModel.stopTransport() }
                        .testTag("stop_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(PulseGridTextSecondary, RoundedCornerShape(1.dp))
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Record Button
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isRecording) PulseGridError else PulseGridSurface)
                        .border(1.dp, if (isRecording) PulseGridError else PulseGridBorder, RoundedCornerShape(4.dp))
                        .clickable { viewModel.toggleRecording() }
                        .testTag("record_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .background(if (isRecording) Color.White else PulseGridError, CircleShape)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Loop / Cycle Button
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isLoopEnabled) PulseGridPanel else PulseGridSurface)
                        .border(1.dp, if (isLoopEnabled) PulseGridActive else PulseGridBorder, RoundedCornerShape(4.dp))
                        .clickable { viewModel.toggleArrangementLoop() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Repeat,
                        contentDescription = "Loop",
                        tint = if (isLoopEnabled) PulseGridActive else PulseGridTextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Metronome Icon Button
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isMetronomeOn) PulseGridPanel else PulseGridSurface)
                        .border(1.dp, if (isMetronomeOn) PulseGridActive else PulseGridBorder, RoundedCornerShape(4.dp))
                        .clickable { viewModel.toggleMetronome() }
                        .testTag("metronome_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = "Metronome",
                        tint = if (isMetronomeOn) PulseGridActive else PulseGridTextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Undo Button ↶
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(PulseGridSurface)
                        .border(1.dp, PulseGridBorder, RoundedCornerShape(4.dp))
                        .clickable { viewModel.undoAction() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Undo,
                        contentDescription = "Undo",
                        tint = PulseGridTextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            // ==========================================
            // Right Cluster: CPU 18% [■■■] | [⋮]
            // ==========================================
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.weight(0.9f)
            ) {
                // CPU Usage readout + LED meter
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(PulseGridBg)
                        .border(1.dp, PulseGridBorder, RoundedCornerShape(3.dp))
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "CPU 18%",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = PulseGridTextSecondary
                    )
                    // Mini LED meter bar
                    Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                        repeat(5) { index ->
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(8.dp)
                                    .background(if (index < 2) PulseGridActive else PulseGridPanel)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // 3-dots Menu
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { showMenu = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More Options",
                        tint = PulseGridTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(PulseGridPanel)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Save Project", color = PulseGridTextPrimary) },
                            onClick = {
                                showMenu = false
                                viewModel.openSaveDialog()
                            },
                            leadingIcon = { Icon(Icons.Default.Save, contentDescription = null, tint = PulseGridActive) }
                        )
                        DropdownMenuItem(
                            text = { Text("Export Audio (WAV)", color = PulseGridTextPrimary) },
                            onClick = {
                                showMenu = false
                                viewModel.exportMasterWav()
                            },
                            leadingIcon = { Icon(Icons.Default.Download, contentDescription = null, tint = TrackBlue) }
                        )
                        DropdownMenuItem(
                            text = { Text("New Project", color = PulseGridTextPrimary) },
                            onClick = {
                                showMenu = false
                                viewModel.createNewProject()
                            },
                            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, tint = TrackOrange) }
                        )
                    }
                }
            }
        }
    }
}

