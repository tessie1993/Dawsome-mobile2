package com.example.ui.screens.earth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Stop
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
import com.example.ui.components.earth.ClipLauncherTile
import com.example.ui.state.SessionStateHolder
import com.example.ui.theme.earth.EarthColorTokens
import com.example.ui.theme.earth.EarthTheme
import com.example.ui.theme.earth.earthGlass

/**
 * Session View & Clip Launcher Matrix (Earth.Design).
 */
@Composable
fun SessionViewScreen(
    sessionStateHolder: SessionStateHolder,
    modifier: Modifier = Modifier
) {
    val state by sessionStateHolder.state.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EarthColorTokens.BgObsidianDeep)
    ) {
        // --- Top Global Action Bar (Return All to Arrangement) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .earthGlass(shape = RoundedCornerShape(0.dp), baseColor = EarthColorTokens.GlassEspresso)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "SESSION CLIP LAUNCHER",
                style = EarthTheme.typography.sectionLabel,
                fontSize = 10.sp
            )

            // Return All to Arrangement
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(EarthColorTokens.GlassSurface)
                    .border(0.5.dp, EarthColorTokens.EarthAmber, RoundedCornerShape(4.dp))
                    .clickable { sessionStateHolder.returnAllToArrangement() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Restore,
                    contentDescription = null,
                    tint = EarthColorTokens.EarthAmber,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "BACK TO ARRANGER",
                    style = EarthTheme.typography.microBadge,
                    fontSize = 8.sp,
                    color = EarthColorTokens.EarthAmber
                )
            }
        }

        // --- Matrix Grid (Tracks horizontal scroll, Scenes vertical) ---
        Row(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(scrollState)
        ) {
            // Track Columns
            state.tracks.forEach { track ->
                val trackColor = try {
                    Color(android.graphics.Color.parseColor(track.colorHex))
                } catch (_: Exception) {
                    EarthColorTokens.EarthAmber
                }

                Column(
                    modifier = Modifier
                        .width(110.dp)
                        .fillMaxHeight()
                        .border(0.5.dp, EarthColorTokens.GlassBorderSubtle)
                        .padding(4.dp)
                ) {
                    // Track Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .earthGlass(shape = RoundedCornerShape(3.dp), baseColor = EarthColorTokens.GlassSurface)
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(trackColor, RoundedCornerShape(3.dp))
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = track.name,
                            style = EarthTheme.typography.trackTitle,
                            fontSize = 9.sp,
                            maxLines = 1
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // 8 Clip Slots for this track
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(8) { slotIndex ->
                            val clip = track.sessionClips.firstOrNull { it.slotIndex == slotIndex }
                            val hasContent = clip?.notes?.isNotEmpty() == true || clip?.drumSteps?.isNotEmpty() == true

                            ClipLauncherTile(
                                name = clip?.name ?: "Slot ${slotIndex + 1}",
                                hasContent = hasContent,
                                isPlaying = clip?.isPlaying == true,
                                isQueued = clip?.isQueued == true,
                                playProgress = clip?.playProgress ?: 0f,
                                trackColor = trackColor,
                                onTrigger = { sessionStateHolder.triggerClip(track.id, slotIndex) }
                            )
                        }
                    }

                    // Stop Track Button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(EarthColorTokens.GlassSurface)
                            .border(0.5.dp, EarthColorTokens.GlassBorderSubtle, RoundedCornerShape(3.dp))
                            .clickable { sessionStateHolder.returnTrackToArrangement(track.id) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop",
                            tint = EarthColorTokens.TextSecondary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            // --- Master Scene Launch Column (Right) ---
            Column(
                modifier = Modifier
                    .width(80.dp)
                    .fillMaxHeight()
                    .border(0.5.dp, EarthColorTokens.GlassBorderSubtle)
                    .background(EarthColorTokens.GlassEspresso)
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "SCENES",
                        style = EarthTheme.typography.sectionLabel,
                        fontSize = 9.sp
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(state.scenes) { index, scene ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(EarthColorTokens.GlassSurface)
                                .border(0.5.dp, EarthColorTokens.AutumnHarvestGold.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                .clickable { sessionStateHolder.triggerScene(index) }
                                .padding(horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = EarthColorTokens.AutumnHarvestGold,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = scene.name,
                                    style = EarthTheme.typography.trackTitle,
                                    fontSize = 8.sp,
                                    color = EarthColorTokens.AutumnHarvestGold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
