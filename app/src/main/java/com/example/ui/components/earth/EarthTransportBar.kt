package com.example.ui.components.earth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.state.TransportStateHolder
import com.example.ui.theme.earth.EarthColorTokens
import com.example.ui.theme.earth.EarthTheme
import com.example.ui.theme.earth.earthGlass

/**
 * Earth.Design Global Transport Bar with Pro-Audio Ergonomics.
 */
@Composable
fun EarthTransportBar(
    transportStateHolder: TransportStateHolder,
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by transportStateHolder.state.collectAsState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .earthGlass(shape = RoundedCornerShape(0.dp), baseColor = EarthColorTokens.BgObsidianDeep)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // --- Left: Transport Play/Stop/Record/Loop/Metronome ---
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Play Button
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (state.isPlaying) EarthColorTokens.EarthAmber else EarthColorTokens.GlassSurface)
                    .border(0.5.dp, if (state.isPlaying) EarthColorTokens.EarthAmber else EarthColorTokens.GlassBorderSubtle, RoundedCornerShape(4.dp))
                    .clickable { transportStateHolder.togglePlay() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = if (state.isPlaying) Color.Black else EarthColorTokens.EarthAmber,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Stop Button
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(EarthColorTokens.GlassSurface)
                    .border(0.5.dp, EarthColorTokens.GlassBorderSubtle, RoundedCornerShape(4.dp))
                    .clickable { transportStateHolder.stop() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Stop",
                    tint = EarthColorTokens.TextPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Record Button
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (state.isRecording) EarthColorTokens.AutumnCrimsonMaple else EarthColorTokens.GlassSurface)
                    .border(0.5.dp, if (state.isRecording) EarthColorTokens.AutumnCrimsonMaple else EarthColorTokens.GlassBorderSubtle, RoundedCornerShape(4.dp))
                    .clickable { transportStateHolder.toggleRecord() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FiberManualRecord,
                    contentDescription = "Record",
                    tint = if (state.isRecording) Color.White else EarthColorTokens.AutumnCrimsonMaple,
                    modifier = Modifier.size(14.dp)
                )
            }

            // Loop Toggle
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (state.isLooping) EarthColorTokens.EarthAmber.copy(alpha = 0.2f) else EarthColorTokens.GlassSurface)
                    .border(0.5.dp, if (state.isLooping) EarthColorTokens.EarthAmber else EarthColorTokens.GlassBorderSubtle, RoundedCornerShape(4.dp))
                    .clickable { transportStateHolder.toggleLoop() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Repeat,
                    contentDescription = "Loop",
                    tint = if (state.isLooping) EarthColorTokens.EarthAmber else EarthColorTokens.TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // --- Center: Timecode & BPM Pill Container ---
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .height(32.dp)
                .earthGlass(shape = RoundedCornerShape(16.dp), baseColor = EarthColorTokens.GlassSurface)
                .padding(horizontal = 10.dp)
        ) {
            // Timecode
            Text(
                text = state.timecodeFormatted,
                style = EarthTheme.typography.displayTime,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(14.dp)
                    .background(EarthColorTokens.GlassBorderSubtle)
            )

            // BPM Scrub
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable {
                    val nextBpm = if (state.bpm >= 140f) 110f else state.bpm + 5f
                    transportStateHolder.setBpm(nextBpm)
                }
            ) {
                Text(
                    text = String.format("%.1f", state.bpm),
                    style = EarthTheme.typography.bpmValue,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = "BPM",
                    style = EarthTheme.typography.paramLabel,
                    fontSize = 9.sp,
                    color = EarthColorTokens.TextSecondary
                )
            }

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(14.dp)
                    .background(EarthColorTokens.GlassBorderSubtle)
            )

            // Time Signature
            Text(
                text = "${state.timeSigNum}/${state.timeSigDen}",
                style = EarthTheme.typography.paramValue,
                fontSize = 11.sp,
                color = EarthColorTokens.TextSecondary
            )
        }

        // --- Right: Undo / Redo Actions ---
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(EarthColorTokens.GlassSurface)
                    .border(0.5.dp, EarthColorTokens.GlassBorderSubtle, RoundedCornerShape(4.dp))
                    .clickable { onUndo() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Undo,
                    contentDescription = "Undo",
                    tint = EarthColorTokens.TextSecondary,
                    modifier = Modifier.size(14.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(EarthColorTokens.GlassSurface)
                    .border(0.5.dp, EarthColorTokens.GlassBorderSubtle, RoundedCornerShape(4.dp))
                    .clickable { onRedo() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Redo,
                    contentDescription = "Redo",
                    tint = EarthColorTokens.TextSecondary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
