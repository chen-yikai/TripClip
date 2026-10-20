package edu.saihs.tripclip.ui.picker

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import edu.saihs.tripclip.R
import edu.saihs.tripclip.data.GalleryImage
import edu.saihs.tripclip.ui.components.PageTopBar
import edu.saihs.tripclip.ui.components.RoundIconButton
import edu.saihs.tripclip.ui.tripClipViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class MediaAccess { None, Partial, Full }

private val TimeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val DayFormat = DateTimeFormatter.ofPattern("MM/dd")

private val MediaPermissions: Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            Manifest.permission.ACCESS_MEDIA_LOCATION,
        )
    } else {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.ACCESS_MEDIA_LOCATION)
    }

@Composable
fun PhotoPickerScreen(
    onClose: () -> Unit,
    onOpenCamera: () -> Unit,
    /** Called after importing with the day to show and the uri of the photo to scroll to. */
    onAdded: (LocalDate, String?) -> Unit,
) {
    val context = LocalContext.current
    val viewModel = tripClipViewModel { app -> PhotoPickerViewModel(app.galleryRepository, app.photoImporter) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    fun currentAccess(): MediaAccess {
        fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
        return when {
            granted(Manifest.permission.READ_MEDIA_IMAGES) -> MediaAccess.Full
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> MediaAccess.Partial
            else -> MediaAccess.None
        }
    }

    var access by remember { mutableStateOf(currentAccess()) }
    var askedOnce by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        access = currentAccess()
        if (access != MediaAccess.None) viewModel.load()
    }
    val systemPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        viewModel.addFromSystemPicker(uris, onAdded)
    }

    LaunchedEffect(Unit) {
        if (access == MediaAccess.None && !askedOnce) {
            askedOnce = true
            permissionLauncher.launch(MediaPermissions)
        }
    }
    // Re-check on resume: the user may have changed access in system settings.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        access = currentAccess()
        if (access != MediaAccess.None) viewModel.load()
    }

    PhotoPickerContent(
        state = state,
        access = access,
        onClose = onClose,
        onToggle = viewModel::toggle,
        onAdd = { viewModel.addSelected(onAdded) },
        onRequestAccess = { permissionLauncher.launch(MediaPermissions) },
        onOpenAllAlbums = {
            systemPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        onOpenCamera = onOpenCamera,
    )
}

@Composable
private fun PhotoPickerContent(
    state: PhotoPickerUiState,
    access: MediaAccess,
    onClose: () -> Unit,
    onToggle: (Uri) -> Unit,
    onAdd: () -> Unit,
    onRequestAccess: () -> Unit,
    onOpenAllAlbums: () -> Unit,
    onOpenCamera: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            PageTopBar(
                title = stringResource(R.string.picker_title),
                subtitle = stringResource(R.string.picker_subtitle),
                leading = {
                    RoundIconButton(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.action_close),
                        onClick = onClose,
                    )
                },
                trailing = {
                    AddButton(count = state.selected.size, adding = state.adding, onClick = onAdd)
                },
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    pluralStringResource(R.plurals.picker_recent_days, RECENT_DAYS.toInt(), RECENT_DAYS.toInt()),
                    fontSize = 16.sp,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onOpenAllAlbums) {
                    Text(stringResource(R.string.picker_all_albums), fontSize = 16.sp, color = colors.onSurfaceVariant)
                    Icon(
                        painterResource(R.drawable.ic_chevron_right),
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                    )
                }
            }

            when {
                access == MediaAccess.None -> PermissionPrompt(onRequestAccess)
                state.loaded && state.images.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.picker_empty), color = colors.onSurfaceVariant)
                        if (access == MediaAccess.Partial) {
                            TextButton(onClick = onRequestAccess) {
                                Text(stringResource(R.string.picker_select_more), color = colors.secondary)
                            }
                        }
                        Spacer(Modifier.height(120.dp))
                    }
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(start = 2.dp, end = 2.dp, top = 2.dp, bottom = navBar + 120.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    val zone = ZoneId.systemDefault()
                    val byDay = state.images.groupBy { Instant.ofEpochMilli(it.takenAt).atZone(zone).toLocalDate() }
                    byDay.forEach { (day, images) ->
                        item(key = "day-$day", span = { GridItemSpan(maxLineSpan) }) {
                            DayHeader(day = day, today = state.today, count = images.size)
                        }
                        items(images, key = { it.uri.toString() }) { image ->
                            val order = state.selected.indexOf(image.uri)
                            GalleryCell(image, order = if (order >= 0) order + 1 else null, onToggle = { onToggle(image.uri) })
                        }
                    }
                    if (access == MediaAccess.Partial) {
                        item {
                            Box(
                                Modifier
                                    .aspectRatio(1f)
                                    .background(colors.surfaceContainer)
                                    .clickable(onClick = onRequestAccess),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    stringResource(R.string.picker_select_more),
                                    color = colors.secondary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(8.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        Surface(
            onClick = onOpenCamera,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 20.dp)
                .size(68.dp),
            shape = CircleShape,
            color = colors.inverseSurface,
            contentColor = colors.inverseOnSurface,
            shadowElevation = 8.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_camera), stringResource(R.string.picker_open_camera), Modifier.size(28.dp))
            }
        }
    }
}

@Composable
private fun DayHeader(day: LocalDate, today: LocalDate, count: Int) {
    val colors = MaterialTheme.colorScheme
    val label = when (day) {
        today -> stringResource(R.string.journal_today)
        today.minusDays(1) -> stringResource(R.string.journal_yesterday)
        else -> day.format(DayFormat)
    }
    Row(
        Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(label, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
        Spacer(Modifier.width(10.dp))
        val countText = pluralStringResource(R.plurals.photo_count_short, count, count)
        val isRelative = day == today || day == today.minusDays(1)
        Text(
            if (isRelative) stringResource(R.string.group_subtitle, day.format(DayFormat), countText) else countText,
            fontSize = 15.sp,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun AddButton(count: Int, adding: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Button(
        onClick = onClick,
        enabled = count > 0 && !adding,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.inverseSurface,
            contentColor = colors.inverseOnSurface,
            disabledContainerColor = colors.surfaceContainerHigh,
            disabledContentColor = colors.onSurfaceVariant,
        ),
        contentPadding = PaddingValues(horizontal = 18.dp),
        modifier = Modifier.height(48.dp),
    ) {
        if (adding) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.inverseOnSurface)
        } else {
            Text(stringResource(R.string.picker_add, count), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun GalleryCell(image: GalleryImage, order: Int?, onToggle: () -> Unit) {
    val time = remember(image.takenAt) {
        LocalTime.ofInstant(Instant.ofEpochMilli(image.takenAt), ZoneId.systemDefault()).format(TimeFormat)
    }
    Box(
        Modifier
            .aspectRatio(1f)
            .toggleable(value = order != null, role = Role.Checkbox, onValueChange = { onToggle() })
    ) {
        AsyncImage(
            model = image.uri,
            contentDescription = stringResource(R.string.picker_select),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer),
        )
        if (order != null) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f)))
        }
        Text(
            time,
            style = TextStyle(
                color = Color.White,
                fontSize = 14.sp,
                shadow = Shadow(Color.Black.copy(alpha = 0.6f), blurRadius = 6f),
            ),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp),
        )
        SelectionBadge(order, Modifier.align(Alignment.TopEnd).padding(8.dp))
    }
}

@Composable
private fun SelectionBadge(order: Int?, modifier: Modifier = Modifier) {
    val shape = CircleShape
    if (order == null) {
        Box(
            modifier
                .size(26.dp)
                .background(Color.Black.copy(alpha = 0.2f), shape)
                .border(BorderStroke(2.dp, Color.White), shape)
        )
    } else {
        Box(
            modifier
                .size(26.dp)
                .background(MaterialTheme.colorScheme.inverseSurface, shape)
                .border(BorderStroke(1.dp, Color.Black.copy(alpha = 0.15f)), shape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "$order",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.inverseOnSurface,
            )
        }
    }
}

@Composable
private fun PermissionPrompt(onRequestAccess: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = colors.surfaceContainerLowest,
            border = BorderStroke(1.dp, colors.outlineVariant),
        ) {
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.picker_permission_title), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.picker_permission_body),
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Button(
                    onClick = onRequestAccess,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                ) { Text(stringResource(R.string.picker_permission_grant)) }
            }
        }
    }
}
