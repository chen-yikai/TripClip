package edu.saihs.tripclip.ui.camera

import android.location.Location
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.saihs.tripclip.data.LocationRepository
import edu.saihs.tripclip.data.Photo
import edu.saihs.tripclip.data.PhotoRepository
import edu.saihs.tripclip.data.Place
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CameraUiState(
    val locating: Boolean = false,
    val location: Location? = null,
    val place: Place? = null,
    /** The most recent photo taken in this camera session. */
    val lastPhoto: Photo? = null,
    val capturing: Boolean = false,
    val useFrontCamera: Boolean = false,
    val flashOn: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class CameraViewModel(
    private val photos: PhotoRepository,
    private val locations: LocationRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private val lastPhotoId = MutableStateFlow<Long?>(null)

    init {
        // Follow the stored row so a retaken (deleted) photo disappears from the thumbnail.
        viewModelScope.launch {
            lastPhotoId
                .flatMapLatest { id -> if (id == null) flowOf(null) else photos.observePhoto(id) }
                .collect { photo -> _uiState.update { it.copy(lastPhoto = photo) } }
        }
    }

    private var locationJob: Job? = null

    /**
     * Follows the device location while the camera is visible. The place name is looked up again
     * once the user has moved [PLACE_REFRESH_METERS]. Safe to call repeatedly (e.g. after permission).
     */
    fun startLocationUpdates() {
        if (locationJob?.isActive == true || !locations.hasPermission()) return
        _uiState.update { it.copy(locating = it.location == null) }
        locationJob = viewModelScope.launch {
            var geocodedAt: Location? = null
            // conflate(): while a place lookup runs, only the newest fix is kept.
            locations.locationUpdates().conflate().collect { location ->
                _uiState.update { it.copy(location = location, locating = false) }
                val last = geocodedAt
                if (last == null || last.distanceTo(location) > PLACE_REFRESH_METERS) {
                    locations.placeAt(location.latitude, location.longitude)?.let { place ->
                        geocodedAt = location
                        _uiState.update { it.copy(place = place) }
                    }
                }
            }
        }
    }

    fun stopLocationUpdates() {
        locationJob?.cancel()
        locationJob = null
        _uiState.update { it.copy(locating = false) }
    }

    fun toggleFlash() = _uiState.update { it.copy(flashOn = !it.flashOn) }

    fun switchCamera() = _uiState.update { it.copy(useFrontCamera = !it.useFrontCamera) }

    fun onCaptureStarted() = _uiState.update { it.copy(capturing = true) }

    fun onCaptureFailed() = _uiState.update { it.copy(capturing = false) }

    /** Stores a photo the camera just saved to [uri]; it becomes the corner thumbnail. The camera stays open. */
    fun onCaptured(uri: Uri, takenAt: Long) {
        val state = _uiState.value
        viewModelScope.launch {
            val photo = Photo(
                uri = uri.toString(),
                takenAt = takenAt,
                latitude = state.location?.latitude,
                longitude = state.location?.longitude,
                placeName = state.place?.name,
            )
            val id = photos.addPhoto(photo)
            lastPhotoId.value = id
            _uiState.update { it.copy(capturing = false) }
        }
    }

    private companion object {
        const val PLACE_REFRESH_METERS = 30f
    }
}
