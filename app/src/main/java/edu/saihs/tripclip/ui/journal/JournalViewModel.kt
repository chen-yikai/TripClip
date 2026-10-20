package edu.saihs.tripclip.ui.journal

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.saihs.tripclip.data.PhotoRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth

data class JournalUiState(
    val selectedDate: LocalDate,
    val today: LocalDate,
    /**
     * Days shown in the date strip, oldest first: days with photos, plus today and the selected day,
     * limited to the selected day's month.
     */
    val dates: List<LocalDate>,
    val groupMode: GroupMode,
    val photoCount: Int,
    val groups: List<PhotoGroup>,
    val loading: Boolean,
)

@OptIn(ExperimentalCoroutinesApi::class)
class JournalViewModel(
    private val repository: PhotoRepository,
    private val savedState: SavedStateHandle,
    initialDate: LocalDate,
) : ViewModel() {

    private val selectedEpochDay = savedState.getStateFlow(KEY_DAY, initialDate.toEpochDay())
    private val groupMode = savedState.getStateFlow(KEY_MODE, GroupMode.Place)

    private val photosOfDay = selectedEpochDay.flatMapLatest { day ->
        repository.observePhotosOn(LocalDate.ofEpochDay(day)).map { day to it }
    }

    val uiState: StateFlow<JournalUiState> = combine(
        photosOfDay,
        groupMode,
        repository.observePhotoCountByDate(),
    ) { (day, photos), mode, counts ->
        val selected = LocalDate.ofEpochDay(day)
        val today = LocalDate.now()
        JournalUiState(
            selectedDate = selected,
            today = today,
            dates = (counts.keys + today + selected)
                .filter { YearMonth.from(it) == YearMonth.from(selected) }
                .sorted(),
            groupMode = mode,
            photoCount = photos.size,
            groups = groupPhotos(photos, mode),
            loading = false,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        JournalUiState(
            selectedDate = initialDate,
            today = LocalDate.now(),
            dates = listOf(initialDate),
            groupMode = groupMode.value,
            photoCount = 0,
            groups = emptyList(),
            loading = true,
        ),
    )

    fun selectDate(date: LocalDate) {
        savedState[KEY_DAY] = date.toEpochDay()
    }

    fun setGroupMode(mode: GroupMode) {
        savedState[KEY_MODE] = mode
    }

    private companion object {
        const val KEY_DAY = "selected_epoch_day"
        const val KEY_MODE = "group_mode"
    }
}
