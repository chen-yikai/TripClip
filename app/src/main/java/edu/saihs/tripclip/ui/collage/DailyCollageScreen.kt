package edu.saihs.tripclip.ui.collage

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import edu.saihs.tripclip.R
import edu.saihs.tripclip.ui.components.PageTopBar
import edu.saihs.tripclip.ui.components.RoundIconButton
import edu.saihs.tripclip.ui.tripClipViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DayFormat = DateTimeFormatter.ofPattern("MM/dd")

@Composable
fun DailyCollageScreen(date: LocalDate, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val viewModel = tripClipViewModel { app ->
        CollageViewModel(app.photoRepository, createSavedStateHandle(), date)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val posterLayer = rememberGraphicsLayer()
    var exporting by remember { mutableStateOf(false) }
    val savedText = stringResource(R.string.collage_saved)
    val failedText = stringResource(R.string.collage_save_failed)

    fun export(block: suspend (android.graphics.Bitmap) -> Unit) {
        if (exporting || state.photos.isEmpty()) return
        exporting = true
        scope.launch {
            try {
                block(posterLayer.toImageBitmap().asAndroidBitmap())
            } catch (e: Exception) {
                Toast.makeText(context, failedText, Toast.LENGTH_SHORT).show()
            } finally {
                exporting = false
            }
        }
    }

    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        PageTopBar(
            title = stringResource(R.string.collage_title),
            subtitle = stringResource(
                R.string.group_subtitle,
                date.format(DayFormat),
                pluralStringResource(R.plurals.photo_count_short, state.photos.size, state.photos.size),
            ),
            leading = {
                RoundIconButton(
                    painter = painterResource(R.drawable.ic_back),
                    contentDescription = stringResource(R.string.action_back),
                    onClick = onBack,
                )
            },
            trailing = {
                RoundIconButton(
                    painter = painterResource(R.drawable.ic_shuffle),
                    contentDescription = stringResource(R.string.collage_shuffle),
                    onClick = viewModel::shuffle,
                )
            },
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth(0.68f)
                    .aspectRatio(PosterWidthPx / PosterHeightPx.toFloat())
                    .clip(RoundedCornerShape(14.dp))
                    .border(BorderStroke(1.dp, colors.outlineVariant), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (state.loaded && state.photos.isEmpty()) {
                    Text(stringResource(R.string.collage_empty), color = colors.onSurfaceVariant)
                } else {
                    PosterPreview(state, posterLayer)
                }
            }
            Text(
                stringResource(R.string.collage_output_hint),
                fontSize = 12.sp,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )

            Text(
                stringResource(R.string.collage_layout),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onBackground,
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 10.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    CollageLayout.Scatter to R.string.collage_layout_scatter,
                    CollageLayout.Grid to R.string.collage_layout_grid,
                    CollageLayout.Film to R.string.collage_layout_film,
                ).forEach { (layout, label) ->
                    LayoutChip(stringResource(label), state.options.layout == layout) { viewModel.setLayout(layout) }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OptionCheck(stringResource(R.string.collage_show_place), state.options.showPlace, viewModel::setShowPlace)
                OptionCheck(stringResource(R.string.collage_show_time), state.options.showTime, viewModel::setShowTime)
                OptionCheck(stringResource(R.string.collage_show_note), state.options.showNote, viewModel::setShowNote)
            }
            Spacer(Modifier.height(16.dp))
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val enabled = state.photos.isNotEmpty() && !exporting
            OutlinedButton(
                onClick = {
                    export { bitmap ->
                        val uri = CollageExporter.saveToGallery(context, bitmap, date)
                        Toast.makeText(context, if (uri != null) savedText else failedText, Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = enabled,
                shape = CircleShape,
                border = BorderStroke(1.dp, colors.outline),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.onBackground),
                modifier = Modifier.weight(1f).height(54.dp),
            ) {
                Icon(painterResource(R.drawable.ic_download), null, Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.collage_save), fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = { export { bitmap -> CollageExporter.shareToStory(context, bitmap) } },
                enabled = enabled,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
                modifier = Modifier.weight(1f).height(54.dp),
            ) {
                Icon(painterResource(R.drawable.ic_share), null, Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.collage_share), fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Lays the poster out at its real output size (1080 × 1920 px), records it into [layer] for export,
 * and draws it scaled down to fit the preview box.
 */
@Composable
private fun PosterPreview(state: CollageUiState, layer: GraphicsLayer) {
    val density = LocalDensity.current
    val posterWidth = with(density) { PosterWidthPx.toDp() }
    val posterHeight = with(density) { PosterHeightPx.toDp() }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val scale = maxWidth / posterWidth
        CollagePoster(
            date = state.date,
            photos = state.photos,
            options = state.options,
            modifier = Modifier
                .requiredSize(posterWidth, posterHeight)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                },
        )
    }
}

@Composable
private fun LayoutChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) colors.inverseSurface else colors.surfaceContainerLowest,
        contentColor = if (selected) colors.inverseOnSurface else colors.onSurfaceVariant,
        border = if (selected) null else BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Text(label, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
    }
}

/** A toggle chip styled like [LayoutChip], with a leading check badge (empty ring off, filled ✓ on). */
@Composable
private fun OptionCheck(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(if (checked) colors.inverseSurface else colors.surfaceContainerLowest)
    val content by animateColorAsState(if (checked) colors.inverseOnSurface else colors.onSurfaceVariant)
    Surface(
        checked = checked,
        onCheckedChange = onChange,
        shape = CircleShape,
        color = container,
        contentColor = content,
        border = if (checked) null else BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Row(
            Modifier.padding(start = 10.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CheckBadge(checked = checked, color = content, checkColor = container)
            Spacer(Modifier.width(8.dp))
            Text(label, fontSize = 14.sp)
        }
    }
}

@Composable
private fun CheckBadge(checked: Boolean, color: Color, checkColor: Color) {
    Box(
        Modifier
            .size(20.dp)
            .clip(CircleShape)
            .then(
                if (checked) Modifier.background(color)
                else Modifier.border(BorderStroke(1.5.dp, color.copy(alpha = 0.6f)), CircleShape)
            ),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(visible = checked, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
            Icon(painterResource(R.drawable.ic_check), null, Modifier.size(13.dp), tint = checkColor)
        }
    }
}
