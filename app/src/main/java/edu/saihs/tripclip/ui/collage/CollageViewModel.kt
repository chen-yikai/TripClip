package edu.saihs.tripclip.ui.collage

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.saihs.tripclip.data.Photo
import edu.saihs.tripclip.data.PhotoRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

enum class CollageLayout { Scatter, Grid, Film }

data class CollageOptions(
    val layout: CollageLayout = CollageLayout.Scatter,
    val showPlace: Boolean = true,
    val showTime: Boolean = true,
    val showNote: Boolean = true,
    /** Changes on "shuffle" to re-roll which photos are featured, their order (spots), and scatter tilts. */
    val seed: Int = 0,
)

data class CollageUiState(
    val date: LocalDate,
    val photos: List<Photo> = emptyList(),
    val options: CollageOptions = CollageOptions(),
    val loaded: Boolean = false,
)

class CollageViewModel(
    repository: PhotoRepository,
    private val savedState: SavedStateHandle,
    private val date: LocalDate,
) : ViewModel() {

    private val layout = savedState.getStateFlow(KEY_LAYOUT, CollageLayout.Scatter)
    private val flags = savedState.getStateFlow(KEY_FLAGS, booleanArrayOf(true, true, true))
    private val seed = savedState.getStateFlow(KEY_SEED, 0)

    val uiState: StateFlow<CollageUiState> = combine(
        repository.observePhotosOn(date),
        layout,
        flags,
        seed,
    ) { photos, layout, flags, seed ->
        CollageUiState(
            date = date,
            photos = photos,
            options = CollageOptions(layout, flags[0], flags[1], flags[2], seed),
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CollageUiState(date))

    fun setLayout(value: CollageLayout) {
        savedState[KEY_LAYOUT] = value
    }

    fun setShowPlace(value: Boolean) = setFlag(0, value)
    fun setShowTime(value: Boolean) = setFlag(1, value)
    fun setShowNote(value: Boolean) = setFlag(2, value)

    fun shuffle() {
        savedState[KEY_SEED] = seed.value + 1
    }

    private fun setFlag(index: Int, value: Boolean) {
        savedState[KEY_FLAGS] = flags.value.copyOf().also { it[index] = value }
    }

    private companion object {
        const val KEY_LAYOUT = "layout"
        const val KEY_FLAGS = "flags"
        const val KEY_SEED = "seed"
    }
}
