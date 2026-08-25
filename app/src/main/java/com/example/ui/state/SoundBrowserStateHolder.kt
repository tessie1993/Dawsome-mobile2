package com.example.ui.state

import com.example.data.media.FactoryPack
import com.example.data.media.FactorySample
import com.example.synth.domain.DeviceType
import com.example.synth.domain.ProjectAction
import com.example.synth.domain.ProjectStore
import com.example.synth.domain.TrackType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class BrowserCategory(val displayName: String) {
    INSTRUMENTS("Instruments"),
    AUDIO_FX("Audio FX"),
    MIDI_FX("MIDI FX"),
    DRUM_KITS("Drum Kits"),
    SAMPLES_LOOPS("Samples & Loops"),
    PRESETS("User Presets")
}

data class BrowserItem(
    val id: String,
    val name: String,
    val category: BrowserCategory,
    val tags: List<String>,
    val author: String = "Factory Library",
    /** Present on playable media rows (spec P1 §12 preview/assign). */
    val sample: FactorySample? = null
)

data class BrowserUiState(
    val selectedCategory: BrowserCategory = BrowserCategory.INSTRUMENTS,
    val searchQuery: String = "",
    val activeTags: Set<String> = emptySet(),
    val items: List<BrowserItem> = emptyList(),
    val previewingItemId: String? = null,
    /** Set when a LOAD could not find any target track — never silent. */
    val lastLoadFailedItemId: String? = null
)

class SoundBrowserStateHolder(
    private val store: ProjectStore,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate)
) {
    private val presetItems = listOf(
        BrowserItem("poly_lead", "Cyberpunk Modular Lead", BrowserCategory.INSTRUMENTS, listOf("Synth", "Wavetable", "Aggressive")),
        BrowserItem("sub_808", "Deep 808 Sub Boom", BrowserCategory.INSTRUMENTS, listOf("Bass", "Sub", "Analog")),
        BrowserItem("rhodes_ep", "Vintage Warm Rhodes", BrowserCategory.INSTRUMENTS, listOf("Keys", "Electric Piano", "Warm")),
        BrowserItem("string_pad", "Ambient Glass Ensemble", BrowserCategory.INSTRUMENTS, listOf("Pad", "Strings", "Space")),
        BrowserItem("fm_metallic", "Metallic FM Bells", BrowserCategory.INSTRUMENTS, listOf("FM", "Keys", "Chime")),
        BrowserItem("808_kit", "Obsidian Trap 808 Kit", BrowserCategory.DRUM_KITS, listOf("808", "Trap", "Electronic")),
        BrowserItem("crystal_reverb", "Crystal Algorithmic Reverb", BrowserCategory.AUDIO_FX, listOf("Reverb", "Space", "Ambient")),
        BrowserItem("ping_pong", "Stereo Ping-Pong Echo", BrowserCategory.AUDIO_FX, listOf("Delay", "Echo", "Stereo")),
        BrowserItem("glue_comp", "Bus Glue Compressor", BrowserCategory.AUDIO_FX, listOf("Dynamics", "Punch", "Master"))
    )

    /** Presets + the generated factory samples (appear once installation lands). */
    private var allItems = presetItems

    private val _state = MutableStateFlow(
        BrowserUiState(items = filterItems(BrowserCategory.INSTRUMENTS, "", emptySet()))
    )
    val state: StateFlow<BrowserUiState> = _state.asStateFlow()

    init {
        scope.launch {
            FactoryPack.samples.collect { samples ->
                allItems = presetItems + samples.map { s ->
                    BrowserItem(
                        id = s.id, name = s.name,
                        category = BrowserCategory.SAMPLES_LOOPS,
                        tags = s.tags, sample = s)
                }
                refresh()
            }
        }
    }

    fun selectCategory(cat: BrowserCategory) {
        _state.value = _state.value.copy(selectedCategory = cat)
        refresh()
    }

    fun search(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
        refresh()
    }

    fun toggleTag(tag: String) {
        val s = _state.value
        _state.value = s.copy(
            activeTags = if (tag in s.activeTags) s.activeTags - tag else s.activeTags + tag)
        refresh()
    }

    // ---- audition (spec P1 §12 "preview sounds before loading") -------------

    /** Toggle semantics: previewing the previewing row stops it. */
    fun togglePreview(item: BrowserItem) {
        val sample = item.sample ?: return
        if (_state.value.previewingItemId == item.id) {
            store.dispatch(ProjectAction.StopPreview)
            _state.value = _state.value.copy(previewingItemId = null)
        } else {
            store.dispatch(ProjectAction.PreviewSample(sample.fileId, sample.path))
            _state.value = _state.value.copy(previewingItemId = item.id)
        }
    }

    fun stopPreview() {
        if (_state.value.previewingItemId == null) return
        store.dispatch(ProjectAction.StopPreview)
        _state.value = _state.value.copy(previewingItemId = null)
    }

    // ---- assignment ---------------------------------------------------------

    /**
     * Load a sample row into the project. An existing SAMPLER on the
     * selected track wins (fallback: first sampler anywhere) and keeps its
     * tweaked params; with no sampler in the project, the Simpler-on-drop
     * gesture inserts one on the best target track in ONE undoable action
     * (review cycle-3: the default project ships samplerless, so LOAD must
     * create, never silently no-op). Returns false — and marks the row —
     * only when the project has no track at all. Drum-pad drag assignment
     * ships with the drum lab pass; the engine seam (per-pad slots) is live.
     */
    fun assignToSampler(item: BrowserItem): Boolean {
        val sample = item.sample ?: return false
        val project = store.state.value
        val candidates =
            project.tracks.filter { it.id == project.selectedTrackId } + project.tracks
        for (track in candidates) {
            val sampler = track.devices.firstOrNull { it.type == DeviceType.SAMPLER } ?: continue
            store.dispatch(ProjectAction.AssignSampleToDevice(
                trackId = track.id, deviceId = sampler.id, slot = 0,
                fileId = sample.fileId, path = sample.path, name = sample.name))
            // The file's recorded pitch rides as an ordinary param so playback
            // is in key immediately (SamplerShared.rootNote). One gesture,
            // two undo steps here - the gesture-transaction pass folds it.
            store.dispatch(ProjectAction.SetDeviceParam(
                track.id, sampler.id, "sample.root", sample.rootNote.toFloat()))
            _state.value = _state.value.copy(lastLoadFailedItemId = null)
            return true
        }
        // Instrument-capable lanes only; a return/master chain never hosts one.
        val target = candidates.firstOrNull {
            it.type == TrackType.MIDI || it.type == TrackType.AUDIO || it.type == TrackType.DRUM
        }
        if (target == null) {
            _state.value = _state.value.copy(lastLoadFailedItemId = item.id)
            return false
        }
        store.dispatch(ProjectAction.LoadSampleIntoNewSampler(
            trackId = target.id, fileId = sample.fileId, path = sample.path,
            name = sample.name, rootNote = sample.rootNote))
        _state.value = _state.value.copy(lastLoadFailedItemId = null)
        return true
    }

    private fun refresh() {
        val s = _state.value
        _state.value = s.copy(items = filterItems(s.selectedCategory, s.searchQuery, s.activeTags))
    }

    private fun filterItems(cat: BrowserCategory, query: String, tags: Set<String>): List<BrowserItem> {
        return allItems.filter { item ->
            item.category == cat &&
                    (query.isEmpty() || item.name.contains(query, ignoreCase = true)) &&
                    (tags.isEmpty() || item.tags.any { tags.contains(it) })
        }
    }
}
