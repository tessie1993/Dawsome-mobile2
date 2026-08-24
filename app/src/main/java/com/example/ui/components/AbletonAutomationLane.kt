package com.example.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.synth.*
import kotlin.math.abs

// Ableton UI Dark Theme Colors
private val AutoDarkBg = Color(0xFF11141A)
private val AutoSurface = Color(0xFF1B1F2A)
private val AutoHeader = Color(0xFF262C3B)
private val AutoBorder = Color(0xFF323B4E)
private val AutoOrange = Color(0xFFFF764D)
private val AutoBlue = Color(0xFF00B0FF)
private val AutoGreen = Color(0xFF00E676)
private val AutoYellow = Color(0xFFFED142)
private val AutoTextPrimary = Color(0xFFF0F4F8)
private val AutoTextSecondary = Color(0xFF8C9BAE)

@Composable
fun AbletonAutomationLane(
    track: ArrangementTrack,
    totalBars: Int,
    baseBarWidthDp: Dp,
    trackHeaderWidthDp: Dp,
    horizontalScrollState: ScrollState,
    currentBar: Float,
    viewModel: SynthViewModel,
    modifier: Modifier = Modifier
) {
    val selectedParam = track.selectedAutomationParam
    val currentLane = track.automationLanes[selectedParam] ?: AutomationLane.defaultLane(selectedParam)
    val liveAutomatedValues by viewModel.liveAutomatedValues.collectAsState()

    // Determine parameter theme accent color
    val paramColor = when (selectedParam) {
        AutomationParameter.FILTER_CUTOFF -> AutoOrange
        AutomationParameter.VOLUME -> AutoBlue
        AutomationParameter.PAN -> AutoGreen
        AutomationParameter.FILTER_RESONANCE -> AutoYellow
        AutomationParameter.REVERB_SEND -> Color(0xFFB28DFF)
        AutomationParameter.DELAY_SEND -> Color(0xFFFF9E80)
        else -> AutoOrange
    }

    var showParamMenu by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF0E1118))
            .border(BorderStroke(0.5.dp, Color(0xFF232A38)))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // --- 1. LEFT HEADER PANEL ---
            Column(
                modifier = Modifier
                    .width(trackHeaderWidthDp)
                    .fillMaxHeight()
                    .background(AutoSurface)
                    .border(BorderStroke(0.5.dp, AutoBorder))
                    .padding(6.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Tag & Parameter Dropdown Trigger
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(paramColor, CircleShape)
                        )
                        Text(
                            text = "AUTOMATION",
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = paramColor
                        )
                    }

                    // Value Readout
                    val liveValKey = when (track.trackType) {
                        SessionTrackType.LEAD -> when (selectedParam) {
                            AutomationParameter.FILTER_CUTOFF -> "lead_cutoff"
                            AutomationParameter.VOLUME -> "lead_volume"
                            AutomationParameter.PAN -> "lead_pan"
                            AutomationParameter.FILTER_RESONANCE -> "lead_resonance"
                            AutomationParameter.REVERB_SEND -> "reverb_send"
                            AutomationParameter.DELAY_SEND -> "delay_send"
                            else -> null
                        }
                        SessionTrackType.BASS -> when (selectedParam) {
                            AutomationParameter.FILTER_CUTOFF -> "bass_cutoff"
                            AutomationParameter.VOLUME -> "bass_volume"
                            AutomationParameter.PAN -> "bass_pan"
                            AutomationParameter.FILTER_RESONANCE -> "bass_resonance"
                            else -> null
                        }
                        SessionTrackType.DRUMS -> null
                    }
                    val currentLiveVal = liveValKey?.let { liveAutomatedValues[it] } ?: currentLane.getValueAtBeat(currentBar * 4f)
                    val normVal = selectedParam.toNormalizedValue(currentLiveVal)

                    Text(
                        text = selectedParam.formatValue(normVal),
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // Parameter Selector Dropdown Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(3.dp))
                        .background(AutoHeader)
                        .clickable { showParamMenu = true }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selectedParam.displayName,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Select Parameter",
                            tint = AutoTextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showParamMenu,
                        onDismissRequest = { showParamMenu = false },
                        modifier = Modifier.background(AutoSurface)
                    ) {
                        val availableParams = listOf(
                            AutomationParameter.FILTER_CUTOFF,
                            AutomationParameter.VOLUME,
                            AutomationParameter.PAN,
                            AutomationParameter.FILTER_RESONANCE,
                            AutomationParameter.REVERB_SEND,
                            AutomationParameter.DELAY_SEND
                        )
                        availableParams.forEach { param ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .background(
                                                    when (param) {
                                                        AutomationParameter.FILTER_CUTOFF -> AutoOrange
                                                        AutomationParameter.VOLUME -> AutoBlue
                                                        AutomationParameter.PAN -> AutoGreen
                                                        AutomationParameter.FILTER_RESONANCE -> AutoYellow
                                                        else -> AutoTextSecondary
                                                    },
                                                    CircleShape
                                                )
                                        )
                                        Text(
                                            text = param.displayName,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = if (param == selectedParam) FontWeight.Bold else FontWeight.Normal,
                                            color = if (param == selectedParam) AutoOrange else Color.White
                                        )
                                    }
                                },
                                onClick = {
                                    viewModel.setTrackAutomationParam(track.id, param)
                                    showParamMenu = false
                                }
                            )
                        }
                    }
                }

                // Preset Shape Toolbar (Ramp, Sine, Triangle, Random, Reset)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AutomationShapeButton("↗", "Ramp Up") {
                        viewModel.applyTrackAutomationCurve(track.id, selectedParam, "Ramp Up")
                    }
                    AutomationShapeButton("↘", "Ramp Down") {
                        viewModel.applyTrackAutomationCurve(track.id, selectedParam, "Ramp Down")
                    }
                    AutomationShapeButton("∿", "Sine LFO") {
                        viewModel.applyTrackAutomationCurve(track.id, selectedParam, "Sine LFO")
                    }
                    AutomationShapeButton("⋀", "Triangle") {
                        viewModel.applyTrackAutomationCurve(track.id, selectedParam, "Triangle")
                    }
                    AutomationShapeButton("⎍", "Random Steps") {
                        viewModel.applyTrackAutomationCurve(track.id, selectedParam, "Random Steps")
                    }
                    AutomationShapeButton("━", "Reset Flat") {
                        viewModel.clearTrackAutomation(track.id, selectedParam)
                    }
                }
            }

            // --- 2. INTERACTIVE AUTOMATION DRAWING CANVAS ---
            Box(
                modifier = Modifier
                    .horizontalScroll(horizontalScrollState)
                    .fillMaxHeight()
            ) {
                AbletonAutomationCurveCanvas(
                    totalBars = totalBars,
                    baseBarWidthDp = baseBarWidthDp,
                    currentBar = currentBar,
                    parameter = selectedParam,
                    points = currentLane.points,
                    color = paramColor,
                    onAddOrUpdatePoint = { beat, normVal ->
                        viewModel.setTrackAutomationPoint(track.id, selectedParam, beat, normVal)
                    },
                    onContinuousDraw = { beat, normVal ->
                        viewModel.drawContinuousTrackAutomation(track.id, selectedParam, beat, normVal)
                    },
                    onRemovePoint = { beat ->
                        viewModel.removeTrackAutomationPoint(track.id, selectedParam, beat)
                    }
                )
            }
        }
    }
}

@Composable
private fun AutomationShapeButton(
    label: String,
    tooltip: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(19.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(AutoHeader)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = AutoTextSecondary
        )
    }
}

@Composable
fun AbletonAutomationCurveCanvas(
    totalBars: Int,
    baseBarWidthDp: Dp,
    currentBar: Float,
    parameter: AutomationParameter,
    points: List<AutomationPoint>,
    color: Color,
    onAddOrUpdatePoint: (Float, Float) -> Unit,
    onContinuousDraw: (Float, Float) -> Unit,
    onRemovePoint: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val totalBeats = totalBars * 4f
    var isDrawing by remember { mutableStateOf(false) }
    var activeTouchOffset by remember { mutableStateOf<Offset?>(null) }
    var activeTouchValueText by remember { mutableStateOf("") }
    var activeTouchBeatText by remember { mutableStateOf("") }

    val sortedPoints = remember(points) {
        if (points.isEmpty()) {
            val def = parameter.toNormalizedValue(parameter.defaultValue)
            listOf(AutomationPoint(0f, def), AutomationPoint(totalBeats, def))
        } else {
            points.sortedBy { it.beat }
        }
    }

    Box(
        modifier = modifier
            .width((totalBars * baseBarWidthDp.value).dp)
            .fillMaxHeight()
            .pointerInput(baseBarWidthDp, totalBars, parameter) {
                detectTapGestures(
                    onTap = { offset ->
                        val barWidthPx = baseBarWidthDp.toPx()
                        val beat = ((offset.x / barWidthPx) * 4f).coerceIn(0f, totalBeats)
                        val normVal = (1.0f - (offset.y / size.height.toFloat())).coerceIn(0f, 1f)
                        onAddOrUpdatePoint(beat, normVal)
                    },
                    onLongPress = { offset ->
                        val barWidthPx = baseBarWidthDp.toPx()
                        val beat = ((offset.x / barWidthPx) * 4f).coerceIn(0f, totalBeats)
                        onRemovePoint(beat)
                    },
                    onDoubleTap = { offset ->
                        val barWidthPx = baseBarWidthDp.toPx()
                        val beat = ((offset.x / barWidthPx) * 4f).coerceIn(0f, totalBeats)
                        onRemovePoint(beat)
                    }
                )
            }
            .pointerInput(baseBarWidthDp, totalBars, parameter) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isDrawing = true
                        activeTouchOffset = offset
                        val barWidthPx = baseBarWidthDp.toPx()
                        val beat = ((offset.x / barWidthPx) * 4f).coerceIn(0f, totalBeats)
                        val normVal = (1.0f - (offset.y / size.height.toFloat())).coerceIn(0f, 1f)
                        activeTouchValueText = parameter.formatValue(normVal)
                        activeTouchBeatText = "Bar ${(beat / 4f + 1f).toInt()}.${((beat % 4f) + 1f).toInt()}"
                        onContinuousDraw(beat, normVal)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val offset = change.position
                        activeTouchOffset = offset
                        val barWidthPx = baseBarWidthDp.toPx()
                        val beat = ((offset.x / barWidthPx) * 4f).coerceIn(0f, totalBeats)
                        val normVal = (1.0f - (offset.y / size.height.toFloat())).coerceIn(0f, 1f)
                        activeTouchValueText = parameter.formatValue(normVal)
                        activeTouchBeatText = "Bar ${(beat / 4f + 1f).toInt()}.${((beat % 4f) + 1f).toInt()}"
                        onContinuousDraw(beat, normVal)
                    },
                    onDragEnd = {
                        isDrawing = false
                        activeTouchOffset = null
                    },
                    onDragCancel = {
                        isDrawing = false
                        activeTouchOffset = null
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val totalWidth = size.width
            val totalHeight = size.height
            val barWidthPx = totalWidth / totalBars.toFloat()

            // 1. Draw Bar & Beat Grid Lines
            for (b in 0 until totalBars) {
                val x = b * barWidthPx
                drawLine(
                    color = Color(0xFF1E2432),
                    start = Offset(x, 0f),
                    end = Offset(x, totalHeight),
                    strokeWidth = 1f
                )
                // Sub-beats (quarter notes)
                for (quarter in 1..3) {
                    val qx = x + quarter * (barWidthPx / 4f)
                    drawLine(
                        color = Color(0xFF141822),
                        start = Offset(qx, 0f),
                        end = Offset(qx, totalHeight),
                        strokeWidth = 0.5f
                    )
                }
            }

            // Center / Middle Guideline (50% value)
            drawLine(
                color = Color(0x22FFFFFF),
                start = Offset(0f, totalHeight * 0.5f),
                end = Offset(totalWidth, totalHeight * 0.5f),
                strokeWidth = 0.8f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
            )

            // 2. Build Smooth Automation Curve Path & Filled Gradient Area
            if (sortedPoints.isNotEmpty()) {
                val curvePath = Path()
                val fillPath = Path()

                val firstPoint = sortedPoints.first()
                val firstX = (firstPoint.beat / totalBeats) * totalWidth
                val firstY = (1.0f - firstPoint.normalizedValue) * totalHeight

                curvePath.moveTo(firstX, firstY)
                fillPath.moveTo(0f, totalHeight)
                fillPath.lineTo(firstX, firstY)

                for (i in 0 until sortedPoints.size - 1) {
                    val p0 = sortedPoints[i]
                    val p1 = sortedPoints[i + 1]

                    val x0 = (p0.beat / totalBeats) * totalWidth
                    val y0 = (1.0f - p0.normalizedValue) * totalHeight
                    val x1 = (p1.beat / totalBeats) * totalWidth
                    val y1 = (1.0f - p1.normalizedValue) * totalHeight

                    // Smooth cubic bezier segment
                    val cx0 = x0 + (x1 - x0) * 0.5f
                    val cy0 = y0
                    val cx1 = x0 + (x1 - x0) * 0.5f
                    val cy1 = y1

                    curvePath.cubicTo(cx0, cy0, cx1, cy1, x1, y1)
                    fillPath.cubicTo(cx0, cy0, cx1, cy1, x1, y1)
                }

                // Close fill path
                val lastPoint = sortedPoints.last()
                val lastX = (lastPoint.beat / totalBeats) * totalWidth
                fillPath.lineTo(lastX, totalHeight)
                fillPath.close()

                // Draw Gradient Shading under curve
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(color.copy(alpha = 0.35f), color.copy(alpha = 0.05f)),
                        startY = 0f,
                        endY = totalHeight
                    )
                )

                // Draw Main Bright Automation Vector Line
                drawPath(
                    path = curvePath,
                    color = color,
                    style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                // 3. Draw Breakpoint Nodes
                for (pt in sortedPoints) {
                    val px = (pt.beat / totalBeats) * totalWidth
                    val py = (1.0f - pt.normalizedValue) * totalHeight

                    // Outer node glow ring
                    drawCircle(
                        color = color.copy(alpha = 0.4f),
                        radius = 7f,
                        center = Offset(px, py)
                    )
                    // Node outer ring
                    drawCircle(
                        color = Color.White,
                        radius = 4.5f,
                        center = Offset(px, py)
                    )
                    // Node core
                    drawCircle(
                        color = color,
                        radius = 2.5f,
                        center = Offset(px, py)
                    )
                }
            }

            // 4. Live Real-Time Playhead Intercept Marker
            val playheadX = (currentBar / totalBars.toFloat()) * totalWidth
            if (playheadX in 0f..totalWidth) {
                // Find current automated value at this exact beat
                val currentBeat = currentBar * 4f
                val currentNormVal = getInterpolatedNormValue(sortedPoints, currentBeat)
                val playheadY = (1.0f - currentNormVal) * totalHeight

                // Draw vertical scanning line segment
                drawLine(
                    color = Color.White.copy(alpha = 0.8f),
                    start = Offset(playheadX, 0f),
                    end = Offset(playheadX, totalHeight),
                    strokeWidth = 1f
                )

                // Glowing tracker dot on the curve
                drawCircle(
                    color = color,
                    radius = 6f,
                    center = Offset(playheadX, playheadY)
                )
                drawCircle(
                    color = Color.White,
                    radius = 3f,
                    center = Offset(playheadX, playheadY)
                )
            }
        }

        // 5. Floating Value Tooltip Badge on Touch/Drag
        if (isDrawing && activeTouchOffset != null) {
            val touch = activeTouchOffset!!
            val tooltipXDp = (touch.x / androidx.compose.ui.platform.LocalDensity.current.density).dp
            val tooltipYDp = (touch.y / androidx.compose.ui.platform.LocalDensity.current.density).dp - 28.dp

            Box(
                modifier = Modifier
                    .offset(x = (tooltipXDp - 40.dp).coerceAtLeast(4.dp), y = tooltipYDp.coerceAtLeast(4.dp))
                    .background(Color(0xE610141D), RoundedCornerShape(4.dp))
                    .border(1.dp, color, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = activeTouchBeatText,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        color = AutoTextSecondary
                    )
                    Text(
                        text = activeTouchValueText,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                }
            }
        }
    }
}

private fun getInterpolatedNormValue(points: List<AutomationPoint>, beat: Float): Float {
    if (points.isEmpty()) return 0.5f
    val sorted = points.sortedBy { it.beat }
    if (beat <= sorted.first().beat) return sorted.first().normalizedValue
    if (beat >= sorted.last().beat) return sorted.last().normalizedValue

    for (i in 0 until sorted.size - 1) {
        val p0 = sorted[i]
        val p1 = sorted[i + 1]
        if (beat >= p0.beat && beat <= p1.beat) {
            val span = p1.beat - p0.beat
            val fraction = if (span > 0.0001f) (beat - p0.beat) / span else 0f
            return (p0.normalizedValue + fraction * (p1.normalizedValue - p0.normalizedValue)).coerceIn(0f, 1f)
        }
    }
    return sorted.last().normalizedValue
}
