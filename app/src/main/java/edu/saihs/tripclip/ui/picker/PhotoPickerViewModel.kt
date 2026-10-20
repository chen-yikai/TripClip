package edu.saihs.tripclip.ui.picker

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.saihs.tripclip.data.GalleryImage
import edu.saihs.tripclip.data.GalleryRepository
import edu.saihs.tripclip.data.PhotoImporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** How far back the in-app picker lists camera-roll photos; older ones go through "All albums". */
const val RECENT_DAYS = 30L

data class PhotoPickerUiState(
    val today: LocalDate = LocalDate.now(),
    val images: List<GalleryImage> = emptyList(),
    /** Selected uris in tap order; the 1-based index is the badge number. */
    val selected: List<Uri> = emptyList(),
    val loaded: Boolean = false,
    val adding: Boolean = false,
)

class PhotoPickerViewModel(
    private val gallery: GalleryRepository,
    private val importer: PhotoImporter,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PhotoPickerUiState())
    val uiState: StateFlow<PhotoPickerUiState> = _uiState.asStateFlow()

    fun load() {
        viewModelScope.launch {
            val today = LocalDate.now()
            val images = runCatching { gallery.recentImages(today, RECENT_DAYS) }
                .onFailure { Log.w("PhotoPicker", "Failed to query gallery", it) }
                .getOrDefault(emptyList())
            val available = images.map { it.uri }.toSet()
            _uiState.update { s ->
                s.copy(today = today, images = images, selected = s.selected.filter { it in available }, loaded = true)
            }
        }
    }

    fun toggle(uri: Uri) {
        _uiState.update { s ->
            s.copy(selected = if (uri in s.selected) s.selected - uri else s.selected + uri)
        }
    }

    /** Imports the selection in tap order, then reports the day to show and the photo to scroll to. */
    fun addSelected(onDone: (LocalDate, String?) -> Unit) {
        val state = _uiState.value
        if (state.adding || state.selected.isEmpty()) return
        val takenAt = state.images.associate { it.uri to it.takenAt }
        import(state.selected, takenAt, fallbackDay = state.today, onDone = onDone)
    }

    /** Imports photos chosen in the system photo picker (any date). */
    fun addFromSystemPicker(uris: List<Uri>, onDone: (LocalDate, String?) -> Unit) {
        if (uris.isEmpty() || _uiState.value.adding) return
        import(uris, emptyMap(), fallbackDay = LocalDate.now(), onDone = onDone)
    }

    private fun import(uris: List<Uri>, takenAt: Map<Uri, Long>, fallbackDay: LocalDate, onDone: (LocalDate, String?) -> Unit) {
        _uiState.update { it.copy(adding = true) }
        viewModelScope.launch {
            val day = try {
                // Show the day the first selected photo belongs to.
                importer.import(uris, takenAt).firstOrNull()
                    ?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
                    ?: fallbackDay
            } catch (e: Exception) {
                fallbackDay
            } finally {
                _uiState.update { it.copy(adding = false, selected = emptyList()) }
            }
            onDone(day, uris.first().toString())
        }
    }
}
