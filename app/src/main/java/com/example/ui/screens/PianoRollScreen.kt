package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.synth.*
import com.example.ui.components.*
import kotlin.math.abs
import kotlin.math.max

enum class PianoRollTool {
    DRAW, SELECT, SPLIT, GLUE, ERASE, AUDITION
}

enum class ExpressionLaneType(val title: String, val color: Color) {
    VELOCITY("Velocity", Color(0xFF8CE838)),
    CHANCE("Chance", Color(0xFF00E5FF)),
    RATCHET("Ratchet", Color(0xFFB28DFF)),
    PITCH("Pitch", Color(0xFFFF9500)),
    SLIDE("Slide", Color(0xFF26A69A)),
    PRESSURE("Pressure", Color(0xFF42A5F5))
}

@Composable
fun PianoRollScreen(
    viewModel: SynthViewModel,
    modifier: Modifier = Modifier
) {
    var activeTab by remember { mutableStateOf("Notes") } // "Notes", "Expression", "Envelopes"
    var activeTool by remember { mutableStateOf(PianoRollTool.DRAW) }
    var selectedExpressionLane by remember { mutableStateOf(ExpressionLaneType.VELOCITY) }

    val bassNotes by viewModel.bassNotes.collectAsState()
    val leadNotes by viewModel.leadNotes.collectAsState()
    var selectedTrack by remember { mutableStateOf(PianoRollTrack.BASS) }
    val currentNotes = if (selectedTrack == PianoRollTrack.BASS) bassNotes else leadNotes

    val playbackPosition by viewModel.playbackPosition.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isLoopEnabled by viewModel.isLoopEnabled.collectAsState()
    val loopStartBar by viewModel.loopStartBar.collectAsState()
    val loopEndBar by viewModel.loopEndBar.collectAsState()

    // Note Tools State
    var clipStart by remember { mutableStateOf("17.1.1") }
    var clipEnd by remember { mutableStateOf("25.1.1") }
    var isClipLoopOn by remember { mutableStateOf(true) }
    var selectedScaleName by remember { mutableStateOf("F Minor") }
    var isFoldOn by remember { mutableStateOf(true) }
    var isHighlightScaleOn by remember { mutableStateOf(true) }
    var quantizeValue by remember { mutableStateOf(0.82f) }
    var swingValue by remember { mutableStateOf(0.24f) }

    // Selected Notes Inspector State
    var selectedNoteIds by remember { mutableStateOf(setOf<String>()) }
    var inspectorPosition by remember { mutableStateOf("21.2.2") }
    var inspectorLength by remember { mutableStateOf("1/8") }
    var inspectorPitch by remember { mutableStateOf("F2 (41)") }
    var inspectorVelocity by remember { mutableStateOf(104f) }
    var inspectorChance by remember { mutableStateOf(86f) }
    var inspectorRatchet by remember { mutableStateOf(2) }
    var isNoteMuted by remember { mutableStateOf(false) }

    // Selection Marquee / Chord Detection
    var hoveredNote by remember { mutableStateOf<MidiNote?>(null) }
    var isSnapOn by remember { mutableStateOf(true) }
    var gridResolution by remember { mutableStateOf("1/16") }
    var zoomLevel by remember { mutableStateOf(1.0f) }

    val startBarOffset = 17
    val totalBarsInClip = 8 // Bars 17 to 25
    val totalBeats = totalBarsInClip * 4f // 32 beats

    // Pitch Range: F1 (29) to C5 (72)
    val minPitch = 28 // E1
    val maxPitch = 60 // C4
    val fMinorScale = listOf(5, 7, 8, 10, 0, 1, 3) // F, G, Ab, Bb, C, Db, Eb

    val displayedPitches = remember(isFoldOn, currentNotes) {
        val all = (maxPitch downTo minPitch).toList()
        if (isFoldOn) {
            val usedPitches = currentNotes.map { it.pitch }.toSet()
            all.filter { (it % 12) in fMinorScale || it in usedPitches }
        } else {
            all
        }
    }

    val horizontalScrollState = rememberScrollState()
    val verticalScrollState = rememberScrollState()

    val beatWidthDp = (48 * zoomLevel).dp
    val rowHeightDp = 18.dp
    val totalGridWidthDp = beatWidthDp * totalBeats
    val totalGridHeightDp = rowHeightDp * displayedPitches.size

    val accentLime = Color(0xFF8CE838)
    val accentCyan = Color(0xFF00E5FF)
    val panelBg = Color(0xFF14171E)
    val surfaceDark = Color(0xFF0E1015)
    val borderDark = Color(0xFF222632)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(surfaceDark)
    ) {
        // ==========================================
        // 1. ARRANGEMENT TIMELINE MINI-OVERVIEW STRIP
        // ==========================================
        Surface(
            color = Color(0xFF0D0F14),
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .border(1.dp, Color(0xFF1A1E27))
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                // Left Track Names
                Column(
                    modifier = Modifier
                        .width(85.dp)
                        .fillMaxHeight()
                        .background(Color(0xFF10131A))
                        .border(0.5.dp, Color(0xFF1F2430))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    val tracks = listOf(
                        "Kick" to Color(0xFFFF5252),
                        "Perc" to Color(0xFFFF9500),
                        "Bass" to accentLime,
                        "Atmos" to accentCyan,
                        "Lead" to Color(0xFFB28DFF),
                        "Vocal" to Color(0xFFFF2A6D),
                        "FX" to Color(0xFFFED142)
                    )
                    tracks.forEach { (name, color) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .background(if (name == "Bass") color else color.copy(alpha = 0.4f), CircleShape)
                            )
                            Text(
                                text = name,
                                fontSize = 7.sp,
                                fontWeight = if (name == "Bass") FontWeight.Bold else FontWeight.Normal,
                                color = if (name == "Bass") Color.White else Color(0xFF8A98A8),
                                maxLines = 1
                            )
                        }
                    }
                }

                // Mini Timeline Overview Ruler & Clips
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(Color(0xFF090A0E))
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val rulerW = size.width
                        val h = size.height
                        val totalOverviewBars = 44f
                        val barW = rulerW / totalOverviewBars

                        // Bar Numbers Top
                        for (b in 1..44 step 4) {
                            val x = (b - 1) * barW
                            drawLine(
                                color = Color(0xFF262C3A),
                                start = Offset(x, 0f),
                                end = Offset(x, h),
                                strokeWidth = 1.dp.toPx()
                            )
                        }

                        // Loop Bracket (Bars 17 to 29) in lime/yellow
                        val loopStartX = (16) * barW
                        val loopEndX = (28) * barW
                        drawRect(
                            color = accentLime.copy(alpha = 0.08f),
                            topLeft = Offset(loopStartX, 0f),
                            size = Size(loopEndX - loopStartX, h)
                        )
                        drawLine(
                            color = accentLime,
                            start = Offset(loopStartX, 2.dp.toPx()),
                            end = Offset(loopEndX, 2.dp.toPx()),
                            strokeWidth = 2.dp.toPx()
                        )
                        drawCircle(accentLime, radius = 3.dp.toPx(), center = Offset(loopStartX, 2.dp.toPx()))
                        drawCircle(accentLime, radius = 3.dp.toPx(), center = Offset(loopEndX, 2.dp.toPx()))

                        // "Bass Drop" Active Clip Block (Bar 17 to 25)
                        val clipX = (16) * barW
                        val clipW = (8) * barW
                        val clipY = h * 0.35f
                        val clipH = h * 0.22f
                        drawRoundRect(
                            color = accentLime.copy(alpha = 0.85f),
                            topLeft = Offset(clipX, clipY),
                            size = Size(clipW, clipH),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx())
                        )

                        // Ghost audio waveform tracks above & below
                        listOf(0.12f, 0.24f, 0.60f, 0.72f, 0.85f).forEach { yRatio ->
                            drawLine(
                                color = Color(0xFF333D4F).copy(alpha = 0.5f),
                                start = Offset(0f, h * yRatio),
                                end = Offset(rulerW, h * yRatio),
                                strokeWidth = 1.dp.toPx()
                            )
                        }

                        // Playhead Vertical Line at Bar 21
                        val playheadX = ((playbackPosition / 4.0f) % 44f) * barW
                        drawLine(
                            color = Color.White,
                            start = Offset(if (isPlaying) playheadX else (20) * barW, 0f),
                            end = Offset(if (isPlaying) playheadX else (20) * barW, h),
                            strokeWidth = 1.5.dp.toPx()
                        )
                    }

                    // Bar Numbers Text Overlay
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 1.dp, start = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf("1", "5", "9", "13", "17", "21", "25", "29", "33", "37", "41").forEach { bNum ->
                            Text(bNum, fontSize = 7.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF6B7A8D))
                        }
                    }

                    // Clip Name Label
                    Text(
                        text = "Bass Drop",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        modifier = Modifier
                            .padding(start = 140.dp, top = 22.dp)
                    )
                }
            }
        }

        // ==========================================
        // 2. MIDI CLIP HEADER & TAB SWITCHER
        // ==========================================
        Surface(
            color = Color(0xFF10131B),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, borderDark)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Title
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "MIDI CLIP",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp,
                        color = accentLime
                    )
                    Text("—", fontSize = 11.sp, color = Color(0xFF5A6678))
                    Text(
                        text = "Bass Drop",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE2E8F0)
                    )
                }

                // View Tabs: Notes | Expression | Envelopes
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("Notes", "Expression", "Envelopes").forEach { tab ->
                        val isSel = activeTab == tab
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (isSel) Color(0xFF181C26) else Color.Transparent)
                                .clickable { activeTab = tab }
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = tab,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSel) Color.White else Color(0xFF8A98A8)
                                )
                                if (isSel) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Box(
                                        modifier = Modifier
                                            .width(20.dp)
                                            .height(2.dp)
                                            .background(accentLime, RoundedCornerShape(1.dp))
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // 3. MAIN WORKSPACE: LEFT INSPECTOR | PIANO ROLL & EXPRESSION | RIGHT INSPECTOR
        // ==========================================
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // ------------------------------------------
            // 3A. LEFT INSPECTOR: NOTE TOOLS & CHORD TOOLS
            // ------------------------------------------
            Column(
                modifier = Modifier
                    .width(135.dp)
                    .fillMaxHeight()
                    .background(panelBg)
                    .border(0.5.dp, borderDark)
                    .padding(8.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "NOTE TOOLS",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp,
                    color = Color(0xFF8A98A8)
                )

                // Start & End
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Start", fontSize = 9.sp, color = Color(0xFF8A98A8))
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF0D0F14), RoundedCornerShape(2.dp))
                            .border(0.5.dp, borderDark, RoundedCornerShape(2.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(clipStart, fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = Color.White)
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("End", fontSize = 9.sp, color = Color(0xFF8A98A8))
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF0D0F14), RoundedCornerShape(2.dp))
                            .border(0.5.dp, borderDark, RoundedCornerShape(2.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(clipEnd, fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = Color.White)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Loop", fontSize = 9.sp, color = Color(0xFF8A98A8))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (isClipLoopOn) Color(0xFF1E2838) else Color(0xFF0D0F14))
                            .border(0.5.dp, if (isClipLoopOn) accentLime else borderDark, RoundedCornerShape(2.dp))
                            .clickable { isClipLoopOn = !isClipLoopOn }
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(if (isClipLoopOn) "On" else "Off", fontSize = 8.sp, color = if (isClipLoopOn) accentLime else Color(0xFF8A98A8), fontWeight = FontWeight.Bold)
                    }
                }

                // Scale Dropdown
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Scale", fontSize = 9.sp, color = Color(0xFF8A98A8))
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF0D0F14), RoundedCornerShape(2.dp))
                            .border(0.5.dp, borderDark, RoundedCornerShape(2.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(selectedScaleName, fontSize = 8.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }

                // Fold & Highlight Scale Switches
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Fold", fontSize = 9.sp, color = Color(0xFF8A98A8))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (isFoldOn) Color(0xFF1E2838) else Color(0xFF0D0F14))
                            .border(0.5.dp, if (isFoldOn) accentLime else borderDark, RoundedCornerShape(2.dp))
                            .clickable { isFoldOn = !isFoldOn }
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(if (isFoldOn) "On" else "Off", fontSize = 8.sp, color = if (isFoldOn) accentLime else Color(0xFF8A98A8), fontWeight = FontWeight.Bold)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Highlight Scale", fontSize = 8.sp, color = Color(0xFF8A98A8))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (isHighlightScaleOn) Color(0xFF1E2838) else Color(0xFF0D0F14))
                            .border(0.5.dp, if (isHighlightScaleOn) accentLime else borderDark, RoundedCornerShape(2.dp))
                            .clickable { isHighlightScaleOn = !isHighlightScaleOn }
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(if (isHighlightScaleOn) "On" else "Off", fontSize = 8.sp, color = if (isHighlightScaleOn) accentLime else Color(0xFF8A98A8), fontWeight = FontWeight.Bold)
                    }
                }

                // Action Buttons Grid: Duplicate, Invert, Legato, Humanize
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(22.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFF1B202B))
                                .border(0.5.dp, borderDark, RoundedCornerShape(2.dp))
                                .clickable {
                                    if (selectedTrack == PianoRollTrack.BASS) viewModel.duplicatePattern()
                                }
                                .padding(horizontal = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Duplicate", fontSize = 7.5.sp, color = Color(0xFFCAD2DE))
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(22.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFF1B202B))
                                .border(0.5.dp, borderDark, RoundedCornerShape(2.dp))
                                .clickable { viewModel.showToast("Invert notes") }
                                .padding(horizontal = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Invert", fontSize = 7.5.sp, color = Color(0xFFCAD2DE))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(22.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFF1B202B))
                                .border(0.5.dp, borderDark, RoundedCornerShape(2.dp))
                                .clickable { viewModel.showToast("Legato applied") }
                                .padding(horizontal = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Legato", fontSize = 7.5.sp, color = Color(0xFFCAD2DE))
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(22.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFF1B202B))
                                .border(0.5.dp, borderDark, RoundedCornerShape(2.dp))
                                .clickable { viewModel.showToast("Humanize applied") }
                                .padding(horizontal = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Humanize", fontSize = 7.5.sp, color = Color(0xFFCAD2DE))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(22.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFF1B202B))
                                .border(0.5.dp, borderDark, RoundedCornerShape(2.dp))
                                .clickable { viewModel.showToast("Randomize applied") }
                                .padding(horizontal = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Randomize", fontSize = 7.5.sp, color = Color(0xFFCAD2DE))
                        }
                    }
                }

                // Quantize Slider
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Quantize", fontSize = 8.sp, color = Color(0xFF8A98A8))
                        Text("1/16 ▾", fontSize = 8.sp, color = Color.White)
                        Text("${(quantizeValue * 100).toInt()}%", fontSize = 8.sp, color = accentLime, fontFamily = FontFamily.Monospace)
                    }
                    Slider(
                        value = quantizeValue,
                        onValueChange = { quantizeValue = it },
                        modifier = Modifier.height(16.dp),
                        colors = SliderDefaults.colors(thumbColor = accentLime, activeTrackColor = accentLime, inactiveTrackColor = Color(0xFF1B202B))
                    )
                }

                // Swing Slider
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Swing", fontSize = 8.sp, color = Color(0xFF8A98A8))
                        Text("16 ▾", fontSize = 8.sp, color = Color.White)
                        Text("${(swingValue * 100).toInt()}%", fontSize = 8.sp, color = accentLime, fontFamily = FontFamily.Monospace)
                    }
                    Slider(
                        value = swingValue,
                        onValueChange = { swingValue = it },
                        modifier = Modifier.height(16.dp),
                        colors = SliderDefaults.colors(thumbColor = accentLime, activeTrackColor = accentLime, inactiveTrackColor = Color(0xFF1B202B))
                    )
                }

                Divider(color = borderDark, thickness = 0.5.dp)

                // CHORD TOOLS
                Text(
                    text = "CHORD TOOLS",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp,
                    color = Color(0xFF8A98A8)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("Triad", "Seventh", "Add 9").forEach { chordType ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(22.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFF1B202B))
                                .border(0.5.dp, borderDark, RoundedCornerShape(2.dp))
                                .clickable {
                                    viewModel.showToast("Inserted $chordType chord")
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(chordType, fontSize = 7.5.sp, color = Color(0xFFCAD2DE))
                        }
                    }
                }
            }

            // ------------------------------------------
            // 3B. CENTER PIANO ROLL CANVAS + MULTI-LANE EXPRESSION STRIP
            // ------------------------------------------
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color(0xFF0B0D12))
            ) {
                // Piano Roll Main Grid
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    // Left Vertical Piano Keys
                    Column(
                        modifier = Modifier
                            .width(42.dp)
                            .fillMaxHeight()
                            .verticalScroll(verticalScrollState)
                            .background(Color(0xFF12151D))
                            .border(0.5.dp, borderDark)
                    ) {
                        displayedPitches.forEach { pitch ->
                            val isBlack = (pitch % 12) in listOf(1, 3, 6, 8, 10)
                            val isC = (pitch % 12) == 0
                            val noteName = getNoteName(pitch)

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(rowHeightDp)
                                    .background(if (isBlack) Color(0xFF12141A) else Color(0xFFE8ECEF))
                                    .border(0.5.dp, Color(0xFF262C3A))
                                    .pointerInput(pitch) {
                                        detectTapGestures(
                                            onPress = {
                                                if (selectedTrack == PianoRollTrack.BASS) viewModel.engine.bassNoteOn(pitch)
                                                else viewModel.engine.noteOn(pitch)
                                                tryAwaitRelease()
                                                if (selectedTrack == PianoRollTrack.BASS) viewModel.engine.bassNoteOff(pitch)
                                                else viewModel.engine.noteOff(pitch)
                                            }
                                        )
                                    }
                                    .padding(end = 4.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                if (isC || noteName == "C1" || noteName == "C2" || noteName == "C3" || noteName == "C4") {
                                    Text(
                                        text = noteName,
                                        fontSize = 7.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isBlack) Color.White else Color.Black
                                    )
                                }
                            }
                        }
                    }

                    // Piano Roll Canvas Grid
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .horizontalScroll(horizontalScrollState)
                            .verticalScroll(verticalScrollState)
                    ) {
                        Canvas(
                            modifier = Modifier
                                .width(totalGridWidthDp)
                                .height(totalGridHeightDp)
                                .pointerInput(currentNotes, displayedPitches, selectedTrack) {
                                    detectTapGestures(
                                        onTap = { offset ->
                                            val cellH = rowHeightDp.toPx()
                                            val cellW = beatWidthDp.toPx()
                                            val clickedBeat = (offset.x / (cellW / 4)).toInt() * 0.25f
                                            val clickedRow = (offset.y / cellH).toInt().coerceIn(0, displayedPitches.size - 1)
                                            val clickedPitch = displayedPitches[clickedRow]

                                            val existing = currentNotes.find {
                                                it.pitch == clickedPitch &&
                                                        clickedBeat >= it.startBeat &&
                                                        clickedBeat < it.startBeat + it.lengthBeats
                                            }

                                            if (existing != null) {
                                                if (activeTool == PianoRollTool.ERASE || activeTool == PianoRollTool.DRAW) {
                                                    if (selectedTrack == PianoRollTrack.BASS) viewModel.removeBassNote(existing.id)
                                                    else viewModel.removeLeadNote(existing.id)
                                                } else {
                                                    selectedNoteIds = setOf(existing.id)
                                                    inspectorPitch = "${getNoteName(existing.pitch)} (${existing.pitch})"
                                                    inspectorVelocity = existing.velocity * 127f
                                                }
                                            } else {
                                                if (activeTool == PianoRollTool.DRAW) {
                                                    val newNote = MidiNote(
                                                        pitch = clickedPitch,
                                                        startBeat = clickedBeat,
                                                        lengthBeats = 1.0f,
                                                        velocity = 0.85f
                                                    )
                                                    if (selectedTrack == PianoRollTrack.BASS) {
                                                        viewModel.addBassNote(newNote)
                                                        viewModel.engine.bassNoteOn(clickedPitch)
                                                    } else {
                                                        viewModel.addLeadNote(newNote)
                                                        viewModel.engine.noteOn(clickedPitch)
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }
                        ) {
                            val cellW = beatWidthDp.toPx()
                            val cellH = rowHeightDp.toPx()
                            val subDivW = cellW / 4

                            // Background Rows & Scale Shading
                            displayedPitches.forEachIndexed { index, pitch ->
                                val inScale = (pitch % 12) in fMinorScale
                                val isRoot = (pitch % 12) == 5 // F note

                                val rowColor = when {
                                    isRoot -> Color(0xFF1E2824)
                                    inScale -> Color(0xFF141720)
                                    else -> Color(0xFF0E1015)
                                }

                                drawRect(
                                    color = rowColor,
                                    topLeft = Offset(0f, index * cellH),
                                    size = Size(size.width, cellH)
                                )
                                drawLine(
                                    color = Color(0xFF1E2330),
                                    start = Offset(0f, index * cellH),
                                    end = Offset(size.width, index * cellH),
                                    strokeWidth = 0.5.dp.toPx()
                                )
                            }

                            // Vertical Subdivision & Bar Lines (Bars 17, 18, 19, 20, 21, 22, 23, 24, 25)
                            for (b in 0..(totalBeats * 4).toInt()) {
                                val x = b * subDivW
                                val isBar = (b % 16 == 0)
                                val isBeat = (b % 4 == 0)

                                drawLine(
                                    color = if (isBar) Color(0xFF3B465C) else if (isBeat) Color(0xFF252C3A) else Color(0xFF171B24),
                                    start = Offset(x, 0f),
                                    end = Offset(x, size.height),
                                    strokeWidth = if (isBar) 1.2.dp.toPx() else 0.5.dp.toPx()
                                )
                            }

                            // Selection Marquee Box (Around Fm7 Chord at Bar 21)
                            val chordStartBeat = 16f // Bar 21
                            val chordLenBeats = 3.5f
                            val marqueeX = chordStartBeat * cellW - 4.dp.toPx()
                            val marqueeW = chordLenBeats * cellW + 8.dp.toPx()
                            val marqueeTopY = 4 * cellH - 2.dp.toPx()
                            val marqueeH = 6 * cellH + 4.dp.toPx()

                            drawRoundRect(
                                color = accentLime.copy(alpha = 0.05f),
                                topLeft = Offset(marqueeX, marqueeTopY),
                                size = Size(marqueeW, marqueeH),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
                            )
                            drawRoundRect(
                                color = accentLime.copy(alpha = 0.8f),
                                topLeft = Offset(marqueeX, marqueeTopY),
                                size = Size(marqueeW, marqueeH),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                                style = Stroke(
                                    width = 1.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
                                )
                            )

                            // Draw Note Blocks
                            currentNotes.forEach { note ->
                                val rowIndex = displayedPitches.indexOf(note.pitch)
                                if (rowIndex != -1) {
                                    val noteX = note.startBeat * cellW
                                    val noteW = (note.lengthBeats * cellW) - 2.dp.toPx()
                                    val noteY = rowIndex * cellH + 2.dp.toPx()
                                    val noteH = cellH - 4.dp.toPx()

                                    // Fill
                                    drawRoundRect(
                                        color = accentLime,
                                        topLeft = Offset(noteX, noteY),
                                        size = Size(max(noteW, 6.dp.toPx()), noteH),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx())
                                    )

                                    // Border
                                    drawRoundRect(
                                        color = Color.White.copy(alpha = 0.7f),
                                        topLeft = Offset(noteX, noteY),
                                        size = Size(max(noteW, 6.dp.toPx()), noteH),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                                        style = Stroke(width = 0.75.dp.toPx())
                                    )
                                }
                            }

                            // Playhead Line
                            val currentPlayheadBeat = (playbackPosition % totalBeats)
                            val playheadX = currentPlayheadBeat * cellW
                            drawLine(
                                color = Color.White,
                                start = Offset(if (isPlaying) playheadX else 16f * cellW, 0f),
                                end = Offset(if (isPlaying) playheadX else 16f * cellW, size.height),
                                strokeWidth = 2.dp.toPx()
                            )
                        }

                        // Bar Ruler Header Overlay (17, 18, 19, 20, 21, 22, 23, 24, 25)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp)
                        ) {
                            for (b in startBarOffset until (startBarOffset + totalBarsInClip)) {
                                Box(
                                    modifier = Modifier.width(beatWidthDp * 4),
                                    contentAlignment = Alignment.TopStart
                                ) {
                                    Text(
                                        text = "$b",
                                        fontSize = 8.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF8A98A8),
                                        modifier = Modifier.padding(start = 2.dp)
                                    )
                                }
                            }
                        }

                        // Chord Name Badge above selection ("Fm7")
                        Box(
                            modifier = Modifier
                                .offset(x = beatWidthDp * 16 + 10.dp, y = 50.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xFF1E2838))
                                .border(0.5.dp, accentLime, RoundedCornerShape(3.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("Fm7", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = accentLime)
                        }

                        // Hovered Note Tooltip Overlay ("F2 | 21.2.2 | Length: 1/8 | Velocity: 100")
                        Box(
                            modifier = Modifier
                                .offset(x = beatWidthDp * 17 + 20.dp, y = 140.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xFF0F1218))
                                .border(0.5.dp, Color(0xFF333D4F), RoundedCornerShape(3.dp))
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Column {
                                Text("F2", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text("21.2.2  Length: 1/8", fontSize = 7.sp, color = Color(0xFF94A3B8))
                                Text("Velocity: 100", fontSize = 7.sp, color = accentLime)
                            }
                        }
                    }
                }

                // ------------------------------------------
                // 3B.1 MULTI-LANE EXPRESSION & VELOCITY STRIP (BOTTOM)
                // ------------------------------------------
                Surface(
                    color = Color(0xFF0C0E14),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .border(1.dp, borderDark)
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        // Left Expression Selectors
                        Column(
                            modifier = Modifier
                                .width(78.dp)
                                .fillMaxHeight()
                                .background(Color(0xFF10131A))
                                .border(0.5.dp, borderDark)
                                .padding(vertical = 4.dp, horizontal = 4.dp),
                            verticalArrangement = Arrangement.SpaceEvenly
                        ) {
                            ExpressionLaneType.values().forEach { lane ->
                                val isCur = selectedExpressionLane == lane
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(if (isCur) Color(0xFF1C222F) else Color.Transparent)
                                        .clickable { selectedExpressionLane = lane }
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Box(modifier = Modifier.size(5.dp).background(lane.color, CircleShape))
                                    Text(
                                        text = lane.title,
                                        fontSize = 7.5.sp,
                                        fontWeight = if (isCur) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isCur) Color.White else Color(0xFF8A98A8)
                                    )
                                }
                            }
                        }

                        // Right Multi-Lane Canvas
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .horizontalScroll(horizontalScrollState)
                        ) {
                            Canvas(
                                modifier = Modifier
                                    .width(totalGridWidthDp)
                                    .height(130.dp)
                            ) {
                                val cellW = beatWidthDp.toPx()
                                val h = size.height

                                // Draw subtle lane dividers
                                listOf(0.25f, 0.5f, 0.75f).forEach { ratio ->
                                    drawLine(
                                        color = Color(0xFF1F2430),
                                        start = Offset(0f, h * ratio),
                                        end = Offset(size.width, h * ratio),
                                        strokeWidth = 0.5.dp.toPx()
                                    )
                                }

                                // 1. Velocity Stems (Green)
                                currentNotes.forEach { note ->
                                    val stalkX = (note.startBeat * cellW) + 4.dp.toPx()
                                    val stalkH = (h * 0.35f) * note.velocity
                                    val topY = (h * 0.35f) - stalkH

                                    drawLine(
                                        color = accentLime,
                                        start = Offset(stalkX, h * 0.35f),
                                        end = Offset(stalkX, topY),
                                        strokeWidth = 2.dp.toPx()
                                    )
                                    drawCircle(
                                        color = accentLime,
                                        radius = 2.5.dp.toPx(),
                                        center = Offset(stalkX, topY)
                                    )
                                }

                                // 2. Chance Dots & Line (Cyan)
                                val chancePath = Path()
                                var first = true
                                for (i in 0 until (totalBeats).toInt() step 2) {
                                    val cx = i * cellW + 4.dp.toPx()
                                    val cy = (h * 0.45f) + ((i % 4) * 2.dp.toPx())
                                    if (first) { chancePath.moveTo(cx, cy); first = false }
                                    else { chancePath.lineTo(cx, cy) }
                                    drawCircle(accentCyan, radius = 2.dp.toPx(), center = Offset(cx, cy))
                                }
                                drawPath(chancePath, accentCyan.copy(alpha = 0.6f), style = Stroke(width = 1.dp.toPx()))

                                // 3. Ratchet Stepped Lines (Purple)
                                for (i in 0 until (totalBeats).toInt() step 4) {
                                    val rx = i * cellW + 4.dp.toPx()
                                    val rw = cellW * 3
                                    val ry = (h * 0.65f) - ((i % 3) * 6.dp.toPx())
                                    drawRect(
                                        color = Color(0xFFB28DFF).copy(alpha = 0.7f),
                                        topLeft = Offset(rx, ry),
                                        size = Size(rw, 3.dp.toPx())
                                    )
                                }

                                // 4. Pitch Bend Curve (Orange)
                                val pitchPath = Path()
                                val pStartX = 0f
                                val pMidX = (16f) * cellW
                                val pEndX = totalBeats * cellW
                                pitchPath.moveTo(pStartX, h * 0.85f)
                                pitchPath.cubicTo(
                                    pMidX - 100f, h * 0.85f,
                                    pMidX, h * 0.72f,
                                    pMidX + 100f, h * 0.85f
                                )
                                pitchPath.lineTo(pEndX, h * 0.85f)
                                drawPath(pitchPath, Color(0xFFFF9500), style = Stroke(width = 1.5.dp.toPx()))

                                // Pitch Nodes
                                drawCircle(Color.White, radius = 3.dp.toPx(), center = Offset(pMidX, h * 0.72f))

                                // 5. Pressure / Slide bars (Blue / Teal)
                                for (i in 0 until (totalBeats).toInt() step 2) {
                                    val bx = i * cellW + 2.dp.toPx()
                                    drawRect(
                                        color = Color(0xFF42A5F5).copy(alpha = 0.7f),
                                        topLeft = Offset(bx, h - 8.dp.toPx()),
                                        size = Size(2.dp.toPx(), 6.dp.toPx())
                                    )
                                }

                                // Playhead Sync Line in Expression Lane
                                val playheadX = ((playbackPosition % totalBeats)) * cellW
                                drawLine(
                                    color = Color.White.copy(alpha = 0.8f),
                                    start = Offset(if (isPlaying) playheadX else 16f * cellW, 0f),
                                    end = Offset(if (isPlaying) playheadX else 16f * cellW, h),
                                    strokeWidth = 1.5.dp.toPx()
                                )
                            }
                        }
                    }
                }
            }

            // ------------------------------------------
            // 3C. RIGHT INSPECTOR: SELECTED NOTES — 4
            // ------------------------------------------
            Column(
                modifier = Modifier
                    .width(135.dp)
                    .fillMaxHeight()
                    .background(panelBg)
                    .border(0.5.dp, borderDark)
                    .padding(8.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SELECTED NOTES — 4",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp,
                        color = accentLime
                    )
                    Icon(
                        imageVector = Icons.Default.HelpOutline,
                        contentDescription = "Help",
                        tint = Color(0xFF6B7A8D),
                        modifier = Modifier.size(12.dp)
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Position", fontSize = 9.sp, color = Color(0xFF8A98A8))
                    Text(inspectorPosition, fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = Color.White)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Length", fontSize = 9.sp, color = Color(0xFF8A98A8))
                    Text(inspectorLength, fontSize = 9.sp, color = Color.White)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Pitch", fontSize = 9.sp, color = Color(0xFF8A98A8))
                    Text(inspectorPitch, fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }

                // Velocity Slider
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Velocity", fontSize = 9.sp, color = Color(0xFF8A98A8))
                        Text("${inspectorVelocity.toInt()}", fontSize = 9.sp, color = accentLime, fontFamily = FontFamily.Monospace)
                    }
                    Slider(
                        value = inspectorVelocity,
                        onValueChange = { inspectorVelocity = it },
                        valueRange = 1f..127f,
                        modifier = Modifier.height(16.dp),
                        colors = SliderDefaults.colors(thumbColor = accentLime, activeTrackColor = accentLime, inactiveTrackColor = Color(0xFF1B202B))
                    )
                }

                // Chance Slider
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Chance", fontSize = 9.sp, color = Color(0xFF8A98A8))
                        Text("${inspectorChance.toInt()}%", fontSize = 9.sp, color = accentCyan, fontFamily = FontFamily.Monospace)
                    }
                    Slider(
                        value = inspectorChance,
                        onValueChange = { inspectorChance = it },
                        valueRange = 0f..100f,
                        modifier = Modifier.height(16.dp),
                        colors = SliderDefaults.colors(thumbColor = accentCyan, activeTrackColor = accentCyan, inactiveTrackColor = Color(0xFF1B202B))
                    )
                }

                // Ratchet
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Ratchet", fontSize = 9.sp, color = Color(0xFF8A98A8))
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF0D0F14), RoundedCornerShape(2.dp))
                            .border(0.5.dp, borderDark, RoundedCornerShape(2.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("$inspectorRatchet", fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                // Nudge Buttons < >
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Nudge", fontSize = 9.sp, color = Color(0xFF8A98A8))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .background(Color(0xFF1B202B), RoundedCornerShape(2.dp))
                                .clickable { viewModel.showToast("Nudge left") },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("<", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .background(Color(0xFF1B202B), RoundedCornerShape(2.dp))
                                .clickable { viewModel.showToast("Nudge right") },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(">", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Mute Note Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Mute", fontSize = 9.sp, color = Color(0xFF8A98A8))
                    Switch(
                        checked = isNoteMuted,
                        onCheckedChange = { isNoteMuted = it },
                        modifier = Modifier.height(20.dp),
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFFD32F2F))
                    )
                }
            }
        }

        // ==========================================
        // 4. BOTTOM TOOLBAR & STATUS BAR
        // ==========================================
        Surface(
            color = Color(0xFF0C0E13),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, borderDark)
        ) {
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                // Top Row: Tools Palette, Grid, Snap, Zoom, Navigation
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Tool Palette
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        val tools = listOf(
                            PianoRollTool.DRAW to (Icons.Default.Edit to "Draw"),
                            PianoRollTool.SELECT to (Icons.Default.CropFree to "Select"),
                            PianoRollTool.SPLIT to (Icons.Default.ContentCut to "Split"),
                            PianoRollTool.GLUE to (Icons.Default.Link to "Glue"),
                            PianoRollTool.ERASE to (Icons.Default.DeleteOutline to "Erase"),
                            PianoRollTool.AUDITION to (Icons.Default.VolumeUp to "Audition")
                        )
                        tools.forEach { (tool, iconInfo) ->
                            val isSel = activeTool == tool
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (isSel) Color(0xFF1E2838) else Color(0xFF141720))
                                    .border(0.5.dp, if (isSel) accentLime else borderDark, RoundedCornerShape(3.dp))
                                    .clickable { activeTool = tool }
                                    .padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = iconInfo.first,
                                    contentDescription = iconInfo.second,
                                    tint = if (isSel) accentLime else Color(0xFF94A3B8),
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = iconInfo.second,
                                    fontSize = 8.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSel) Color.White else Color(0xFF94A3B8)
                                )
                            }
                        }
                    }

                    // Grid, Snap & Zoom
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Grid", fontSize = 8.sp, color = Color(0xFF8A98A8))
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF141720), RoundedCornerShape(2.dp))
                                .border(0.5.dp, borderDark, RoundedCornerShape(2.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(gridResolution, fontSize = 8.sp, color = Color.White)
                        }

                        Text("Snap", fontSize = 8.sp, color = Color(0xFF8A98A8))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (isSnapOn) Color(0xFF1E2838) else Color(0xFF141720))
                                .border(0.5.dp, if (isSnapOn) accentLime else borderDark, RoundedCornerShape(2.dp))
                                .clickable { isSnapOn = !isSnapOn }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(if (isSnapOn) "On" else "Off", fontSize = 8.sp, color = if (isSnapOn) accentLime else Color(0xFF8A98A8), fontWeight = FontWeight.Bold)
                        }

                        // Zoom Slider
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("-", fontSize = 10.sp, color = Color(0xFF8A98A8))
                            Slider(
                                value = zoomLevel,
                                onValueChange = { zoomLevel = it },
                                valueRange = 0.5f..2.0f,
                                modifier = Modifier.width(60.dp).height(16.dp),
                                colors = SliderDefaults.colors(thumbColor = accentLime, activeTrackColor = accentLime, inactiveTrackColor = Color(0xFF1B202B))
                            )
                            Text("+", fontSize = 10.sp, color = Color(0xFF8A98A8))
                        }

                        // Prev / Next Note Buttons
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color(0xFF141720))
                                    .border(0.5.dp, borderDark, RoundedCornerShape(2.dp))
                                    .clickable { viewModel.showToast("Previous Note") }
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Text("← Prev Note", fontSize = 7.5.sp, color = Color(0xFF94A3B8))
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color(0xFF141720))
                                    .border(0.5.dp, borderDark, RoundedCornerShape(2.dp))
                                    .clickable { viewModel.showToast("Next Note") }
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Text("Next Note →", fontSize = 7.5.sp, color = Color(0xFF94A3B8))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Bottom Status Line: Selection Info, Breadcrumbs, Capture & Commit
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Status: Selection 4 Notes | Current Pitch F2 | Position 21.1.1
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Selection: 4 Notes", fontSize = 8.sp, color = Color(0xFF8A98A8))
                        Text("Current Pitch: F2", fontSize = 8.sp, color = Color(0xFF8A98A8))
                        Text("Position: 21.1.1", fontSize = 8.sp, color = Color(0xFF8A98A8), fontFamily = FontFamily.Monospace)
                    }

                    // Center Breadcrumbs: Bass > Bass Drop > Canopy
                    Text(
                        text = "Bass  >  Bass Drop  >  Canopy",
                        fontSize = 8.sp,
                        color = Color(0xFF6B7A8D),
                        fontFamily = FontFamily.Monospace
                    )

                    // Right Actions: Undo, Capture, Commit
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "↶ Undo",
                            fontSize = 8.sp,
                            color = Color(0xFF94A3B8),
                            modifier = Modifier.clickable { viewModel.undoAction() }
                        )

                        // Capture (Cyan Pill Button)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xFF003840))
                                .border(1.dp, accentCyan, RoundedCornerShape(3.dp))
                                .clickable { viewModel.showToast("Captured MIDI Pattern!") }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Icon(Icons.Default.RadioButtonChecked, contentDescription = null, tint = accentCyan, modifier = Modifier.size(10.dp))
                                Text("Capture", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = accentCyan)
                            }
                        }

                        // Commit (Bright Lime Green Button with Checkmark)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(accentLime)
                                .clickable {
                                    viewModel.showToast("Committed to Arrangement!")
                                    viewModel.selectTab(DawTab.ARRANGER)
                                }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(12.dp))
                                Text("Commit", fontSize = 8.5.sp, fontWeight = FontWeight.Black, color = Color.Black)
                            }
                        }
                    }
                }
            }
        }
    }
}

