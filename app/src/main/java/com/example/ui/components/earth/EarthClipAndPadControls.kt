package com.example.ui.components.earth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.synth.domain.AutomationPoint
import com.example.synth.domain.DrumPadType
import com.example.ui.theme.earth.EarthColorTokens
import com.example.ui.theme.earth.EarthTheme
import com.example.ui.theme.earth.earthGlass

/**
 * Session Clip Launcher Tile.
 */
@Composable
fun ClipLauncherTile(
    name: String,
    hasContent: Boolean,
    isPlaying: Boolean,
    isQueued: Boolean,
    playProgress: Float, // 0.0 .. 1.0
    trackColor: Color,
    modifier: Modifier = Modifier,
    onTrigger: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(
                if (isPlaying) trackColor.copy(alpha = 0.35f)
                else if (hasContent) EarthColorTokens.GlassSurface
                else EarthColorTokens.GlassEspresso.copy(alpha = 0.5f)
            )
            .border(
                width = if (isPlaying || isQueued) 1.5.dp else 0.5.dp,
                color = when {
                    isQueued -> EarthColorTokens.AutumnMapleAmber
                    isPlaying -> trackColor
                    hasContent -> EarthColorTokens.GlassBorderSubtle
                    else -> EarthColorTokens.GlassBorderSubtle.copy(alpha = 0.3f)
                },
                shape = RoundedCornerShape(4.dp)
            )
            .clickable { onTrigger() }
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        // Progress Fill Bar across bottom
        if (isPlaying && playProgress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(playProgress.coerceIn(0f, 1f))
                    .height(2.dp)
                    .align(Alignment.BottomStart)
                    .background(EarthColorTokens.EarthAmber)
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (hasContent) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = if (isPlaying) EarthColorTokens.EarthAmber else EarthColorTokens.TextSecondary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = if (hasContent) name else "—",
                style = EarthTheme.typography.trackTitle,
                fontSize = 10.sp,
                color = if (isPlaying) EarthColorTokens.TextPrimary else EarthColorTokens.TextSecondary,
                maxLines = 1
            )
        }
    }
}

/**
 * 4x4 Matrix Velocity Drum Pad.
 */
@Composable
fun VelocityDrumPad(
    pad: DrumPadType,
    isTriggered: Boolean,
    modifier: Modifier = Modifier,
    onTrigger: (velocity: Float) -> Unit
) {
    var touchFlash by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .aspectRatio(1.0f)
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (isTriggered || touchFlash) EarthColorTokens.AutumnRust.copy(alpha = 0.5f)
                else EarthColorTokens.GlassSurface
            )
            .border(
                1.dp,
                if (isTriggered || touchFlash) EarthColorTokens.AutumnRust else EarthColorTokens.GlassBorderSubtle,
                RoundedCornerShape(6.dp)
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { offset ->
                        touchFlash = true
                        // Calculate velocity based on vertical touch position
                        val vel = (1f - (offset.y / size.height) * 0.4f).coerceIn(0.5f, 1.0f)
                        onTrigger(vel)
                        tryAwaitRelease()
                        touchFlash = false
                    }
                )
            }
            .padding(6.dp),
        contentAlignment = Alignment.BottomStart
    ) {
        Column {
            if (pad.chokeGroup > 0) {
                Text(
                    text = "CH ${pad.chokeGroup}",
                    style = EarthTheme.typography.microBadge,
                    fontSize = 7.sp,
                    color = EarthColorTokens.AutumnMapleAmber
                )
            }
            Text(
                text = pad.displayName,
                style = EarthTheme.typography.trackTitle,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = EarthColorTokens.TextPrimary
            )
        }
    }
}

/**
 * Real-time Waveform Canvas with Transient Slice Markers.
 */
@Composable
fun InteractiveWaveformCanvas(
    modifier: Modifier = Modifier,
    accentColor: Color = EarthColorTokens.NatureEmerald
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp)
            .earthGlass(shape = RoundedCornerShape(4.dp), baseColor = EarthColorTokens.BgObsidianDeep)
    ) {
        val midY = size.height / 2
        val step = 4.dp.toPx()
        val bars = (size.width / step).toInt()

        // Synthetic waveform pattern simulating high-res audio
        for (i in 0 until bars) {
            val x = i * step
            val envelope = kotlin.math.sin(i * 0.15) * kotlin.math.exp(-((i % 30) * 0.08))
            val amp = (kotlin.math.abs(envelope) * midY * 0.8f).toFloat().coerceAtLeast(2f)

            drawLine(
                color = accentColor.copy(alpha = 0.85f),
                start = Offset(x, midY - amp),
                end = Offset(x, midY + amp),
                strokeWidth = 2.dp.toPx()
            )
        }

        // Center line
        drawLine(
            color = EarthColorTokens.GlassBorderSubtle,
            start = Offset(0f, midY),
            end = Offset(size.width, midY),
            strokeWidth = 0.5.dp.toPx()
        )
    }
}

/**
 * 8-Band Interactive Parametric EQ Curve Canvas.
 */
@Composable
fun ParametricEqGraph(
    modifier: Modifier = Modifier,
    accentColor: Color = EarthColorTokens.EarthAmber
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .earthGlass(shape = RoundedCornerShape(6.dp), baseColor = EarthColorTokens.BgObsidianDeep)
    ) {
        // Draw frequency grid lines (100Hz, 1kHz, 10kHz)
        val gridLines = listOf(0.2f, 0.5f, 0.8f)
        gridLines.forEach { xPercent ->
            drawLine(
                color = EarthColorTokens.GlassBorderSubtle.copy(alpha = 0.4f),
                start = Offset(size.width * xPercent, 0f),
                end = Offset(size.width * xPercent, size.height),
                strokeWidth = 0.5.dp.toPx()
            )
        }

        // 0dB Center line
        val zeroDbY = size.height * 0.5f
        drawLine(
            color = EarthColorTokens.GlassBorderSubtle,
            start = Offset(0f, zeroDbY),
            end = Offset(size.width, zeroDbY),
            strokeWidth = 1.dp.toPx()
        )

        // Draw EQ Response Curve
        val path = Path()
        path.moveTo(0f, zeroDbY)

        val points = 64
        for (i in 0..points) {
            val x = (i.toFloat() / points) * size.width
            val normX = i.toFloat() / points
            val diff = (normX - 0.6) / 0.15
            val bell = kotlin.math.exp(-(diff * diff)).toFloat() * 30.dp.toPx()
            val y = zeroDbY - bell
            path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = accentColor,
            style = Stroke(width = 2.dp.toPx())
        )
    }
}

/**
 * Multi-Stage ADSR Envelope Interactive Graph.
 */
@Composable
fun AdsrEnvelopeGraph(
    attack: Float = 0.2f,
    decay: Float = 0.3f,
    sustain: Float = 0.6f,
    release: Float = 0.4f,
    modifier: Modifier = Modifier,
    accentColor: Color = EarthColorTokens.AutumnMapleAmber
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .earthGlass(shape = RoundedCornerShape(4.dp), baseColor = EarthColorTokens.BgObsidianDeep)
    ) {
        val totalW = size.width
        val h = size.height - 12.dp.toPx()
        val bottomY = size.height - 6.dp.toPx()
        val topY = 6.dp.toPx()

        val aW = totalW * 0.25f * attack.coerceIn(0.05f, 1f)
        val dW = totalW * 0.25f * decay.coerceIn(0.05f, 1f)
        val sW = totalW * 0.25f
        val rW = totalW * 0.25f * release.coerceIn(0.05f, 1f)

        val susY = bottomY - (h * sustain.coerceIn(0f, 1f))

        val path = Path().apply {
            moveTo(0f, bottomY)
            lineTo(aW, topY) // Attack
            lineTo(aW + dW, susY) // Decay
            lineTo(aW + dW + sW, susY) // Sustain
            lineTo(aW + dW + sW + rW, bottomY) // Release
        }

        // Fill area
        val fillPath = Path().apply {
            addPath(path)
            lineTo(size.width, bottomY)
            lineTo(0f, bottomY)
            close()
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(accentColor.copy(alpha = 0.25f), Color.Transparent)
            )
        )

        drawPath(
            path = path,
            color = accentColor,
            style = Stroke(width = 2.dp.toPx())
        )
    }
}
