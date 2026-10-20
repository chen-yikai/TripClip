package edu.saihs.tripclip.ui.journal

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import edu.saihs.tripclip.R
import edu.saihs.tripclip.data.Photo
import edu.saihs.tripclip.ui.components.Polaroid
import edu.saihs.tripclip.ui.components.RoundIconButton
import edu.saihs.tripclip.ui.components.polaroidTilt
import edu.saihs.tripclip.ui.navigation.aboveSharedPhotos
import edu.saihs.tripclip.ui.theme.Accent
import edu.saihs.tripclip.ui.theme.PolaroidInk
import edu.saihs.tripclip.ui.theme.handwritingFontFamily
import edu.saihs.tripclip.ui.theme.notebookGrid
import edu.saihs.tripclip.ui.tripClipViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TimeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val ChipDateFormat = DateTimeFormatter.ofPattern("MM/dd")

/** LazyColumn items before the first photo group (the header is pinned outside the list). */
private const val ItemsBeforeGroups = 0

/** How far the pinned header's gradient keeps fading past its content. */
private val AppBarFadeTail = 40.dp

@Composable
fun JournalScreen(
    initialDate: LocalDate,
    /** Photo to scroll to once it's in the list (e.g. just added); null for none. */
    focusPhotoUri: String?,
    onOpenCalendar: (LocalDate) -> Unit,
    onOpenSettings: () -> Unit,
    onAddPhotos: () -> Unit,
    onOpenPhoto: (day: LocalDate, id: Long, uri: String) -> Unit,
    onOpenCollage: (LocalDate) -> Unit,
) {
    val viewModel = tripClipViewModel { app ->
        JournalViewModel(app.photoRepository, createSavedStateHandle(), initialDate)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    JournalContent(
        state = state,
        focusPhotoUri = focusPhotoUri,
        onSelectDate = viewModel::selectDate,
        onGroupModeChange = viewModel::setGroupMode,
        onOpenCalendar = { onOpenCalendar(state.selectedDate) },
        onOpenSettings = onOpenSettings,
        onAddPhotos = onAddPhotos,
        onOpenPhoto = { id, uri -> onOpenPhoto(state.selectedDate, id, uri) },
        onOpenCollage = { onOpenCollage(state.selectedDate) },
    )
}

@Composable
private fun JournalContent(
    state: JournalUiState,
    focusPhotoUri: String?,
    onSelectDate: (LocalDate) -> Unit,
    onGroupModeChange: (GroupMode) -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenSettings: () -> Unit,
    onAddPhotos: () -> Unit,
    onOpenPhoto: (Long, String) -> Unit,
    onOpenCollage: () -> Unit,
) {
    val hand = handwritingFontFamily()
    val handBold = handwritingFontFamily(FontWeight.Bold)
    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val density = LocalDensity.current
    // Height of the pinned header (status bar, app bar, dates, day header, fade tail), so the list starts below it.
    // Saved so it's right on the first frame when returning here (shared photos land in the right place).
    var appBarHeightDp by rememberSaveable { mutableFloatStateOf(0f) }
    val appBarHeight = appBarHeightDp.dp

    // Scroll to the focused photo once; cleared after its row has scrolled to it (survives returning here).
    var pendingFocus by rememberSaveable(focusPhotoUri) { mutableStateOf(focusPhotoUri) }
    val listState = rememberLazyListState()
    LaunchedEffect(pendingFocus, state.groups) {
        val target = pendingFocus ?: return@LaunchedEffect
        val groupIndex = state.groups.indexOfFirst { group -> group.photos.any { it.uri == target } }
        // Jump (not animate) so the photo is in place while the shared transition (e.g. from the
        // camera) is still running, and the polaroid can fly straight into its spot.
        if (groupIndex >= 0) listState.scrollToItem(ItemsBeforeGroups + groupIndex)
    }

    Box(Modifier.fillMaxSize().notebookGrid()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = (appBarHeight - AppBarFadeTail).coerceAtLeast(0.dp),
                bottom = navBar + 112.dp,
            ),
        ) {
            if (!state.loading && state.groups.isEmpty()) {
                item(key = "empty") { EmptyDay() }
            }
            items(state.groups, key = { it.key }) { group ->
                PhotoGroupRow(
                    group = group,
                    focusPhotoUri = pendingFocus,
                    onFocused = { pendingFocus = null },
                    onOpenPhoto = onOpenPhoto,
                )
            }
        }

        // Pinned header (app bar, date strip, day header): page colour that's solid only behind the
        // status bar and fades gradually to transparent, so the grid and photos show through below.
        val background = MaterialTheme.colorScheme.background
        Column(
            Modifier
                .aboveSharedPhotos()
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0f to background,
                        0.25f to background.copy(alpha = 0.9f),
                        0.6f to background.copy(alpha = 0.7f),
                        0.85f to background.copy(alpha = 0.35f),
                        1f to background.copy(alpha = 0f),
                    )
                )
                .onSizeChanged { appBarHeightDp = with(density) { it.height.toDp().value } }
                .statusBarsPadding()
                .padding(top = 8.dp),
        ) {
            JournalAppBar(
                titleStyle = TextStyle(fontFamily = handBold, fontSize = 34.sp, color = MaterialTheme.colorScheme.onBackground),
                onOpenCalendar = onOpenCalendar,
                onOpenSettings = onOpenSettings,
            )
            DateStrip(
                dates = state.dates,
                selected = state.selectedDate,
                today = state.today,
                textStyle = TextStyle(fontFamily = hand, fontSize = 20.sp),
                onSelect = onSelectDate,
            )
            DayHeader(
                date = state.selectedDate,
                photoCount = state.photoCount,
                groupMode = state.groupMode,
                onGroupModeChange = onGroupModeChange,
                onOpenCollage = onOpenCollage,
            )
            Spacer(Modifier.height(AppBarFadeTail))
        }

        Surface(
            onClick = onAddPhotos,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .aboveSharedPhotos()
                .navigationBarsPadding()
                .padding(bottom = 20.dp)
                .size(68.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            shadowElevation = 8.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_add), stringResource(R.string.journal_add_photos), Modifier.size(32.dp))
            }
        }
    }
}

@Composable
private fun JournalAppBar(
    titleStyle: TextStyle,
    onOpenCalendar: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        RoundIconButton(
            painter = painterResource(R.drawable.ic_calendar),
            contentDescription = stringResource(R.string.journal_open_calendar),
            onClick = onOpenCalendar,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        Text(stringResource(R.string.app_name), style = titleStyle)
        RoundIconButton(
            painter = painterResource(R.drawable.ic_settings),
            contentDescription = stringResource(R.string.journal_open_settings),
            onClick = onOpenSettings,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

@Composable
private fun DateStrip(
    dates: List<LocalDate>,
    selected: LocalDate,
    today: LocalDate,
    textStyle: TextStyle,
    onSelect: (LocalDate) -> Unit,
) {
    val listState = rememberLazyListState()
    val selectedIndex = dates.indexOf(selected)
    LaunchedEffect(selectedIndex, dates.size) {
        if (selectedIndex >= 0) listState.animateScrollToItem((selectedIndex - 2).coerceAtLeast(0))
    }
    val todayLabel = stringResource(R.string.journal_today)
    val yesterdayLabel = stringResource(R.string.journal_yesterday)

    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(dates, key = { it.toEpochDay() }) { date ->
            val label = when (date) {
                today -> todayLabel
                today.minusDays(1) -> yesterdayLabel
                else -> date.format(ChipDateFormat)
            }
            DateChip(label = label, selected = date == selected, textStyle = textStyle, onClick = { onSelect(date) })
        }
    }
}

@Composable
private fun DateChip(label: String, selected: Boolean, textStyle: TextStyle, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(if (selected) colors.inverseSurface else colors.surfaceContainerLowest.copy(alpha = 0f))
    val content by animateColorAsState(if (selected) colors.inverseOnSurface else colors.onSurfaceVariant)
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = container,
        contentColor = content,
        border = if (selected) null else BorderStroke(1.dp, colors.outlineVariant),
        modifier = Modifier.height(48.dp).widthIn(min = 84.dp),
    ) {
        Box(Modifier.padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
            Text(label, style = textStyle)
        }
    }
}

@Composable
private fun DayHeader(
    date: LocalDate,
    photoCount: Int,
    groupMode: GroupMode,
    onGroupModeChange: (GroupMode) -> Unit,
    onOpenCollage: () -> Unit,
) {
    val hand = handwritingFontFamily()
    val locale = LocalConfiguration.current.locales[0]
    val pattern = stringResource(R.string.journal_date_pattern)
    val dateText = remember(date, pattern, locale) { date.format(DateTimeFormatter.ofPattern(pattern, locale)) }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                dateText,
                style = TextStyle(fontFamily = hand, fontSize = 32.sp, color = MaterialTheme.colorScheme.onBackground),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                pluralStringResource(R.plurals.photo_count, photoCount, photoCount),
                style = TextStyle(fontFamily = hand, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant),
            )
        }
        GroupModeToggle(groupMode, onGroupModeChange)
        Spacer(Modifier.width(10.dp))
        RoundIconButton(
            painter = painterResource(R.drawable.ic_collage),
            contentDescription = stringResource(R.string.journal_make_collage),
            onClick = onOpenCollage,
        )
    }
}

@Composable
private fun GroupModeToggle(mode: GroupMode, onChange: (GroupMode) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = CircleShape,
        color = colors.surfaceContainerLowest,
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        val itemWidth = 52.dp
        val indicatorX by animateDpAsState(
            targetValue = if (mode == GroupMode.Place) 0.dp else itemWidth,
            animationSpec = spring(dampingRatio = 0.8f, stiffness = 500f),
            label = "groupModeIndicator",
        )
        Box(Modifier.padding(4.dp)) {
            // Sliding background pill
            Box(
                Modifier
                    .offset { IntOffset(indicatorX.roundToPx(), 0) }
                    .size(width = itemWidth, height = 44.dp)
                    .clip(CircleShape)
                    // Ink at very low opacity: a faint black tint in light mode (and white in dark mode).
                    .background(colors.onSurface.copy(alpha = 0.08f))
            )
            Row(Modifier.selectableGroup()) {
                listOf(
                    Triple(GroupMode.Place, R.drawable.ic_location, R.string.journal_group_by_place),
                    Triple(GroupMode.Time, R.drawable.ic_clock, R.string.journal_group_by_time),
                ).forEach { (option, icon, label) ->
                    val selected = option == mode
                    val content by animateColorAsState(if (selected) colors.onSurface else colors.onSurfaceVariant.copy(alpha = 0.7f))
                    Surface(
                        selected = selected,
                        onClick = { onChange(option) },
                        shape = CircleShape,
                        color = Color.Transparent,
                        contentColor = content,
                        modifier = Modifier.size(width = itemWidth, height = 44.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(painterResource(icon), stringResource(label), Modifier.size(22.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoGroupRow(
    group: PhotoGroup,
    focusPhotoUri: String?,
    onFocused: () -> Unit,
    onOpenPhoto: (Long, String) -> Unit,
) {
    val hand = handwritingFontFamily()
    val handBold = handwritingFontFamily(FontWeight.Bold)
    val colors = MaterialTheme.colorScheme
    val unknownPlace = stringResource(R.string.journal_unknown_place)
    val separator = stringResource(R.string.list_separator)
    val count = pluralStringResource(R.plurals.photo_count_short, group.photos.size, group.photos.size)

    val (title, titleStyle, subtitle) = when (group) {
        is PhotoGroup.ByPlace -> {
            val range = if (group.start == group.end) group.start.format(TimeFormat)
            else "${group.start.format(TimeFormat)} – ${group.end.format(TimeFormat)}"
            Triple(
                group.placeName ?: unknownPlace,
                TextStyle(fontFamily = hand, fontSize = 24.sp),
                stringResource(R.string.group_subtitle, range, count),
            )
        }
        is PhotoGroup.ByHour -> Triple(
            group.hour.format(TimeFormat),
            TextStyle(fontFamily = handBold, fontSize = 26.sp),
            if (group.placeNames.isEmpty()) count
            else stringResource(R.string.group_subtitle, group.placeNames.joinToString(separator), count),
        )
    }

    Column(Modifier.padding(top = 20.dp)) {
        Row(
            Modifier.padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(Accent))
            Spacer(Modifier.width(14.dp))
            Text(title, style = titleStyle.copy(color = colors.onBackground), maxLines = 1)
            Spacer(Modifier.width(12.dp))
            Text(
                subtitle,
                style = TextStyle(fontFamily = hand, fontSize = 17.sp, color = colors.onSurfaceVariant),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val rowState = rememberLazyListState()
        val focusIndex = focusPhotoUri?.let { uri -> group.photos.indexOfFirst { it.uri == uri } } ?: -1
        LaunchedEffect(focusIndex) {
            if (focusIndex >= 0) {
                rowState.scrollToItem(focusIndex)
                onFocused()
            }
        }
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(group.photos, key = { it.id }) { photo ->
                JournalPolaroid(photo, onClick = { onOpenPhoto(photo.id, photo.uri) })
            }
        }
    }
}

@Composable
private fun JournalPolaroid(photo: Photo, onClick: () -> Unit) {
    val hand = handwritingFontFamily()
    val time = remember(photo.takenAt) {
        LocalTime.ofInstant(Instant.ofEpochMilli(photo.takenAt), ZoneId.systemDefault()).format(TimeFormat)
    }
    Polaroid(
        model = photo.uri,
        tiltDegrees = polaroidTilt(photo.id),
        contentDescription = photo.note ?: time,
        onClick = onClick,
        sharedPhotoId = photo.id,
        modifier = Modifier.width(170.dp),
    ) {
        Column(Modifier.heightIn(min = 68.dp)) {
            Text(time, style = TextStyle(fontFamily = hand, fontSize = 16.sp, color = PolaroidInk.copy(alpha = 0.6f)))
            photo.note?.let {
                Text(
                    it,
                    style = TextStyle(fontFamily = hand, fontSize = 18.sp, lineHeight = 24.sp, color = PolaroidInk),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun EmptyDay() {
    val hand = handwritingFontFamily()
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            stringResource(R.string.journal_empty_title),
            style = TextStyle(fontFamily = hand, fontSize = 22.sp, color = MaterialTheme.colorScheme.onBackground),
        )
        Text(
            stringResource(R.string.journal_empty_hint),
            style = TextStyle(fontFamily = hand, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant),
        )
    }
}
