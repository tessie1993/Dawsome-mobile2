package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.synth.*
import com.example.ui.theme.*
import kotlin.math.sin

@Composable
fun ArrangerScreen(
    viewModel: SynthViewModel,
    modifier: Modifier = Modifier
) {
    val isPlaying by viewModel.isPlaying.collectAsState()
    val playbackPosition by viewModel.playbackPosition.collectAsState()
    val zoomLevel by viewModel.zoomLevel.collectAsState()

    var isBrowserOpen by remember { mutableStateOf(true) }
    var selectedBrowserCategory by remember { mutableStateOf("Drums") }
    var selectedTrackId by remember { mutableStateOf("track_bass") }

    // Bottom Device Chain parameters
    var wtPosition by remember { mutableStateOf(0.53f) }
    var wtFilter by remember { mutableStateOf(0.65f) }
    var wtResonance by remember { mutableStateOf(0.24f) }
    var satDrive by remember { mutableStateOf(0.62f) }

    val horizontalScrollState = rememberScrollState()
    val timelineScrollState = rememberScrollState()

    val totalBars = 33
    val baseBarWidthDp = (58 * zoomLevel).dp
    val trackHeaderWidthDp = 135.dp
    val currentBar = playbackPosition / 4.0f

    val accentLime = PulseGridActive
    val accentCyan = TrackBlue
    val panelBg = PulseGridSurface
    val surfaceDark = PulseGridBg
    val borderDark = PulseGridBorder

    // Palette for 8 Studio Tracks
    val trackThemes = listOf(
        Triple("1 Kick", TrackRed, "Kick 909"),
        Triple("2 Perc", TrackOrange, "Perc Loop"),
        Triple("3 Bass", TrackGreen, "Acid Sub"),
        Triple("4 Atmos", TrackBlue, "Deep Pad"),
        Triple("5 Lead", TrackPurple, "Saw Lead"),
        Triple("6 Vocal", TrackPink, "Vocals Hook"),
        Triple("7 FX", TrackYellow, "Riser FX"),
        Triple("8 Return", TrackReturn, "Reverb Send")
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PulseGridBg)
    ) {
        // ==========================================
        // TOP HALF: BROWSER | TIMELINE ARRANGER | MASTER CHANNEL
        // ==========================================
        Row(
            modifier = Modifier
                .weight(1.1f)
                .fillMaxWidth()
        ) {
            // ------------------------------------------
            // 1. COLLAPSIBLE LEFT BROWSER
            // ------------------------------------------
            AnimatedVisibility(visible = isBrowserOpen) {
                Surface(
                    color = panelBg,
                    modifier = Modifier
                        .width(180.dp)
                        .fillMaxHeight()
                        .border(0.5.dp, borderDark)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(6.dp)
                    ) {
                        // Search Bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0B0D12), RoundedCornerShape(3.dp))
                                .border(0.5.dp, borderDark, RoundedCornerShape(3.dp))
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.Search, contentDescription = "Search", tint = Color(0xFF6B7A8D), modifier = Modifier.size(12.dp))
                                Text("Search", fontSize = 8.5.sp, color = Color(0xFF6B7A8D))
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Category Tree
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text("CATEGORIES", fontSize = 7.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6B7A8D), letterSpacing = 0.5.sp)

                            val categories = listOf("Sounds", "Drums", "Instruments", "Audio Effects", "MIDI Effects", "Samples", "Packs", "Projects")
                            categories.forEach { cat ->
                                val isSel = selectedBrowserCategory == cat
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(if (isSel) Color(0xFF1E2838) else Color.Transparent)
                                        .clickable { selectedBrowserCategory = cat }
                                        .padding(horizontal = 6.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = when(cat) {
                                            "Drums" -> Icons.Default.Album
                                            "Instruments" -> Icons.Default.Piano
                                            "Audio Effects" -> Icons.Default.Tune
                                            "Samples" -> Icons.Default.GraphicEq
                                            else -> Icons.Default.Folder
                                        },
                                        contentDescription = null,
                                        tint = if (isSel) accentLime else Color(0xFF8A98A8),
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text = cat,
                                        fontSize = 8.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSel) Color.White else Color(0xFFCAD2DE)
                                    )
                                }
                            }

                            HorizontalDivider(color = borderDark, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))

                            Text("KITS & SAMPLES", fontSize = 7.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6B7A8D), letterSpacing = 0.5.sp)
                            val samples = listOf("Analogue Kit", "Deep House Kit", "Forest Kit", "Minimal Kit", "Vintage Breaks", "Clap 01.wav", "Clap 02.wav", "Sub 808.wav")
                            samples.forEach { sample ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.showToast("Loaded $sample") }
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color(0xFF5A6678), modifier = Modifier.size(10.dp))
                                    Text(sample, fontSize = 7.5.sp, color = Color(0xFF94A3B8), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }

            // ------------------------------------------
            // 2. CENTER TIMELINE ARRANGER (8 TRACKS)
            // ------------------------------------------
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color(0xFF090A0E))
            ) {
                // Arrangement Ruler & Loop Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .background(Color(0xFF10131B))
                        .border(0.5.dp, borderDark)
                ) {
                    // Left Header Corner
                    Box(
                        modifier = Modifier
                            .width(trackHeaderWidthDp)
                            .fillMaxHeight()
                            .background(Color(0xFF131722))
                            .border(0.5.dp, borderDark)
                            .padding(horizontal = 6.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(
                                imageVector = if (isBrowserOpen) Icons.Default.ArrowBack else Icons.Default.ArrowForward,
                                contentDescription = "Toggle Browser",
                                tint = accentLime,
                                modifier = Modifier.size(12.dp).clickable { isBrowserOpen = !isBrowserOpen }
                            )
                            Text("TRACKS (8)", fontSize = 7.5.sp, fontWeight = FontWeight.Black, color = Color(0xFFCAD2DE))
                        }
                    }

                    // Horizontal Timeline Ruler
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .horizontalScroll(timelineScrollState)
                    ) {
                        Canvas(modifier = Modifier.width(baseBarWidthDp * totalBars).height(24.dp)) {
                            val barW = baseBarWidthDp.toPx()
                            val h = size.height

                            // Draw Bar Grid Markers
                            for (b in 1..totalBars) {
                                val x = (b - 1) * barW
                                val isMajor = (b - 1) % 4 == 0
                                drawLine(
                                    color = if (isMajor) Color(0xFF3E485C) else Color(0xFF202532),
                                    start = Offset(x, 0f),
                                    end = Offset(x, h),
                                    strokeWidth = if (isMajor) 1.dp.toPx() else 0.5.dp.toPx()
                                )
                            }

                            // Loop Bracket (Bars 5 to 21) in lime
                            val loopStartX = (4) * barW
                            val loopEndX = (20) * barW
                            drawRect(
                                color = accentLime.copy(alpha = 0.12f),
                                topLeft = Offset(loopStartX, 0f),
                                size = Size(loopEndX - loopStartX, h)
                            )
                            drawLine(
                                color = accentLime,
                                start = Offset(loopStartX, 2.dp.toPx()),
                                end = Offset(loopEndX, 2.dp.toPx()),
                                strokeWidth = 2.5.dp.toPx()
                            )
                        }

                        // Bar Numbers Text
                        Row(modifier = Modifier.fillMaxWidth()) {
                            for (b in 1..totalBars) {
                                Box(
                                    modifier = Modifier.width(baseBarWidthDp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if ((b - 1) % 4 == 0 || b == 1) {
                                        Text(
                                            text = "$b",
                                            fontSize = 8.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = if (b in 5..21) accentLime else Color(0xFF8A98A8),
                                            modifier = Modifier.padding(start = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Scrollable 8 Tracks Body
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    trackThemes.forEachIndexed { index, (trackName, trackColor, clipLabel) ->
                        val isTrackSel = selectedTrackId == "track_$index" || (index == 2 && selectedTrackId == "track_bass")
                        val isMuted = false
                        val isSolo = index == 2 // Bass soloed in demo

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .background(if (isTrackSel) Color(0xFF131720) else Color(0xFF0C0E14))
                                .border(0.5.dp, borderDark)
                                .clickable { selectedTrackId = "track_$index" }
                        ) {
                            // Track Header Panel
                            Row(
                                modifier = Modifier
                                    .width(trackHeaderWidthDp)
                                    .fillMaxHeight()
                                    .background(Color(0xFF12151D))
                                    .border(0.5.dp, borderDark)
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(verticalArrangement = Arrangement.Center) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Box(modifier = Modifier.size(5.dp).background(trackColor, RoundedCornerShape(1.dp)))
                                        Text(trackName, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                    Text("In 1-2 | Auto", fontSize = 6.5.sp, color = Color(0xFF6B7A8D))
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                                    // Solo
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(if (isSolo) Color(0xFF00E5FF) else Color(0xFF1C222E))
                                            .clickable { viewModel.showToast("Solo $trackName") },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("S", fontSize = 7.5.sp, fontWeight = FontWeight.Bold, color = if (isSolo) Color.Black else Color(0xFFCAD2DE))
                                    }

                                    // Mute
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(if (isMuted) Color(0xFFFF5252) else Color(0xFF1C222E))
                                            .clickable { viewModel.showToast("Mute $trackName") },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("M", fontSize = 7.5.sp, fontWeight = FontWeight.Bold, color = if (isMuted) Color.White else Color(0xFFCAD2DE))
                                    }

                                    // Mini VU Level
                                    Column(
                                        modifier = Modifier
                                            .width(4.dp)
                                            .height(28.dp)
                                            .background(Color(0xFF0B0D12), RoundedCornerShape(1.dp))
                                    ) {
                                        Spacer(modifier = Modifier.weight(if (isPlaying) 0.3f else 0.8f))
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(if (isPlaying) 0.7f else 0.2f)
                                                .background(if (index == 2) accentLime else Color(0xFF00E5FF))
                                        )
                                    }
                                }
                            }

                            // Track Timeline Clips Area
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .horizontalScroll(timelineScrollState)
                            ) {
                                Canvas(
                                    modifier = Modifier
                                        .width(baseBarWidthDp * totalBars)
                                        .fillMaxHeight()
                                ) {
                                    val barW = baseBarWidthDp.toPx()
                                    val h = size.height

                                    // Subdivision lines
                                    for (b in 1..totalBars) {
                                        val x = (b - 1) * barW
                                        drawLine(
                                            color = Color(0xFF1A1E29),
                                            start = Offset(x, 0f),
                                            end = Offset(x, h),
                                            strokeWidth = 0.5.dp.toPx()
                                        )
                                    }

                                    // Draw Track Clips
                                    val clipRanges = when (index) {
                                        0 -> listOf(1 to 17, 17 to 33) // Kick
                                        1 -> listOf(5 to 17, 21 to 33) // Perc
                                        2 -> listOf(9 to 25) // Bass Drop
                                        3 -> listOf(1 to 33) // Atmos Pad
                                        4 -> listOf(13 to 29) // Lead Saw
                                        5 -> listOf(9 to 17, 25 to 33) // Vocal
                                        6 -> listOf(15 to 17, 31 to 33) // FX Risers
                                        else -> listOf(1 to 33)
                                    }

                                    clipRanges.forEach { (sBar, eBar) ->
                                        val clipX = (sBar - 1) * barW
                                        val clipW = (eBar - sBar) * barW - 2.dp.toPx()
                                        val clipY = 4.dp.toPx()
                                        val clipH = h - 8.dp.toPx()

                                        // Clip body
                                        drawRoundRect(
                                            color = trackColor.copy(alpha = 0.85f),
                                            topLeft = Offset(clipX, clipY),
                                            size = Size(clipW, clipH),
                                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx())
                                        )

                                        // Clip border
                                        drawRoundRect(
                                            color = Color.White.copy(alpha = 0.6f),
                                            topLeft = Offset(clipX, clipY),
                                            size = Size(clipW, clipH),
                                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                                            style = Stroke(width = 0.75.dp.toPx())
                                        )

                                        // Simulated Waveform/MIDI lines
                                        for (step in 0 until (clipW / 6.dp.toPx()).toInt()) {
                                            val lx = clipX + step * 6.dp.toPx() + 2.dp.toPx()
                                            val lh = (clipH * 0.6f) * (0.3f + 0.7f * ((step % 5) / 4f))
                                            drawLine(
                                                color = Color.Black.copy(alpha = 0.45f),
                                                start = Offset(lx, clipY + (clipH - lh) / 2f),
                                                end = Offset(lx, clipY + (clipH + lh) / 2f),
                                                strokeWidth = 1.5.dp.toPx()
                                            )
                                        }
                                    }

                                    // Realtime Playhead
                                    if (isPlaying) {
                                        val playheadX = currentBar * barW
                                        drawLine(
                                            color = Color.White,
                                            start = Offset(playheadX, 0f),
                                            end = Offset(playheadX, h),
                                            strokeWidth = 2.dp.toPx()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ------------------------------------------
            // 3. RIGHT MASTER VU PEAK METER (MAIN)
            // ------------------------------------------
            Surface(
                color = Color(0xFF0F1218),
                modifier = Modifier
                    .width(44.dp)
                    .fillMaxHeight()
                    .border(0.5.dp, borderDark)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 4.dp, horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Main", fontSize = 7.5.sp, fontWeight = FontWeight.Bold, color = Color.White)

                    // Master LED scale & Peak Meter
                    Box(
                        modifier = Modifier
                            .width(18.dp)
                            .weight(1f)
                            .background(Color(0xFF08090C), RoundedCornerShape(2.dp))
                            .border(0.5.dp, borderDark)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(1.dp)
                        ) {
                            val activeSegs = if (isPlaying) 14 else 4
                            for (seg in 20 downTo 1) {
                                val segColor = when {
                                    seg > 18 -> Color(0xFFFF5252)
                                    seg > 14 -> TrackYellow
                                    else -> accentLime
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .padding(vertical = 0.5.dp)
                                        .background(if (seg <= activeSegs) segColor else Color(0xFF141822))
                                )
                            }
                        }
                    }

                    // Master Fader Value Readout
                    Text("-3.2", fontSize = 7.5.sp, fontFamily = FontFamily.Monospace, color = accentLime)

                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(Color(0xFF1E2838), RoundedCornerShape(2.dp))
                            .clickable { viewModel.showToast("Master Solo") },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("S", fontSize = 7.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }

        // ==========================================
        // BOTTOM HALF: SPLIT CLIP INSPECTOR & ABLETON DEVICE CHAIN
        // ==========================================
        Surface(
            color = panelBg,
            modifier = Modifier
                .weight(0.9f)
                .fillMaxWidth()
                .border(1.dp, borderDark)
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                // ------------------------------------------
                // 4A. LEFT CARD: CLIP | NOTES | ENVELOPES
                // ------------------------------------------
                Column(
                    modifier = Modifier
                        .width(220.dp)
                        .fillMaxHeight()
                        .background(Color(0xFF10131B))
                        .border(0.5.dp, borderDark)
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Header & Expand Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(modifier = Modifier.size(6.dp).background(accentLime, RoundedCornerShape(1.dp)))
                            Text("Clip: Bass 01", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFF1C222E))
                                .border(0.5.dp, accentLime, RoundedCornerShape(2.dp))
                                .clickable {
                                    viewModel.selectTab(DawTab.PIANO_ROLL)
                                }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("Expand ↗", fontSize = 7.5.sp, fontWeight = FontWeight.Bold, color = accentLime)
                        }
                    }

                    // Parameters Grid
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Start: 9.1.1", fontSize = 7.5.sp, color = Color(0xFF8A98A8), fontFamily = FontFamily.Monospace)
                        Text("End: 17.1.1", fontSize = 7.5.sp, color = Color(0xFF8A98A8), fontFamily = FontFamily.Monospace)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Length: 8.0.0", fontSize = 7.5.sp, color = Color(0xFF8A98A8))
                        Text("Loop: On", fontSize = 7.5.sp, color = accentLime, fontWeight = FontWeight.Bold)
                    }

                    // Mini Piano Roll Preview
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(Color(0xFF090A0E), RoundedCornerShape(3.dp))
                            .border(0.5.dp, borderDark)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height
                            // Grid
                            for (i in 0..8) {
                                val gx = i * (w / 8)
                                drawLine(Color(0xFF181C26), Offset(gx, 0f), Offset(gx, h), 0.5.dp.toPx())
                            }
                            // Notes
                            val miniNotes = listOf(
                                Triple(0.5f, 0.3f, 1.5f),
                                Triple(2.0f, 0.6f, 1.0f),
                                Triple(3.5f, 0.4f, 2.0f),
                                Triple(6.0f, 0.7f, 1.2f)
                            )
                            miniNotes.forEach { (st, pitchR, len) ->
                                val nx = st * (w / 8)
                                val nw = len * (w / 8)
                                val ny = pitchR * h
                                drawRoundRect(
                                    color = accentLime,
                                    topLeft = Offset(nx, ny),
                                    size = Size(nw, 4.dp.toPx()),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx(), 1.dp.toPx())
                                )
                            }
                        }
                    }
                }

                // ------------------------------------------
                // 4B. RIGHT CARD: ABLETON DEVICE CHAIN (BASS TRACK)
                // ------------------------------------------
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(Color(0xFF0E1017))
                        .padding(6.dp)
                ) {
                    // Track Device Chain Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(modifier = Modifier.size(6.dp).background(accentLime, RoundedCornerShape(1.dp)))
                            Text("DEVICE CHAIN: 3 Bass", fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp, color = accentLime)
                        }
                        Text("Macro 1: Morph  Macro 2: Drive  Macro 3: Cutoff  Macro 4: Space", fontSize = 7.5.sp, color = Color(0xFF6B7A8D))
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Chained Audio Devices (Horizontal Scrollable)
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .horizontalScroll(horizontalScrollState),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // DEVICE 1: Drum Rack (Pads)
                        Surface(
                            color = Color(0xFF141720),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(0.5.dp, borderDark),
                            modifier = Modifier.width(170.dp).fillMaxHeight()
                        ) {
                            Column(modifier = Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Box(modifier = Modifier.size(5.dp).background(TrackOrange, CircleShape))
                                    Text("Drum Rack", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                                // 4x4 Pads Grid
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    for (r in 0..3) {
                                        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                            for (c in 0..3) {
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .fillMaxHeight()
                                                        .background(if (r == 3 && c == 0) Color(0xFFFF5252) else Color(0xFF1E2430), RoundedCornerShape(2.dp))
                                                        .clickable { viewModel.showToast("Pad ${r * 4 + c + 1}") }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // DEVICE 2: Wavetable Synth
                        Surface(
                            color = Color(0xFF141720),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(0.5.dp, accentLime.copy(alpha = 0.5f)),
                            modifier = Modifier.width(230.dp).fillMaxHeight()
                        ) {
                            Column(modifier = Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Box(modifier = Modifier.size(5.dp).background(accentLime, CircleShape))
                                        Text("Wavetable", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                    Text("Basics > Shapes", fontSize = 7.sp, color = accentLime)
                                }

                                // Animated Green Morphed Waveform Canvas
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(42.dp)
                                        .background(Color(0xFF090A0E), RoundedCornerShape(3.dp))
                                        .border(0.5.dp, borderDark)
                                ) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val w = size.width
                                        val h = size.height
                                        val wavePath = Path()
                                        for (x in 0..w.toInt()) {
                                            val normX = x / w
                                            val y = (h / 2f) + sin((normX * 4 * Math.PI) + (wtPosition * Math.PI)).toFloat() * (h * 0.38f)
                                            if (x == 0) wavePath.moveTo(x.toFloat(), y) else wavePath.lineTo(x.toFloat(), y)
                                        }
                                        drawPath(wavePath, accentLime, style = Stroke(width = 1.5.dp.toPx()))
                                    }
                                }

                                // Knobs Row: WT Pos, Filter, Res
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("WT Pos", fontSize = 6.5.sp, color = Color(0xFF8A98A8))
                                        Slider(
                                            value = wtPosition,
                                            onValueChange = { wtPosition = it },
                                            modifier = Modifier.width(48.dp).height(14.dp),
                                            colors = SliderDefaults.colors(thumbColor = accentLime, activeTrackColor = accentLime, inactiveTrackColor = Color(0xFF1E2430))
                                        )
                                        Text("${(wtPosition * 100).toInt()}%", fontSize = 6.5.sp, color = Color.White)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Filter", fontSize = 6.5.sp, color = Color(0xFF8A98A8))
                                        Slider(
                                            value = wtFilter,
                                            onValueChange = {
                                                wtFilter = it
                                                viewModel.setFilterCutoff(it * 10000f + 200f)
                                            },
                                            modifier = Modifier.width(48.dp).height(14.dp),
                                            colors = SliderDefaults.colors(thumbColor = accentLime, activeTrackColor = accentLime, inactiveTrackColor = Color(0xFF1E2430))
                                        )
                                        Text("1.2 kHz", fontSize = 6.5.sp, color = Color.White)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Res", fontSize = 6.5.sp, color = Color(0xFF8A98A8))
                                        Slider(
                                            value = wtResonance,
                                            onValueChange = { wtResonance = it },
                                            modifier = Modifier.width(48.dp).height(14.dp),
                                            colors = SliderDefaults.colors(thumbColor = accentLime, activeTrackColor = accentLime, inactiveTrackColor = Color(0xFF1E2430))
                                        )
                                        Text("24%", fontSize = 6.5.sp, color = Color.White)
                                    }
                                }
                            }
                        }

                        // DEVICE 3: Saturator
                        Surface(
                            color = Color(0xFF141720),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(0.5.dp, borderDark),
                            modifier = Modifier.width(180.dp).fillMaxHeight()
                        ) {
                            Column(modifier = Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Box(modifier = Modifier.size(5.dp).background(TrackOrange, CircleShape))
                                    Text("Saturator", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }

                                // S-Curve Visualizer
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(42.dp)
                                        .background(Color(0xFF090A0E), RoundedCornerShape(3.dp))
                                ) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val w = size.width
                                        val h = size.height
                                        val sPath = Path()
                                        sPath.moveTo(0f, h)
                                        sPath.cubicTo(w * 0.35f, h * 0.8f, w * 0.65f, h * 0.2f, w, 0f)
                                        drawPath(sPath, TrackOrange, style = Stroke(width = 1.5.dp.toPx()))
                                    }
                                }

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Drive: 6.2 dB", fontSize = 7.sp, color = Color.White)
                                    Text("Soft Clip", fontSize = 7.sp, color = TrackOrange)
                                }
                            }
                        }

                        // DEVICE 4: Delay (Echo)
                        Surface(
                            color = Color(0xFF141720),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(0.5.dp, borderDark),
                            modifier = Modifier.width(180.dp).fillMaxHeight()
                        ) {
                            Column(modifier = Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Box(modifier = Modifier.size(5.dp).background(accentCyan, CircleShape))
                                    Text("Delay", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("L Sync: 1/4", fontSize = 7.5.sp, color = accentCyan)
                                    Text("R Sync: 1/4", fontSize = 7.5.sp, color = accentCyan)
                                }
                                Text("Ping Pong | Feedback 32%", fontSize = 7.sp, color = Color(0xFF8A98A8))
                            }
                        }

                        // DEVICE 5: Reverb
                        Surface(
                            color = Color(0xFF141720),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(0.5.dp, borderDark),
                            modifier = Modifier.width(180.dp).fillMaxHeight()
                        ) {
                            Column(modifier = Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Box(modifier = Modifier.size(5.dp).background(TrackPurple, CircleShape))
                                    Text("Reverb", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                                Text("Plate | Decay: 2.35s", fontSize = 7.5.sp, color = TrackPurple)
                                Text("Pre-Delay: 20ms | Size: 65%", fontSize = 7.sp, color = Color(0xFF8A98A8))
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // 5. BOTTOM STATUS & GRID CONTROL BAR
        // ==========================================
        Surface(
            color = Color(0xFF0C0E13),
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp)
                .border(0.5.dp, borderDark)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left controls: Zoom, Fit, Grid, Swing
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Grid: 1/8", fontSize = 8.sp, color = Color(0xFF8A98A8))
                    Text("Swing: 0%", fontSize = 8.sp, color = Color(0xFF8A98A8))
                    Text("Snap: On", fontSize = 8.sp, color = accentLime, fontWeight = FontWeight.Bold)
                }

                // Center readout: Position
                Text(
                    text = "Pos: ${(currentBar.toInt() + 1)}.1.1  0:22.857",
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White
                )

                // Right controls: Scale, Key, Virtual keyboard
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Key: C", fontSize = 8.sp, color = Color(0xFF8A98A8))
                    Text("Scale: Minor", fontSize = 8.sp, color = Color(0xFF8A98A8))
                    Icon(Icons.Default.Keyboard, contentDescription = "Keyboard", tint = accentLime, modifier = Modifier.size(12.dp))
                }
            }
        }
    }
}
