package com.example.ui.screens.earth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.earth.InteractiveWaveformCanvas
import com.example.ui.state.BrowserCategory
import com.example.ui.state.SoundBrowserStateHolder
import com.example.ui.theme.earth.EarthColorTokens
import com.example.ui.theme.earth.EarthTheme
import com.example.ui.theme.earth.earthGlass

/**
 * Sound Browser & Preset Management Suite (Earth.Design).
 */
@Composable
fun SoundBrowserScreen(
    browserStateHolder: SoundBrowserStateHolder,
    onLoadItem: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by browserStateHolder.state.collectAsState()
    var searchInput by remember { mutableStateOf(state.searchQuery) }

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(EarthColorTokens.BgObsidianDeep)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // --- Left: Category Tree ---
        Column(
            modifier = Modifier
                .width(130.dp)
                .fillMaxHeight()
                .earthGlass(shape = RoundedCornerShape(6.dp), baseColor = EarthColorTokens.GlassSurface)
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "LIBRARY",
                style = EarthTheme.typography.sectionLabel,
                color = EarthColorTokens.TextSecondary,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            BrowserCategory.entries.forEach { cat ->
                val isSelected = cat == state.selectedCategory
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isSelected) EarthColorTokens.EarthAmber.copy(alpha = 0.25f) else EarthColorTokens.GlassEspresso)
                        .border(0.5.dp, if (isSelected) EarthColorTokens.EarthAmber else EarthColorTokens.GlassBorderSubtle, RoundedCornerShape(4.dp))
                        .clickable { browserStateHolder.selectCategory(cat) }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = cat.displayName,
                        style = EarthTheme.typography.trackTitle,
                        fontSize = 10.sp,
                        color = if (isSelected) EarthColorTokens.EarthAmber else EarthColorTokens.TextPrimary
                    )
                }
            }
        }

        // --- Center: Search & Filtered Results ---
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .earthGlass(shape = RoundedCornerShape(6.dp), baseColor = EarthColorTokens.GlassSurface)
                .padding(8.dp)
        ) {
            // Search Input
            OutlinedTextField(
                value = searchInput,
                onValueChange = {
                    searchInput = it
                    browserStateHolder.search(it)
                },
                placeholder = { Text("Search sounds, presets, tags...", fontSize = 11.sp, color = EarthColorTokens.TextDisabled) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = EarthColorTokens.TextSecondary) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EarthColorTokens.EarthAmber,
                    unfocusedBorderColor = EarthColorTokens.GlassBorderSubtle,
                    focusedTextColor = EarthColorTokens.TextPrimary,
                    unfocusedTextColor = EarthColorTokens.TextPrimary
                ),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Tag Filter Chips
            val tags = listOf("Warm", "Analog", "Aggressive", "Sub", "Lead", "Pad", "FM", "Space")
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(tags) { tag ->
                    val isTagActive = state.activeTags.contains(tag)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isTagActive) EarthColorTokens.NatureEmerald.copy(alpha = 0.3f) else EarthColorTokens.GlassEspresso)
                            .border(0.5.dp, if (isTagActive) EarthColorTokens.NatureEmerald else EarthColorTokens.GlassBorderSubtle, RoundedCornerShape(12.dp))
                            .clickable { browserStateHolder.toggleTag(tag) }
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = tag,
                            style = EarthTheme.typography.microBadge,
                            fontSize = 8.sp,
                            color = if (isTagActive) EarthColorTokens.NatureEmerald else EarthColorTokens.TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Results List
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(state.items) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .earthGlass(shape = RoundedCornerShape(4.dp), baseColor = EarthColorTokens.GlassEspresso)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(text = item.name, style = EarthTheme.typography.trackTitle, fontSize = 11.sp)
                            Text(text = item.author, style = EarthTheme.typography.paramLabel, fontSize = 8.sp, color = EarthColorTokens.TextSecondary)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(EarthColorTokens.GlassSurface)
                                    .border(0.5.dp, EarthColorTokens.GlassBorderSubtle, RoundedCornerShape(3.dp))
                                    .padding(4.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Audition", tint = EarthColorTokens.EarthAmber, modifier = Modifier.size(14.dp))
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(EarthColorTokens.EarthAmber)
                                    .clickable { onLoadItem(item.id) }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Text("LOAD", style = EarthTheme.typography.microBadge, color = Color.Black)
                            }
                        }
                    }
                }
            }
        }
    }
}
