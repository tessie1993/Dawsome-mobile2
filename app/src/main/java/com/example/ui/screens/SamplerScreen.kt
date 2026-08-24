package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.synth.*
import com.example.ui.components.*
import kotlinx.coroutines.delay

@Composable
fun SamplerScreen(
    viewModel: SynthViewModel,
    modifier: Modifier = Modifier
) {
    val engine = viewModel.engine
    val sampler = engine.samplerInstrument
    val currentPresetIdx by viewModel.samplerPresetIndex.collectAsState()
    val mode by viewModel.samplerMode.collectAsState()
    val startPoint by viewModel.samplerStartPoint.collectAsState()
    val endPoint by viewModel.samplerEndPoint.collectAsState()
    val loopStart by viewModel.samplerLoopStart.collectAsState()
    val loopEnd by viewModel.samplerLoopEnd.collectAsState()
    val isLoopEnabled by viewModel.isSamplerLoopEnabled.collectAsState()
    val isReversed by viewModel.isSamplerReversed.collectAsState()
    val transpose by viewModel.samplerTranspose.collectAsState()
    val activeSlice by viewModel.activeSamplerSlice.collectAsState()
    val isAutoArmed by viewModel.isAutomationRecordArmed.collectAsState()

    var showPresetDropdown by remember { mutableStateOf(false) }

    // Live playhead tracking (~60fps)
    var livePlayhead by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            val buf = sampler.sampleBuffer
            if (buf.isNotEmpty()) {
                livePlayhead = (sampler.playheadPos / buf.size).toFloat().coerceIn(0f, 1f)
            }
            delay(16)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AbletonBgDark)
            .verticalScroll(rememberScrollState())
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ==========================================
        // 1. ABLETON SIMPLER HEADER & SAMPLE SELECTOR
        // ==========================================
        Surface(
            color = AbletonSurface,
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(1.dp, AbletonBorder),
            modifier = Modifier.fillMaxWidth().testTag("simpler_header_panel")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Device Title & Icon
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(AbletonCyan, RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "Simpler",
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "ABLETON SIMPLER",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = AbletonCyan,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Creative Sample Instrument & Slicer",
                            fontSize = 9.sp,
                            color = StudioTextSecondary
                        )
                    }
                }

                // Sample Bank Selector Dropdown
                Box {
                    Button(
                        onClick = { showPresetDropdown = true },
                        colors = ButtonDefaults.buttonColors(containerColor = AbletonPanel),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.height(28.dp).testTag("sample_preset_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Audiotrack,
                            contentDescription = null,
                            tint = AbletonCyan,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = SamplerInstrument.getPresetName(currentPresetIdx),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = StudioTextPrimary
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = StudioTextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showPresetDropdown,
                        onDismissRequest = { showPresetDropdown = false },
                        modifier = Modifier.background(AbletonSurface).border(1.dp, AbletonBorder)
                    ) {
                        repeat(SamplerInstrument.SAMPLE_PRESETS_COUNT) { idx ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = SamplerInstrument.getPresetName(idx),
                                        fontSize = 11.sp,
                                        color = if (idx == currentPresetIdx) AbletonCyan else StudioTextPrimary
                                    )
                                },
                                onClick = {
                                    viewModel.selectSamplerPreset(idx)
                                    showPresetDropdown = false
                                }
                            )
                        }
                    }
                }

                // Import Live Recording Button
                Button(
                    onClick = { viewModel.loadRecordedAudioToSampler() },
                    colors = ButtonDefaults.buttonColors(containerColor = AbletonGreen.copy(alpha = 0.2f)),
                    border = BorderStroke(1.dp, AbletonGreen),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.height(28.dp).testTag("load_recording_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Load Take",
                        tint = AbletonGreen,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("LOAD REC TAKE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = AbletonGreen)
                }
            }
        }

        // ==========================================
        // 2. PLAYBACK MODE STRIP (CLASSIC / 1-SHOT / SLICING)
        // ==========================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SamplerPlaybackMode.values().forEach { m ->
                val isSelected = (mode == m)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(30.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isSelected) AbletonCyan else AbletonSurface)
                        .border(1.dp, if (isSelected) AbletonCyan else AbletonBorder, RoundedCornerShape(4.dp))
                        .clickable { viewModel.setSamplerPlaybackMode(m) }
                        .testTag("mode_${m.name.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when (m) {
                            SamplerPlaybackMode.CLASSIC -> "CLASSIC (WARP)"
                            SamplerPlaybackMode.ONE_SHOT -> "1-SHOT (TRIGGER)"
                            SamplerPlaybackMode.SLICING -> "SLICING (8 PADS)"
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color.Black else StudioTextPrimary
                    )
                }
            }
        }

        // ==========================================
        // 3. INTERACTIVE SAMPLE WAVEFORM CANVAS
        // ==========================================
        Surface(
            color = Color(0xFF0F1115),
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(1.dp, AbletonBorder),
            modifier = Modifier.fillMaxWidth().testTag("sampler_waveform_panel")
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                // Status HUD
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "WAVEFORM DISPLAY // ${(sampler.sampleBuffer.size / (SynthEngine.SAMPLE_RATE / 1000f)).toInt()} ms",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = AbletonCyan,
                        fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "START: ${(startPoint * 100).toInt()}%",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            color = AbletonYellow
                        )
                        Text(
                            text = "END: ${(endPoint * 100).toInt()}%",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            color = AbletonOrange
                        )
                        if (isAutoArmed) {
                            Text(
                                text = "AUTO WRITE ACTIVE",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = AbletonRed,
                                modifier = Modifier
                                    .background(AbletonRed.copy(alpha = 0.2f), RoundedCornerShape(2.dp))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Waveform Canvas with Drag Handles & Slices
                val buffer = sampler.sampleBuffer
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF07090C))
                        .pointerInput(mode) {
                            detectTapGestures { offset ->
                                val normX = (offset.x / size.width).coerceIn(0f, 1f)
                                if (mode == SamplerPlaybackMode.SLICING) {
                                    val slice = (normX * 8).toInt().coerceIn(0, 7)
                                    viewModel.triggerSamplerSlice(slice)
                                } else {
                                    // Move start point
                                    viewModel.setSamplerStartPoint(normX)
                                    engine.noteOn(60)
                                }
                            }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                val normX = (change.position.x / size.width).coerceIn(0f, 1f)
                                if (normX < endPoint - 0.05f) {
                                    viewModel.setSamplerStartPoint(normX)
                                } else {
                                    viewModel.setSamplerEndPoint(normX)
                                }
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val midY = h / 2f

                        // 1. Draw Grid lines
                        drawLine(Color(0xFF1E232B), Offset(0f, midY), Offset(w, midY), strokeWidth = 1f)
                        repeat(8) { i ->
                            val x = (w / 8f) * i
                            drawLine(Color(0xFF161A22), Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
                        }

                        // 2. Draw Sample Waveform Peaks
                        if (buffer.isNotEmpty()) {
                            val path = Path()
                            val step = (buffer.size / w.toInt()).coerceAtLeast(1)
                            path.moveTo(0f, midY)

                            for (i in 0 until w.toInt()) {
                                val bufIdx = (i * step).coerceIn(0, buffer.size - 1)
                                val sampleVal = buffer[bufIdx]
                                val y = midY - (sampleVal * (h * 0.44f))
                                path.lineTo(i.toFloat(), y)
                            }
                            drawPath(
                                path = path,
                                color = AbletonCyan.copy(alpha = 0.85f),
                                style = Stroke(width = 1.5f)
                            )
                        }

                        // 3. Draw Loop Range Shading
                        if (isLoopEnabled && mode == SamplerPlaybackMode.CLASSIC) {
                            val lx1 = loopStart * w
                            val lx2 = loopEnd * w
                            drawRect(
                                color = AbletonGreen.copy(alpha = 0.15f),
                                topLeft = Offset(lx1, 0f),
                                size = Size(lx2 - lx1, h)
                            )
                            drawLine(AbletonGreen, Offset(lx1, 0f), Offset(lx1, h), strokeWidth = 2f)
                            drawLine(AbletonGreen, Offset(lx2, 0f), Offset(lx2, h), strokeWidth = 2f)
                        }

                        // 4. Draw Slicing Grid Markers
                        if (mode == SamplerPlaybackMode.SLICING) {
                            repeat(8) { s ->
                                val sx = (w / 8f) * s
                                drawLine(
                                    color = if (s == activeSlice) AbletonYellow else AbletonBorder,
                                    start = Offset(sx, 0f),
                                    end = Offset(sx, h),
                                    strokeWidth = if (s == activeSlice) 2.5f else 1f
                                )
                            }
                        }

                        // 5. Draw Start & End Markers
                        val sx = startPoint * w
                        val ex = endPoint * w
                        // Inactive region shading
                        if (sx > 0f) {
                            drawRect(Color(0x88000000), Offset(0f, 0f), Size(sx, h))
                        }
                        if (ex < w) {
                            drawRect(Color(0x88000000), Offset(ex, 0f), Size(w - ex, h))
                        }
                        // Start handle
                        drawLine(AbletonYellow, Offset(sx, 0f), Offset(sx, h), strokeWidth = 3f)
                        drawCircle(AbletonYellow, radius = 5f, center = Offset(sx, 10f))
                        // End handle
                        drawLine(AbletonOrange, Offset(ex, 0f), Offset(ex, h), strokeWidth = 3f)
                        drawCircle(AbletonOrange, radius = 5f, center = Offset(ex, h - 10f))

                        // 6. Live Playhead Indicator
                        val px = livePlayhead * w
                        if (px in 0f..w) {
                            drawLine(Color.White, Offset(px, 0f), Offset(px, h), strokeWidth = 2f)
                            drawCircle(Color.White, radius = 4f, center = Offset(px, midY))
                        }
                    }
                }
            }
        }

        // ==========================================
        // 4. PERFORMANCE SECTION (8 SLICE PADS OR KEYBOARD)
        // ==========================================
        if (mode == SamplerPlaybackMode.SLICING) {
            Surface(
                color = AbletonSurface,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, AbletonBorder),
                modifier = Modifier.fillMaxWidth().testTag("sampler_slice_pads")
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        text = "8-PAD TRANSIENT SLICER (TAP TO TRIGGER)",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = AbletonYellow,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        repeat(8) { sliceIdx ->
                            val isActive = (activeSlice == sliceIdx)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(54.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isActive) AbletonYellow else AbletonPanel)
                                    .border(1.dp, if (isActive) Color.White else AbletonBorder, RoundedCornerShape(4.dp))
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onPress = {
                                                viewModel.triggerSamplerSlice(sliceIdx)
                                            }
                                        )
                                    }
                                    .testTag("slice_pad_$sliceIdx"),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "PAD ${sliceIdx + 1}",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isActive) Color.Black else StudioTextPrimary
                                    )
                                    Text(
                                        text = "1/8",
                                        fontSize = 7.sp,
                                        color = if (isActive) Color.Black.copy(alpha = 0.7f) else StudioTextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // 5. ABLETON PARAMETER CONTROL STRIP
        // ==========================================
        Surface(
            color = AbletonSurface,
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(1.dp, AbletonBorder),
            modifier = Modifier.fillMaxWidth().testTag("simpler_controls_panel")
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = "PITCH, FILTER & ENVELOPE CONTROLS",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = AbletonCyan,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Transpose Knob (-24 to +24)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("TRANSPOSE", fontSize = 8.sp, color = StudioTextSecondary, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(AbletonPanel)
                                    .clickable { viewModel.setSamplerTranspose(transpose - 1) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("-", fontSize = 12.sp, color = StudioTextPrimary, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                text = "${if (transpose > 0) "+" else ""}$transpose st",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = AbletonCyan,
                                modifier = Modifier.padding(horizontal = 6.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(AbletonPanel)
                                    .clickable { viewModel.setSamplerTranspose(transpose + 1) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("+", fontSize = 12.sp, color = StudioTextPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Reverse Button Toggle
                    Box(
                        modifier = Modifier
                            .height(36.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isReversed) AbletonOrange else AbletonPanel)
                            .border(1.dp, if (isReversed) AbletonOrange else AbletonBorder, RoundedCornerShape(4.dp))
                            .clickable { viewModel.toggleSamplerReverse() }
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = "Reverse",
                                tint = if (isReversed) Color.Black else StudioTextPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "REV",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isReversed) Color.Black else StudioTextPrimary
                            )
                        }
                    }

                    // Loop Mode Toggle
                    Box(
                        modifier = Modifier
                            .height(36.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isLoopEnabled) AbletonGreen else AbletonPanel)
                            .border(1.dp, if (isLoopEnabled) AbletonGreen else AbletonBorder, RoundedCornerShape(4.dp))
                            .clickable { viewModel.toggleSamplerLoop() }
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Repeat,
                                contentDescription = "Loop",
                                tint = if (isLoopEnabled) Color.Black else StudioTextPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "LOOP",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isLoopEnabled) Color.Black else StudioTextPrimary
                            )
                        }
                    }

                    // Filter Cutoff
                    StudioKnob(
                        value = (engine.filterCutoff / 16000f).coerceIn(0f, 1f),
                        label = "CUTOFF",
                        displayValue = if (engine.filterCutoff >= 1000f) "${(engine.filterCutoff / 1000f).toInt()}k" else "${engine.filterCutoff.toInt()}",
                        accentColor = AbletonCyan,
                        onValueChange = {
                            val hz = it * 16000f
                            engine.filterCutoff = hz
                            viewModel.recordParameterAutomation(AutomationParameter.FILTER_CUTOFF, SessionTrackType.LEAD, it)
                        }
                    )

                    // Amp Attack
                    StudioKnob(
                        value = (sampler.attackTime / 2f).coerceIn(0f, 1f),
                        label = "ATTACK",
                        displayValue = "${(sampler.attackTime * 1000).toInt()}ms",
                        accentColor = AbletonYellow,
                        onValueChange = { sampler.attackTime = (it * 2f).coerceIn(0.005f, 2f) }
                    )

                    // Amp Release
                    StudioKnob(
                        value = (sampler.releaseTime / 3f).coerceIn(0f, 1f),
                        label = "RELEASE",
                        displayValue = "${(sampler.releaseTime * 1000).toInt()}ms",
                        accentColor = AbletonOrange,
                        onValueChange = { sampler.releaseTime = (it * 3f).coerceIn(0.01f, 3f) }
                    )
                }
            }
        }

        // ==========================================
        // 6. TOUCH KEYBOARD / CHROMATIC AUDITION
        // ==========================================
        Surface(
            color = AbletonSurface,
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(1.dp, AbletonBorder),
            modifier = Modifier.fillMaxWidth().testTag("sampler_touch_keyboard")
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = "CHROMATIC KEYBOARD (POLYPHONIC TRIGGER)",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = AbletonGreen,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black)
                ) {
                    val baseMidi = 60 // C4
                    val whiteNotes = listOf(0, 2, 4, 5, 7, 9, 11, 12, 14, 16, 17, 19, 21, 23, 24)
                    val blackNotes = listOf(1, 3, 6, 8, 10, 13, 15, 18, 20, 22)

                    // White Keys Layer
                    Row(modifier = Modifier.fillMaxSize()) {
                        whiteNotes.forEach { offset ->
                            val pitch = baseMidi + offset
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .padding(horizontal = 0.5.dp)
                                    .clip(RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp))
                                    .background(Color(0xFFE8E8E8))
                                    .border(1.dp, Color(0xFF333333))
                                    .pointerInput(pitch) {
                                        detectTapGestures(
                                            onPress = {
                                                viewModel.selectInstrument(InstrumentType.SAMPLER)
                                                engine.noteOn(pitch)
                                                tryAwaitRelease()
                                                engine.noteOff(pitch)
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Text(getNoteName(pitch), fontSize = 7.sp, color = Color.Black, modifier = Modifier.padding(bottom = 2.dp))
                            }
                        }
                    }

                    // Black Keys Layer
                    Row(modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        val blackLayout = listOf(
                            true, true, false, true, true, true, false,
                            true, true, false, true, true, true, false
                        )
                        var blackIndex = 0
                        blackLayout.forEach { hasBlack ->
                            if (hasBlack && blackIndex < blackNotes.size) {
                                val pitch = baseMidi + blackNotes[blackIndex]
                                Box(
                                    modifier = Modifier
                                        .weight(0.7f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(bottomStart = 2.dp, bottomEnd = 2.dp))
                                        .background(Color(0xFF1A1A1A))
                                        .border(0.5.dp, Color.Black)
                                        .pointerInput(pitch) {
                                            detectTapGestures(
                                                onPress = {
                                                    viewModel.selectInstrument(InstrumentType.SAMPLER)
                                                    engine.noteOn(pitch)
                                                    tryAwaitRelease()
                                                    engine.noteOff(pitch)
                                                }
                                            )
                                        }
                                )
                                blackIndex++
                            } else {
                                Spacer(modifier = Modifier.weight(0.7f))
                            }
                        }
                    }
                }
            }
        }
    }
}
