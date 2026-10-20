package edu.saihs.tripclip.ui.viewer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.saihs.tripclip.data.Photo
import edu.saihs.tripclip.data.PhotoRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class PhotoViewerUiState(
    /** The day's photos, oldest first (same order as the journal). */
    val photos: List<Photo> = emptyList(),
    /** The photo currently shown; kept across edits and process death. */
    val currentId: Long,
    val loaded: Boolean = false,
)

class PhotoViewerViewModel(
    private val repository: PhotoRepository,
    private val savedState: SavedStateHandle,
    day: LocalDate,
    initialPhotoId: Long,
) : ViewModel() {

    private val currentId = savedState.getStateFlow(KEY_CURRENT, initialPhotoId)

    val uiState: StateFlow<PhotoViewerUiState> = combine(
        repository.observePhotosOn(day).map { photos -> photos.sortedBy { it.takenAt } },
        currentId,
    ) { photos, id ->
        PhotoViewerUiState(photos = photos, currentId = id, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PhotoViewerUiState(currentId = initialPhotoId))

    fun show(photoId: Long) {
        savedState[KEY_CURRENT] = photoId
    }

    /** Deletes [photo]; the viewer moves on to the next photo (or the previous one if it was last). */
    fun delete(photo: Photo) {
        val photos = uiState.value.photos
        val index = photos.indexOfFirst { it.id == photo.id }
        (photos.getOrNull(index + 1) ?: photos.getOrNull(index - 1))?.let { show(it.id) }
        viewModelScope.launch { repository.deletePhoto(photo.id) }
    }

    private companion object {
        const val KEY_CURRENT = "current_photo"
    }
}
