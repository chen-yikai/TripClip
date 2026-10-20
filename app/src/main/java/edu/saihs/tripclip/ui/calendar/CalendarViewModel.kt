package edu.saihs.tripclip.ui.calendar

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.saihs.tripclip.data.Photo
import edu.saihs.tripclip.data.PhotoRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class DaySummary(val cover: Photo, val count: Int)

data class CalendarUiState(
    val month: YearMonth,
    val selected: LocalDate,
    val today: LocalDate = LocalDate.now(),
    /** Days of [month] and its neighbours (for swiping) that have photos, with the first photo as the thumbnail. */
    val days: Map<LocalDate, DaySummary> = emptyMap(),
    /** Photos of [selected], oldest first. */
    val selectedPhotos: List<Photo> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(
    private val repository: PhotoRepository,
    private val savedState: SavedStateHandle,
    initialDate: LocalDate,
) : ViewModel() {

    private val month = savedState.getStateFlow(KEY_MONTH, YearMonth.from(initialDate).toString())
    private val selected = savedState.getStateFlow(KEY_SELECTED, initialDate.toEpochDay())

    private val days = month.flatMapLatest { value ->
        val ym = YearMonth.parse(value)
        // Load the neighbouring months too, so pages swiped into view already show their photos.
        repository.observePhotosBetween(ym.minusMonths(1).atDay(1), ym.plusMonths(2).atDay(1)).map { photos ->
            val zone = ZoneId.systemDefault()
            photos.groupBy { Instant.ofEpochMilli(it.takenAt).atZone(zone).toLocalDate() }
                .mapValues { (_, list) -> DaySummary(list.first(), list.size) }
        }
    }

    private val selectedPhotos = selected.flatMapLatest { day -> repository.observePhotosOn(LocalDate.ofEpochDay(day)) }

    val uiState: StateFlow<CalendarUiState> = combine(month, selected, days, selectedPhotos) { m, s, d, photos ->
        CalendarUiState(YearMonth.parse(m), LocalDate.ofEpochDay(s), LocalDate.now(), d, photos)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        CalendarUiState(YearMonth.from(initialDate), initialDate),
    )

    fun setMonth(value: YearMonth) {
        savedState[KEY_MONTH] = value.toString()
    }

    fun select(date: LocalDate) {
        savedState[KEY_SELECTED] = date.toEpochDay()
    }

    private companion object {
        const val KEY_MONTH = "month"
        const val KEY_SELECTED = "selected"
    }
}
