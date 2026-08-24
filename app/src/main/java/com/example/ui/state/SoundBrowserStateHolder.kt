package com.example.ui.state

import com.example.synth.domain.ProjectStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
    val author: String = "Factory Library"
)

data class BrowserUiState(
    val selectedCategory: BrowserCategory = BrowserCategory.INSTRUMENTS,
    val searchQuery: String = "",
    val activeTags: Set<String> = emptySet(),
    val items: List<BrowserItem> = emptyList(),
    val previewingItemId: String? = null
)

class SoundBrowserStateHolder(private val store: ProjectStore) {
    private val allFactoryItems = listOf(
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

    private val _state = MutableStateFlow(
        BrowserUiState(items = allFactoryItems.filter { it.category == BrowserCategory.INSTRUMENTS })
    )
    val state: StateFlow<BrowserUiState> = _state.asStateFlow()

    fun selectCategory(cat: BrowserCategory) {
        _state.value = _state.value.copy(
            selectedCategory = cat,
            items = filterItems(cat, _state.value.searchQuery, _state.value.activeTags)
        )
    }

    fun search(query: String) {
        _state.value = _state.value.copy(
            searchQuery = query,
            items = filterItems(_state.value.selectedCategory, query, _state.value.activeTags)
        )
    }

    fun toggleTag(tag: String) {
        val updated = if (_state.value.activeTags.contains(tag)) _state.value.activeTags - tag
        else _state.value.activeTags + tag
        _state.value = _state.value.copy(
            activeTags = updated,
            items = filterItems(_state.value.selectedCategory, _state.value.searchQuery, updated)
        )
    }

    private fun filterItems(cat: BrowserCategory, query: String, tags: Set<String>): List<BrowserItem> {
        return allFactoryItems.filter { item ->
            item.category == cat &&
                    (query.isEmpty() || item.name.contains(query, ignoreCase = true)) &&
                    (tags.isEmpty() || item.tags.any { tags.contains(it) })
        }
    }
}
