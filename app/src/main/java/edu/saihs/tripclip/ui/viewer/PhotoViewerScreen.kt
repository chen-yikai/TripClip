package edu.saihs.tripclip.ui.viewer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import edu.saihs.tripclip.R
import edu.saihs.tripclip.data.Photo
import edu.saihs.tripclip.ui.components.PageTopBar
import edu.saihs.tripclip.ui.components.Polaroid
import edu.saihs.tripclip.ui.components.RoundIconButton
import edu.saihs.tripclip.ui.components.polaroidTilt
import edu.saihs.tripclip.ui.theme.PolaroidInk
import edu.saihs.tripclip.ui.theme.handwritingFontFamily
import edu.saihs.tripclip.ui.theme.notebookGrid
import edu.saihs.tripclip.ui.tripClipViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TitleFormat = DateTimeFormatter.ofPattern("MM/dd・HH:mm")
private val DeleteRed = Color(0xFFE5484D)
private val PolaroidStamp = DateTimeFormatter.ofPattern("MM.dd・HH:mm")

/** 查看照片: the day's photos, one per page; tap the photo to edit its note, or delete it from the top bar. */
@Composable
fun PhotoViewerScreen(
    day: LocalDate,
    photoId: Long,
    /** Known up front so the photo (and the shared transition) shows on the first frame. */
    photoUri: String?,
    onBack: () -> Unit,
    onEdit: (photoId: Long, uri: String) -> Unit,
) {
    val viewModel = tripClipViewModel { app ->
        PhotoViewerViewModel(app.photoRepository, createSavedStateHandle(), day, photoId)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Every photo of the day was deleted (e.g. from the editor): nothing left to show.
    LaunchedEffect(state.loaded, state.photos.isEmpty()) {
        if (state.loaded && state.photos.isEmpty()) onBack()
    }

    val current = state.photos.firstOrNull { it.id == state.currentId }
    val zone = ZoneId.systemDefault()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .notebookGrid()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        PageTopBar(
            title = current?.let { Instant.ofEpochMilli(it.takenAt).atZone(zone).format(TitleFormat) }.orEmpty(),
            subtitle = if (state.photos.size > 1 && current != null) {
                stringResource(R.string.viewer_counter, state.photos.indexOf(current) + 1, state.photos.size)
            } else null,
            leading = {
                RoundIconButton(
                    painter = painterResource(R.drawable.ic_back),
                    contentDescription = stringResource(R.string.action_back),
                    onClick = onBack,
                )
            },
            trailing = {
                RoundIconButton(
                    painter = painterResource(R.drawable.ic_delete),
                    contentDescription = stringResource(R.string.note_delete),
                    onClick = { if (current != null) confirmDelete = true },
                    contentColor = DeleteRed,
                )
            },
        )

        // Only the photo swipes; the place/time rows below stay put and follow the current photo.
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (!state.loaded || state.photos.isEmpty()) {
                // Before the day's photos load, show the tapped one so the shared transition has a target.
                if (photoUri != null) ViewerPhoto(photoId = photoId, uri = photoUri, photo = null, shared = true, onClick = null)
            } else {
                val initialPage = state.photos.indexOfFirst { it.id == state.currentId }.coerceAtLeast(0)
                val pagerState = rememberPagerState(initialPage = initialPage) { state.photos.size }
                val photos by rememberUpdatedState(state.photos)
                LaunchedEffect(pagerState) {
                    snapshotFlow { pagerState.currentPage }.collect { page ->
                        photos.getOrNull(page)?.let { viewModel.show(it.id) }
                    }
                }
                HorizontalPager(
                    state = pagerState,
                    key = { state.photos[it].id },
                    beyondViewportPageCount = 1,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    val photo = state.photos[page]
                    // Neighbouring pages are composed too; only the current photo morphs to/from the journal.
                    ViewerPhoto(
                        photoId = photo.id,
                        uri = photo.uri,
                        photo = photo,
                        shared = photo.id == state.currentId,
                        onClick = { onEdit(photo.id, photo.uri) },
                    )
                }
            }
        }

        PhotoInfo(current)
    }

    if (confirmDelete && current != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.note_delete_title)) },
            text = { Text(stringResource(R.string.note_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete(current)
                }) { Text(stringResource(R.string.action_delete), color = DeleteRed) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/** One page: the polaroid, centred; tapping it opens the note editor. */
@Composable
private fun ViewerPhoto(photoId: Long, uri: String, photo: Photo?, shared: Boolean, onClick: (() -> Unit)?) {
    val hand = handwritingFontFamily()
    val taken = photo?.let { Instant.ofEpochMilli(it.takenAt).atZone(ZoneId.systemDefault()) }
    Box(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
        Polaroid(
            model = uri,
            // Same tilt as in the journal, so the polaroid doesn't rotate during the shared transition.
            tiltDegrees = polaroidTilt(photoId),
            border = 14.dp,
            onClick = onClick,
            sharedPhotoId = if (shared) photoId else null,
            modifier = Modifier.fillMaxWidth(0.86f),
        ) {
            Text(
                photo?.note.orEmpty(),
                style = TextStyle(fontFamily = hand, fontSize = 22.sp, lineHeight = 28.sp, color = PolaroidInk),
                modifier = Modifier.heightIn(min = 56.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    photo?.placeName.orEmpty(),
                    style = TextStyle(fontFamily = hand, fontSize = 15.sp, color = PolaroidInk.copy(alpha = 0.6f)),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    taken?.format(PolaroidStamp).orEmpty(),
                    style = TextStyle(fontFamily = hand, fontSize = 15.sp, color = PolaroidInk.copy(alpha = 0.6f)),
                )
            }
        }
    }
}

/** Place and time of the current photo, fixed at the bottom of the screen. */
@Composable
private fun PhotoInfo(photo: Photo?) {
    val locale = LocalConfiguration.current.locales[0]
    val fullPattern = stringResource(R.string.note_datetime_pattern)
    val taken = photo?.let { Instant.ofEpochMilli(it.takenAt).atZone(ZoneId.systemDefault()) }
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Bottom),
    ) {
        photo?.placeName?.let { InfoRow(R.drawable.ic_location, it) }
        taken?.let { InfoRow(R.drawable.ic_clock, it.format(DateTimeFormatter.ofPattern(fullPattern, locale))) }
    }
}

@Composable
private fun InfoRow(icon: Int, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(painterResource(icon), null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
