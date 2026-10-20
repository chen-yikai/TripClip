package edu.saihs.tripclip.ui.calendar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import edu.saihs.tripclip.R
import edu.saihs.tripclip.ui.components.PageTopBar
import edu.saihs.tripclip.ui.components.Polaroid
import edu.saihs.tripclip.ui.components.RoundIconButton
import edu.saihs.tripclip.ui.components.polaroidTilt
import edu.saihs.tripclip.ui.theme.PolaroidInk
import edu.saihs.tripclip.ui.tripClipViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

/** Weeks start on Sunday, as in the wireframe. */
private val WeekOrder =
    listOf(DayOfWeek.SUNDAY) + DayOfWeek.entries.filter { it != DayOfWeek.SUNDAY }

/** Pager pages are months from January [FirstYear]. */
private const val FirstYear = 1970
private const val MonthPageCount = (2100 - FirstYear) * 12

private fun YearMonth.toPage(): Int =
    ((year - FirstYear) * 12 + monthValue - 1).coerceIn(0, MonthPageCount - 1)

private fun pageToMonth(page: Int): YearMonth = YearMonth.of(FirstYear, 1).plusMonths(page.toLong())

@Composable
fun CalendarScreen(
    initialDate: LocalDate,
    onBack: () -> Unit,
    onViewDay: (LocalDate) -> Unit,
    onOpenPhoto: (day: LocalDate, photoId: Long, uri: String) -> Unit,
) {
    val viewModel = tripClipViewModel { app ->
        CalendarViewModel(app.photoRepository, createSavedStateHandle(), initialDate)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val locale = LocalConfiguration.current.locales[0]
    val monthPattern = stringResource(R.string.calendar_month_pattern)

    val pagerState = rememberPagerState(initialPage = state.month.toPage()) { MonthPageCount }
    val shownMonth = pageToMonth(pagerState.currentPage)
    val scope = rememberCoroutineScope()
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { viewModel.setMonth(pageToMonth(it)) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        PageTopBar(
            title = stringResource(R.string.calendar_title),
            leading = {
                RoundIconButton(
                    painter = painterResource(R.drawable.ic_back),
                    contentDescription = stringResource(R.string.action_back),
                    onClick = onBack,
                )
            },
            trailing = {
                RoundIconButton(
                    painter = painterResource(R.drawable.ic_today),
                    contentDescription = stringResource(R.string.calendar_go_today),
                    onClick = {
                        viewModel.select(state.today)
                        scope.launch {
                            pagerState.animateScrollToPage(
                                YearMonth.from(state.today).toPage()
                            )
                        }
                    },
                )
            },
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } }) {
                    Icon(
                        painterResource(R.drawable.ic_back),
                        stringResource(R.string.calendar_prev_month)
                    )
                }
                Text(
                    shownMonth.format(DateTimeFormatter.ofPattern(monthPattern, locale)),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } }) {
                    Icon(
                        painterResource(R.drawable.ic_chevron_right),
                        stringResource(R.string.calendar_next_month)
                    )
                }
            }

            Row(Modifier.fillMaxWidth()) {
                WeekOrder.forEach { day ->
                    Text(
                        day.getDisplayName(TextStyle.NARROW, locale),
                        fontSize = 13.sp,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            HorizontalPager(state = pagerState, verticalAlignment = Alignment.Top) { page ->
                MonthGrid(
                    month = pageToMonth(page),
                    days = state.days,
                    selected = state.selected,
                    today = state.today,
                    onSelect = viewModel::select,
                )
            }
        }

        // Up to three of the selected day's photos as petals, just above the day card.
        AnimatedVisibility(
            visible = state.selectedPhotos.isNotEmpty(),
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = 40.dp),
        ) {
            Box(Modifier
                .fillMaxWidth()
                .padding(top = 5.dp), contentAlignment = Alignment.Center) {
                AnimatedContent(
                    targetState = state.selected to state.selectedPhotos,
                    contentKey = { (day, photos) -> day to photos.map { it.id } },
                    transitionSpec = {
                        (fadeIn(tween(220)) + scaleIn(
                            tween(220),
                            initialScale = 0.85f
                        )) togetherWith
                                (fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.9f))
                    },
                    label = "petals",
                ) { (day, photos) ->
                    PhotoPetals(
                        day = day,
                        photos = photos,
                        onOpenPhoto = { onOpenPhoto(day, it.id, it.uri) })
                }
            }
        }

        SelectedDayCard(
            date = state.selected,
            today = state.today,
            count = state.days[state.selected]?.count ?: 0,
            onViewDay = { onViewDay(state.selected) },
        )
    }
}

/** One month, always 6 weeks tall so the page height doesn't jump while swiping. */
@Composable
private fun MonthGrid(
    month: YearMonth,
    days: Map<LocalDate, DaySummary>,
    selected: LocalDate,
    today: LocalDate,
    onSelect: (LocalDate) -> Unit,
) {
    val cells = remember(month) {
        val leading = WeekOrder.indexOf(month.atDay(1).dayOfWeek)
        val dates = List(leading) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
        dates + List(42 - dates.size) { null }
    }
    Column(Modifier.fillMaxWidth()) {
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    Box(Modifier
                        .weight(1f)
                        .height(72.dp), contentAlignment = Alignment.Center) {
                        if (date != null) {
                            DayCell(
                                date = date,
                                summary = days[date],
                                selected = date == selected,
                                isToday = date == today,
                                onClick = { onSelect(date) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    summary: DaySummary?,
    selected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val dayColor = when {
        isToday -> colors.secondary
        else -> colors.onBackground
    }
    val dayWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
    if (summary != null) {
        Column(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Polaroid(
                model = summary.cover.uri,
                tiltDegrees = polaroidTilt(date.toEpochDay()),
                photoAspectRatio = 1f,
                border = 3.dp,
                modifier = Modifier
                    .width(38.dp)
                    .then(
                        when {
                            selected -> Modifier.border(
                                BorderStroke(2.dp, colors.onBackground),
                                RoundedCornerShape(2.dp)
                            )

                            isToday -> Modifier.border(
                                BorderStroke(2.dp, colors.secondary),
                                RoundedCornerShape(2.dp)
                            )

                            else -> Modifier
                        }
                    ),
            ) {
                Text(
                    "${date.dayOfMonth}",
                    fontSize = 9.sp,
                    fontWeight = dayWeight,
                    color = if (isToday) colors.secondary else PolaroidInk,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    } else {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .then(
                    when {
                        selected -> Modifier.background(colors.inverseSurface)
                        // Today: a ring in the accent colour (filled when it's also selected).
                        isToday -> Modifier.border(
                            BorderStroke(1.5.dp, colors.secondary),
                            CircleShape
                        )

                        else -> Modifier
                    }
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${date.dayOfMonth}",
                fontSize = 15.sp,
                fontWeight = dayWeight,
                color = if (selected) colors.inverseOnSurface else dayColor,
            )
        }
    }
}

@Composable
private fun SelectedDayCard(date: LocalDate, today: LocalDate, count: Int, onViewDay: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val locale = LocalConfiguration.current.locales[0]
    val pattern = stringResource(R.string.journal_date_pattern)
    val title = when (date) {
        today -> stringResource(R.string.journal_today)
        today.minusDays(1) -> stringResource(R.string.journal_yesterday)
        else -> date.format(DateTimeFormatter.ofPattern("MM/dd"))
    }
    val subtitle = if (count > 0) {
        stringResource(
            R.string.group_subtitle,
            date.format(DateTimeFormatter.ofPattern(pattern, locale)),
            pluralStringResource(R.plurals.photo_count, count, count),
        )
    } else {
        stringResource(
            R.string.group_subtitle,
            date.format(DateTimeFormatter.ofPattern(pattern, locale)),
            stringResource(R.string.calendar_no_photos),
        )
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.surfaceContainerLowest,
        border = BorderStroke(1.dp, colors.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface
                )
                Text(subtitle, fontSize = 13.sp, color = colors.onSurfaceVariant)
            }
            Button(
                onClick = onViewDay,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.inverseSurface,
                    contentColor = colors.inverseOnSurface,
                ),
            ) { Text(stringResource(R.string.calendar_view_day), fontWeight = FontWeight.Bold) }
        }
    }
}
