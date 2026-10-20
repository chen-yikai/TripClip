package edu.saihs.tripclip.ui.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import java.time.LocalDate

/** Typed navigation actions over the app's single back stack. Journal is always the root. */
class Navigator(private val backStack: NavBackStack<NavKey>) {

    fun back() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    fun openPhotoPicker() = push(PhotoPicker)

    /** The camera replaces the picker, so closing it returns to the journal. */
    fun openCamera() {
        if (backStack.lastOrNull() == PhotoPicker) backStack.removeAt(backStack.lastIndex)
        push(Camera)
    }

    fun openPhotoViewer(day: LocalDate, photoId: Long, photoUri: String?) =
        push(PhotoViewer(day.toEpochDay(), photoId, photoUri))

    fun openEditNote(photoId: Long, photoUri: String? = null) = push(EditNote(photoId, photoUri = photoUri))

    /** Opens the note editor for a photo the camera just took (enables retake); going back returns to the camera. */
    fun openEditNoteAfterCapture(photoId: Long, photoUri: String?) =
        push(EditNote(photoId, fromCamera = true, photoUri = photoUri))

    fun openDailyCollage(date: LocalDate) = push(DailyCollage(date.toEpochDay()))

    fun openCalendar(current: LocalDate) = push(CalendarPicker(current.toEpochDay()))

    fun openSettings() = push(Settings)

    /** Clears everything above the root and shows the journal on [date], scrolled to [focusPhotoUri]. */
    fun showJournal(date: LocalDate? = null, focusPhotoUri: String? = null) {
        backStack.clear()
        backStack.add(Journal(date?.toEpochDay(), focusPhotoUri))
    }

    private fun push(key: NavKey) {
        if (backStack.lastOrNull() != key) backStack.add(key)
    }
}
