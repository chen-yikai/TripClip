package edu.saihs.tripclip.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import edu.saihs.tripclip.TripClipApplication

/**
 * Creates a ViewModel scoped to the current nav entry, with access to the app's dependencies.
 * The lambda runs with [CreationExtras] as receiver, so `createSavedStateHandle()` is available.
 *
 * ```
 * val vm = tripClipViewModel { app -> EditNoteViewModel(app.photoRepository, photoId) }
 * ```
 */
@Composable
inline fun <reified VM : ViewModel> tripClipViewModel(
    crossinline create: CreationExtras.(TripClipApplication) -> VM,
): VM = viewModel(
    factory = viewModelFactory {
        initializer { create(this[APPLICATION_KEY] as TripClipApplication) }
    }
)
