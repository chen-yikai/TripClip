package edu.saihs.tripclip.ui.note

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import edu.saihs.tripclip.R
import edu.saihs.tripclip.data.NOTE_MAX_LENGTH
import edu.saihs.tripclip.ui.components.PageTopBar
import edu.saihs.tripclip.ui.components.Polaroid
import edu.saihs.tripclip.ui.components.RoundIconButton
import edu.saihs.tripclip.ui.components.polaroidTilt
import edu.saihs.tripclip.ui.theme.PolaroidInk
import edu.saihs.tripclip.ui.theme.handwritingFontFamily
import edu.saihs.tripclip.ui.tripClipViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val PolaroidStamp = DateTimeFormatter.ofPattern("MM.dd・HH:mm")
private val DeleteRed = Color(0xFFE5484D)

@Composable
fun EditNoteScreen(
    photoId: Long,
    /** The photo's uri if known up front, so the preview (and shared transition) shows on the first frame. */
    photoUri: String?,
    fromCamera: Boolean,
    onBack: () -> Unit,
    /** Called after 收進旅程 with the photo's day and uri. */
    onSaved: (LocalDate, String) -> Unit,
) {
    val viewModel = tripClipViewModel { app ->
        EditNoteViewModel(app.photoRepository, app.locationRepository, app.contentResolver, photoId)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    EditNoteContent(
        state = state,
        photoId = photoId,
        initialPhotoUri = photoUri,
        onBack = onBack,
        onNoteChange = viewModel::onNoteChange,
        onSave = { viewModel.save(onSaved) },
        onRetake = if (fromCamera) ({ viewModel.retake(onBack) }) else null,
    )
}

@Composable
private fun EditNoteContent(
    state: EditNoteUiState,
    photoId: Long,
    initialPhotoUri: String?,
    onBack: () -> Unit,
    onNoteChange: (String) -> Unit,
    onSave: () -> Unit,
    /** Shown only right after a capture: discards the shot and returns to the camera. */
    onRetake: (() -> Unit)?,
) {
    val colors = MaterialTheme.colorScheme
    val photo = state.photo
    val locale = LocalConfiguration.current.locales[0]
    val zone = ZoneId.systemDefault()
    val taken = photo?.let { Instant.ofEpochMilli(it.takenAt).atZone(zone) }
    val fullPattern = stringResource(R.string.note_datetime_pattern)
    val hand = handwritingFontFamily()

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        PageTopBar(
            title = stringResource(R.string.note_title),
            leading = {
                RoundIconButton(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = stringResource(R.string.action_close),
                    onClick = onBack,
                )
            },
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            val previewUri = photo?.uri ?: initialPhotoUri
            if (previewUri != null) {
                // Big polaroid preview, showing the note as it's typed.
                Box(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp), contentAlignment = Alignment.TopCenter) {
                    Polaroid(
                        model = previewUri,
                        // Same tilt as in the journal and viewer, so it doesn't rotate during the transition.
                        tiltDegrees = polaroidTilt(photoId),
                        border = 14.dp,
                        sharedPhotoId = photoId,
                        modifier = Modifier.fillMaxWidth(0.86f),
                    ) {
                        Text(
                            state.note,
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
            if (photo != null) {
                Spacer(Modifier.height(16.dp))
                state.place?.label(separator = "・")?.let { InfoRow(R.drawable.ic_location, it) }
                taken?.let {
                    InfoRow(R.drawable.ic_clock, it.format(DateTimeFormatter.ofPattern(fullPattern, locale)))
                }

                Row(
                    Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.note_label),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        stringResource(R.string.note_counter, state.note.length, NOTE_MAX_LENGTH),
                        fontSize = 13.sp,
                        color = colors.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                    value = state.note,
                    onValueChange = onNoteChange,
                    placeholder = { Text(stringResource(R.string.note_placeholder)) },
                    minLines = 3,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.secondary,
                        unfocusedBorderColor = colors.outlineVariant,
                        cursorColor = colors.secondary,
                        focusedContainerColor = colors.surfaceContainerLowest,
                        unfocusedContainerColor = colors.surfaceContainerLowest,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (onRetake != null) {
                OutlinedButton(
                    onClick = onRetake,
                    enabled = photo != null && !state.busy,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, colors.outline),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.onBackground),
                    modifier = Modifier.weight(1f).height(56.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_camera), null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.note_retake), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
            Button(
                onClick = onSave,
                enabled = photo != null && !state.busy,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
                modifier = Modifier.weight(if (onRetake != null) 1.4f else 1f).height(56.dp),
            ) {
                Icon(painterResource(R.drawable.ic_check), null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.note_save), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun InfoRow(icon: Int, text: String) {
    Row(
        Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(painterResource(icon), null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
