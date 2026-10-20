package edu.saihs.tripclip.ui.camera

import android.content.ContentResolver
import android.location.Location
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.saihs.tripclip.data.LocationRepository
import edu.saihs.tripclip.data.Photo
import edu.saihs.tripclip.data.PhotoRepository
import edu.saihs.tripclip.data.Place
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A shot that's been taken and saved to the gallery, waiting for retake or confirm. */
data class PendingShot(
    val uri: Uri,
    val takenAt: Long,
    val latitude: Double?,
    val longitude: Double?,
    val placeName: String?,
)

data class CameraUiState(
    val locating: Boolean = false,
    val location: Location? = null,
    val place: Place? = null,
    val capturing: Boolean = false,
    val useFrontCamera: Boolean = false,
    val flashOn: Boolean = false,
    /** Shown frozen in the viewfinder until retaken or confirmed. */
    val pending: PendingShot? = null,
    /** Set once the pending shot is confirmed and stored; the screen then flies it into the journal. */
    val stored: Photo? = null,
)

class CameraViewModel(
    private val photos: PhotoRepository,
    private val locations: LocationRepository,
    private val contentResolver: ContentResolver,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

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

    /** The camera saved a shot to [uri]: hold it for review, with where it was taken. */
    fun onCaptured(uri: Uri, takenAt: Long) = _uiState.update {
        it.copy(
            capturing = false,
            pending = PendingShot(uri, takenAt, it.location?.latitude, it.location?.longitude, it.place?.name),
        )
    }

    /** Throws the pending shot away (also deletes the image the camera saved) and returns to live. */
    fun retake() {
        val shot = _uiState.value.pending ?: return
        _uiState.update { it.copy(pending = null) }
        viewModelScope.launch { deleteImage(shot.uri) }
    }

    /** Adds the pending shot to the journal; [CameraUiState.stored] is set when done. */
    fun confirm() {
        val shot = _uiState.value.pending ?: return
        if (_uiState.value.stored != null) return
        viewModelScope.launch {
            val photo = Photo(
                uri = shot.uri.toString(),
                takenAt = shot.takenAt,
                latitude = shot.latitude,
                longitude = shot.longitude,
                placeName = shot.placeName,
            )
            val id = photos.addPhoto(photo)
            _uiState.update { it.copy(stored = photo.copy(id = id)) }
        }
    }

    /** Leaving with an unconfirmed shot: don't keep it in the gallery. */
    fun discardPending() {
        val shot = _uiState.value.pending ?: return
        if (_uiState.value.stored != null) return
        _uiState.update { it.copy(pending = null) }
        // Not tied to viewModelScope: it's cancelled as the screen goes away.
        val uri = shot.uri
        Thread { runCatching { contentResolver.delete(uri, null, null) } }.start()
    }

    private suspend fun deleteImage(uri: Uri) = withContext(Dispatchers.IO) {
        // The app created this MediaStore entry, so it may delete it without asking.
        runCatching { contentResolver.delete(uri, null, null) }
    }

    private companion object {
        const val PLACE_REFRESH_METERS = 30f
    }
}
