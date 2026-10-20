package edu.saihs.tripclip.ui.note

import android.content.ContentResolver
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.saihs.tripclip.data.LocationRepository
import edu.saihs.tripclip.data.NOTE_MAX_LENGTH
import edu.saihs.tripclip.data.Photo
import edu.saihs.tripclip.data.PhotoRepository
import edu.saihs.tripclip.data.Place
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class EditNoteUiState(
    val photo: Photo? = null,
    val note: String = "",
    /** Full place (spot + district) resolved from the photo's coordinates, when available. */
    val place: Place? = null,
    val loaded: Boolean = false,
    val busy: Boolean = false,
)

class EditNoteViewModel(
    private val photos: PhotoRepository,
    private val locations: LocationRepository,
    private val contentResolver: ContentResolver,
    private val photoId: Long,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditNoteUiState())
    val uiState: StateFlow<EditNoteUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val photo = photos.getPhoto(photoId)
            _uiState.update {
                it.copy(
                    photo = photo,
                    note = photo?.note.orEmpty(),
                    place = photo?.placeName?.let { name -> Place(name, null) },
                    loaded = true,
                )
            }
            val lat = photo?.latitude
            val lng = photo?.longitude
            if (lat != null && lng != null) {
                locations.placeAt(lat, lng)?.let { resolved ->
                    _uiState.update { s -> s.copy(place = Place(photo.placeName ?: resolved.name, resolved.area)) }
                }
            }
        }
    }

    fun onNoteChange(value: String) {
        _uiState.update { it.copy(note = value.take(NOTE_MAX_LENGTH)) }
    }

    /** Saves the note, then reports the photo's capture day and uri. */
    fun save(onDone: (LocalDate, String) -> Unit) {
        val state = _uiState.value
        if (state.busy || state.photo == null) return
        _uiState.update { it.copy(busy = true) }
        viewModelScope.launch {
            photos.updateNote(photoId, state.note)
            onDone(Instant.ofEpochMilli(state.photo.takenAt).atZone(ZoneId.systemDefault()).toLocalDate(), state.photo.uri)
        }
    }

    /** Discards a photo the camera just took: removes it from the journey and deletes the image file. */
    fun retake(onDone: () -> Unit) {
        val photo = _uiState.value.photo
        if (_uiState.value.busy || photo == null) return
        _uiState.update { it.copy(busy = true) }
        viewModelScope.launch {
            photos.deletePhoto(photoId)
            withContext(Dispatchers.IO) {
                // The app created this MediaStore entry, so it may delete it without asking.
                runCatching { contentResolver.delete(photo.uri.toUri(), null, null) }
            }
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(busy = true) }
        viewModelScope.launch {
            photos.deletePhoto(photoId)
            onDone()
        }
    }
}
