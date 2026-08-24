package com.example.ui.components.earth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.earth.EarthColorTokens
import com.example.ui.theme.earth.EarthTheme
import com.example.ui.theme.earth.earthGlass

/**
 * Precision Crystal Volume Fader (Vertical Slider).
 */
@Composable
fun PrecisionCrystalFader(
    volumeDb: Float, // -60.0 .. +6.0 dB
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 140.dp,
    onVolumeChange: (Float) -> Unit
) {
    // Normalized 0.0 .. 1.0 mapping from -60 to +6 dB (0dB unity = ~0.80)
    val norm = ((volumeDb + 60f) / 66f).coerceIn(0f, 1f)
    var isDragging by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .width(32.dp)
            .height(height)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val deltaNorm = -dragAmount.y / (height.toPx())
                        val newNorm = (norm + deltaNorm).coerceIn(0f, 1f)
                        val newDb = (newNorm * 66f) - 60f
                        onVolumeChange(newDb)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Track Slot
        Canvas(modifier = Modifier.fillMaxSize()) {
            val trackWidth = 2.dp.toPx()
            val trackX = (size.width - trackWidth) / 2
            drawRoundRect(
                color = EarthColorTokens.GlassBorderSubtle,
                topLeft = Offset(trackX, 8.dp.toPx()),
                size = Size(trackWidth, size.height - 16.dp.toPx()),
                cornerRadius = CornerRadius(1.dp.toPx())
            )

            // Unity 0dB mark line
            val unityY = size.height * (1f - (60f / 66f))
            drawLine(
                color = EarthColorTokens.TextSecondary.copy(alpha = 0.5f),
                start = Offset(trackX - 6.dp.toPx(), unityY),
                end = Offset(trackX + trackWidth + 6.dp.toPx(), unityY),
                strokeWidth = 1.dp.toPx()
            )
        }

        // Fader Cap
        val capYPercent = 1f - norm
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(28.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (height - 20.dp) * capYPercent)
                    .size(width = 26.dp, height = 18.dp)
                    .earthGlass(
                        shape = RoundedCornerShape(3.dp),
                        baseColor = EarthColorTokens.GlassSurfaceRaised,
                        borderColor = if (isDragging) EarthColorTokens.EarthAmber else EarthColorTokens.GlassBorderSubtle
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Amber center notch line
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(2.dp)
                        .background(EarthColorTokens.EarthAmber, RoundedCornerShape(1.dp))
                )
            }
        }

        // Floating dB Tooltip when dragging
        if (isDragging) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = -22.dp)
                    .earthGlass(shape = RoundedCornerShape(2.dp), baseColor = EarthColorTokens.GlassEspresso)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = String.format("%.1f dB", volumeDb),
                    style = EarthTheme.typography.paramValue,
                    fontSize = 8.sp,
                    color = EarthColorTokens.EarthAmber
                )
            }
        }
    }
}

/**
 * 24-Segment Stereo LED Level Meter.
 */
@Composable
fun StereoLedLevelMeter(
    levelL: Float, // 0.0 .. 1.0
    levelR: Float, // 0.0 .. 1.0
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 140.dp
) {
    Row(
        modifier = modifier
            .width(10.dp)
            .height(height)
            .background(EarthColorTokens.BgObsidianDeep, RoundedCornerShape(2.dp))
            .border(0.5.dp, EarthColorTokens.GlassBorderSubtle, RoundedCornerShape(2.dp))
            .padding(1.dp),
        horizontalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        SingleMeterBar(level = levelL, modifier = Modifier.weight(1f).fillMaxHeight())
        SingleMeterBar(level = levelR, modifier = Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun SingleMeterBar(
    level: Float,
    modifier: Modifier = Modifier
) {
    val segmentCount = 20

    Canvas(modifier = modifier) {
        val segHeight = size.height / segmentCount
        val segGap = 1.dp.toPx()

        for (i in 0 until segmentCount) {
            val segIndex = segmentCount - 1 - i
            val segThreshold = i.toFloat() / segmentCount
            val isLit = level >= segThreshold

            val segColor = when {
                i >= 18 -> EarthColorTokens.MeterClipRed
                i >= 14 -> EarthColorTokens.MeterAutumnRust
                i >= 9 -> EarthColorTokens.MeterAutumnAmber
                else -> EarthColorTokens.MeterNatureGreen
            }

            drawRect(
                color = if (isLit) segColor else segColor.copy(alpha = 0.12f),
                topLeft = Offset(0f, segIndex * segHeight + segGap / 2),
                size = Size(size.width, segHeight - segGap)
            )
        }
    }
}

/**
 * Solo, Mute, and Record Arm Button Group.
 */
@Composable
fun SoloMuteArmToggles(
    isSoloed: Boolean,
    isMuted: Boolean,
    isArmed: Boolean,
    onToggleSolo: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleArm: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Solo (S)
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (isSoloed) EarthColorTokens.AutumnHarvestGold else EarthColorTokens.GlassSurface)
                .border(0.5.dp, if (isSoloed) EarthColorTokens.AutumnHarvestGold else EarthColorTokens.GlassBorderSubtle, RoundedCornerShape(2.dp))
                .clickable { onToggleSolo() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "S",
                style = EarthTheme.typography.microBadge,
                color = if (isSoloed) Color.Black else EarthColorTokens.TextSecondary
            )
        }

        // Mute (M)
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (isMuted) EarthColorTokens.AutumnTerracotta else EarthColorTokens.GlassSurface)
                .border(0.5.dp, if (isMuted) EarthColorTokens.AutumnTerracotta else EarthColorTokens.GlassBorderSubtle, RoundedCornerShape(2.dp))
                .clickable { onToggleMute() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "M",
                style = EarthTheme.typography.microBadge,
                color = if (isMuted) Color.White else EarthColorTokens.TextSecondary
            )
        }

        // Arm (A / Rec)
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (isArmed) EarthColorTokens.AutumnCrimsonMaple else EarthColorTokens.GlassSurface)
                .border(0.5.dp, if (isArmed) EarthColorTokens.AutumnCrimsonMaple else EarthColorTokens.GlassBorderSubtle, RoundedCornerShape(2.dp))
                .clickable { onToggleArm() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "A",
                style = EarthTheme.typography.microBadge,
                color = if (isArmed) Color.White else EarthColorTokens.TextSecondary
            )
        }
    }
}
