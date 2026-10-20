package edu.saihs.tripclip.ui.navigation

import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import edu.saihs.tripclip.ui.calendar.CalendarScreen
import edu.saihs.tripclip.ui.camera.CameraScreen
import edu.saihs.tripclip.ui.collage.DailyCollageScreen
import edu.saihs.tripclip.ui.journal.JournalScreen
import edu.saihs.tripclip.ui.note.EditNoteScreen
import edu.saihs.tripclip.ui.picker.PhotoPickerScreen
import edu.saihs.tripclip.ui.settings.SettingsScreen
import edu.saihs.tripclip.ui.theme.TripClipTheme
import edu.saihs.tripclip.ui.viewer.PhotoViewerScreen
import java.time.Instant
import java.time.ZoneId

@Composable
fun TripClipNavDisplay() {
    val backStack = rememberNavBackStack(Journal())
    val navigator = remember(backStack) { Navigator(backStack) }

    SharedTransitionLayout {
    CompositionLocalProvider(LocalSharedTransitionScope provides this) {
    NavDisplay(
        backStack = backStack,
        onBack = { navigator.back() },
        sharedTransitionScope = this,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        transitionSpec = { pageTransition(forward = true) },
        popTransitionSpec = { pageTransition(forward = false) },
        predictivePopTransitionSpec = { pageTransition(forward = false) },
        entryProvider = entryProvider {
            entry<Journal> { key ->
                JournalScreen(
                    initialDate = key.date,
                    focusPhotoUri = key.focusPhotoUri,
                    onOpenCalendar = navigator::openCalendar,
                    onOpenSettings = navigator::openSettings,
                    onAddPhotos = navigator::openPhotoPicker,
                    onOpenPhoto = { day, id, uri -> navigator.openPhotoViewer(day, id, uri) },
                    onOpenCollage = navigator::openDailyCollage,
                )
            }
            entry<PhotoPicker> {
                PhotoPickerScreen(
                    onClose = navigator::back,
                    onOpenCamera = navigator::openCamera,
                    onAdded = { day, focusUri -> navigator.showJournal(day, focusUri) },
                )
            }
            entry<Camera> {
                // The camera is always shown on a dark background.
                TripClipTheme(darkTheme = true) {
                    CameraScreen(
                        onClose = navigator::back,
                        // A confirmed shot flies into its spot in the journal.
                        onPhotoTaken = { photo ->
                            navigator.showJournal(
                                Instant.ofEpochMilli(photo.takenAt).atZone(ZoneId.systemDefault()).toLocalDate(),
                                photo.uri,
                            )
                        },
                    )
                }
            }
            entry<PhotoViewer> { key ->
                PhotoViewerScreen(
                    day = key.date,
                    photoId = key.photoId,
                    photoUri = key.photoUri,
                    onBack = navigator::back,
                    onEdit = { id, uri -> navigator.openEditNote(id, uri) },
                )
            }
            entry<EditNote> { key ->
                EditNoteScreen(
                    photoId = key.photoId,
                    photoUri = key.photoUri,
                    fromCamera = key.fromCamera,
                    onBack = navigator::back,
                    // Return to wherever the photo was opened from (the camera keeps shooting from there).
                    onSaved = { _, _ -> navigator.back() },
                )
            }
            entry<DailyCollage> { key ->
                DailyCollageScreen(
                    date = key.date,
                    onBack = navigator::back,
                )
            }
            entry<CalendarPicker> { key ->
                CalendarScreen(
                    initialDate = key.date,
                    onBack = navigator::back,
                    onViewDay = { navigator.showJournal(it) },
                    onOpenPhoto = { day, id, uri -> navigator.openPhotoViewer(day, id, uri) },
                )
            }
            entry<Settings> {
                SettingsScreen(onBack = navigator::back)
            }
        },
    )
    }
    }
}
