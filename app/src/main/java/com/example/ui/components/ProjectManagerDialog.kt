package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.local.ProjectEntity
import com.example.synth.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SaveProjectDialog(
    viewModel: SynthViewModel,
    onDismiss: () -> Unit
) {
    val currentName by viewModel.currentProjectName.collectAsState()
    val currentGenre by viewModel.currentGenre.collectAsState()
    val currentId by viewModel.currentProjectId.collectAsState()

    var projectName by remember { mutableStateOf(currentName) }
    var genre by remember { mutableStateOf(currentGenre) }

    val genres = listOf("Electronic", "Synthwave", "Cyberpunk Acid", "Deep House", "Techno", "Ambient", "Trap & Hip Hop", "Custom")

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = AbletonSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, AbletonBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("save_project_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "Save Project",
                            tint = AbletonOrange,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (currentId != null) "SAVE PROJECT (ROOM DB)" else "SAVE NEW PROJECT",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = StudioTextPrimary
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = StudioTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Project Name Input
                OutlinedTextField(
                    value = projectName,
                    onValueChange = { projectName = it },
                    label = { Text("Project / Live Set Name", color = StudioTextSecondary, fontSize = 12.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = AbletonOrange,
                        unfocusedBorderColor = AbletonBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("project_name_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Genre Chips
                Text("Genre Category", fontSize = 11.sp, color = StudioTextSecondary)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    genres.take(4).forEach { g ->
                        val isSelected = genre == g
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(28.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isSelected) AbletonOrange else AbletonPanel)
                                .clickable { genre = g },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = g,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.Black else StudioTextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (currentId != null) {
                        Button(
                            onClick = {
                                viewModel.saveProjectAsNew(projectName.trim().ifEmpty { "Untitled" }, genre)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AbletonPanel),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.weight(1f).testTag("save_as_new_btn")
                        ) {
                            Text("SAVE AS NEW", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AbletonYellow)
                        }
                    }

                    Button(
                        onClick = {
                            viewModel.saveCurrentProject(projectName.trim().ifEmpty { "Untitled" }, genre)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AbletonOrange),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f).testTag("confirm_save_btn")
                    ) {
                        Text(
                            text = if (currentId != null) "OVERWRITE" else "SAVE PROJECT",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                }
            }
        }
    }
}

enum class BrowserTab(val title: String) {
    SAMPLES("Samples"),
    RACKS("Racks & FX"),
    SETS("Live Sets")
}

@Composable
fun AbletonBrowserDrawer(
    viewModel: SynthViewModel,
    modifier: Modifier = Modifier
) {
    val projects by viewModel.allSavedProjects.collectAsState()
    val currentProjectId by viewModel.currentProjectId.collectAsState()
    val browserSamples by viewModel.browserSamples.collectAsState()
    val context = LocalContext.current

    var activeBrowserTab by remember { mutableStateOf(BrowserTab.SAMPLES) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<BrowserCategory?>(null) }

    var isJsonDialogVisible by remember { mutableStateOf(false) }
    var jsonDialogMode by remember { mutableStateOf("export") } // "export" or "import"
    var jsonText by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(320.dp)
            .background(AbletonSurface)
            .border(1.dp, AbletonBorder)
            .padding(10.dp)
            .testTag("ableton_browser_drawer")
    ) {
        // --- 1. DRAWER HEADER ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.FolderSpecial,
                    contentDescription = "Browser",
                    tint = AbletonYellow,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "ABLETON BROWSER",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = AbletonYellow
                )
            }

            IconButton(
                onClick = { viewModel.closeBrowserDrawer() },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = StudioTextSecondary)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- 2. BROWSER CATEGORY TABS ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AbletonPanel, RoundedCornerShape(6.dp))
                .padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            BrowserTab.values().forEach { tab ->
                val isSelected = activeBrowserTab == tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(28.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isSelected) AbletonOrange else Color.Transparent)
                        .clickable { activeBrowserTab = tab },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tab.title.uppercase(),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color.Black else StudioTextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- 3. TAB CONTENT ---
        when (activeBrowserTab) {
            BrowserTab.SAMPLES -> {
                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search samples & loops...", fontSize = 10.sp, color = StudioTextSecondary) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = StudioTextSecondary, modifier = Modifier.size(14.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(16.dp)) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = StudioTextSecondary, modifier = Modifier.size(12.dp))
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = AbletonOrange,
                        unfocusedBorderColor = AbletonBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Category Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (selectedCategory == null) AbletonBlue else AbletonPanel)
                            .clickable { selectedCategory = null }
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text("ALL", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = if (selectedCategory == null) Color.Black else StudioTextSecondary)
                    }

                    BrowserCategory.values().forEach { cat ->
                        val isCatSelected = selectedCategory == cat
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isCatSelected) AbletonBlue else AbletonPanel)
                                .clickable { selectedCategory = cat }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(cat.badge, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = if (isCatSelected) Color.Black else StudioTextSecondary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Filtered Samples List
                val filteredSamples = browserSamples.filter { sample ->
                    val matchesCat = selectedCategory == null || sample.category == selectedCategory
                    val matchesQuery = searchQuery.isEmpty() ||
                            sample.name.contains(searchQuery, ignoreCase = true) ||
                            sample.subCategory.contains(searchQuery, ignoreCase = true) ||
                            sample.description.contains(searchQuery, ignoreCase = true)
                    matchesCat && matchesQuery
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredSamples) { sample ->
                        BrowserSampleRow(
                            sample = sample,
                            onAudition = { viewModel.auditionSample(sample) },
                            onLoad = { viewModel.loadBrowserSampleToActiveTrack(sample) }
                        )
                    }
                }
            }

            BrowserTab.RACKS -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text("MACRO RACK PRESETS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AbletonOrange)
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    val rackPresets = listOf(
                        "Club Banger Master" to "Tight low-end compression with punchy limiter and transient saturation.",
                        "Acid Tweaker 303" to "Resonant peak envelope sweeping with tape overdrive and delay echoes.",
                        "Ambient Lush Space" to "Wide stereo chorus ensemble into massive Schroeder algorithmic reverb.",
                        "Lo-Fi Tape Machine" to "Warm vinyl saturation, gentle bandpass EQ filtering, and subtle wow/flutter."
                    )

                    items(rackPresets) { (name, desc) ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = AbletonPanel,
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, AbletonBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    Button(
                                        onClick = { viewModel.loadMacroRackPreset(name) },
                                        colors = ButtonDefaults.buttonColors(containerColor = AbletonOrange),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(24.dp)
                                    ) {
                                        Text("LOAD", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(desc, fontSize = 9.sp, color = StudioTextSecondary)
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("SYNTHESIS PATCH PRESETS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AbletonGreen)
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    items(SynthPatch.PRESETS) { patch ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = AbletonPanel,
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, AbletonBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(patch.name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    Text(patch.description, fontSize = 8.sp, color = StudioTextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Button(
                                    onClick = { viewModel.loadPatch(patch) },
                                    colors = ButtonDefaults.buttonColors(containerColor = AbletonGreen),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(24.dp)
                                ) {
                                    Text("APPLY", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                }
                            }
                        }
                    }
                }
            }

            BrowserTab.SETS -> {
                // Quick Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Button(
                        onClick = { viewModel.createNewBlankProject() },
                        colors = ButtonDefaults.buttonColors(containerColor = AbletonPanel),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.weight(1f).height(30.dp).testTag("new_set_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "New", modifier = Modifier.size(12.dp), tint = AbletonGreen)
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("NEW", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = StudioTextPrimary)
                    }

                    Button(
                        onClick = {
                            jsonText = viewModel.exportCurrentProjectJson()
                            jsonDialogMode = "export"
                            isJsonDialogVisible = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AbletonPanel),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.weight(1f).height(30.dp).testTag("export_json_btn")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Export", modifier = Modifier.size(12.dp), tint = AbletonBlue)
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("EXPORT", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = StudioTextPrimary)
                    }

                    Button(
                        onClick = {
                            jsonText = ""
                            jsonDialogMode = "import"
                            isJsonDialogVisible = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AbletonPanel),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.weight(1f).height(30.dp).testTag("import_json_btn")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = "Import", modifier = Modifier.size(12.dp), tint = AbletonOrange)
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("IMPORT", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = StudioTextPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Saved Live Sets Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "SAVED LIVE SETS (${projects.size})",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = StudioTextSecondary,
                        letterSpacing = 0.5.sp
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Projects List
                if (projects.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No saved projects in Room DB.\nTap SAVE to store your session!",
                            fontSize = 11.sp,
                            color = StudioTextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(projects) { project ->
                            val isCurrent = project.id == currentProjectId
                            SavedProjectItemCard(
                                project = project,
                                isCurrent = isCurrent,
                                onLoad = { viewModel.loadProjectFromDb(project) },
                                onDelete = { viewModel.deleteProjectFromDb(project.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    // JSON Export / Import Dialog
    if (isJsonDialogVisible) {
        Dialog(onDismissRequest = { isJsonDialogVisible = false }) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = AbletonSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, AbletonBorder),
                modifier = Modifier.fillMaxWidth().padding(8.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (jsonDialogMode == "export") "EXPORT PROJECT JSON" else "IMPORT PROJECT JSON",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = AbletonOrange
                        )
                        IconButton(onClick = { isJsonDialogVisible = false }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = StudioTextSecondary)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = jsonText,
                        onValueChange = { jsonText = it },
                        readOnly = jsonDialogMode == "export",
                        maxLines = 10,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = AbletonOrange,
                            unfocusedBorderColor = AbletonBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (jsonDialogMode == "export") {
                            Button(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("DAW Project JSON", jsonText)
                                    clipboard.setPrimaryClip(clip)
                                    viewModel.showToast("Copied JSON to Clipboard!")
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AbletonBlue),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("COPY TO CLIPBOARD", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = {
                                    val success = viewModel.importProjectFromJson(jsonText)
                                    if (success) {
                                        isJsonDialogVisible = false
                                        viewModel.showToast("Project successfully imported!")
                                    } else {
                                        viewModel.showToast("Invalid JSON syntax or format")
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AbletonGreen),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("PARSE & LOAD JSON", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BrowserSampleRow(
    sample: BrowserSampleItem,
    onAudition: () -> Unit,
    onLoad: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = AbletonPanel,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, AbletonBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Audition Play Button
            IconButton(
                onClick = onAudition,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Audition",
                    tint = AbletonGreen,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Sample Meta
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(AbletonBlue.copy(alpha = 0.2f), RoundedCornerShape(2.dp))
                            .padding(horizontal = 3.dp, vertical = 1.dp)
                    ) {
                        Text(sample.category.badge, fontSize = 7.sp, fontWeight = FontWeight.Bold, color = AbletonBlue)
                    }
                    Text(
                        text = sample.name,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "${sample.subCategory} • ${sample.description}",
                    fontSize = 8.sp,
                    color = StudioTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Load Action
            Button(
                onClick = onLoad,
                colors = ButtonDefaults.buttonColors(containerColor = AbletonSurface),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                shape = RoundedCornerShape(3.dp),
                modifier = Modifier.height(22.dp)
            ) {
                Text("LOAD", fontSize = 7.sp, fontWeight = FontWeight.Bold, color = AbletonOrange)
            }
        }
    }
}

@Composable
fun SavedProjectItemCard(
    project: ProjectEntity,
    isCurrent: Boolean,
    onLoad: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
    val dateStr = remember(project.lastModified) { dateFormat.format(Date(project.lastModified)) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (isCurrent) AbletonOrange.copy(alpha = 0.15f) else AbletonPanel)
            .border(
                1.dp,
                if (isCurrent) AbletonOrange else AbletonBorder,
                RoundedCornerShape(6.dp)
            )
            .padding(8.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = project.name,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isCurrent) AbletonOrange else StudioTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${project.bpm.toInt()} BPM",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = AbletonYellow
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = project.genre,
                            fontSize = 9.sp,
                            color = StudioTextSecondary
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = dateStr,
                    fontSize = 8.sp,
                    color = StudioTextSecondary.copy(alpha = 0.7f)
                )

                Button(
                    onClick = onLoad,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCurrent) AbletonOrange else AbletonSurface
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.height(24.dp)
                ) {
                    Text(
                        text = if (isCurrent) "ACTIVE" else "LOAD",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isCurrent) Color.Black else StudioTextPrimary
                    )
                }
            }
        }
    }
}
