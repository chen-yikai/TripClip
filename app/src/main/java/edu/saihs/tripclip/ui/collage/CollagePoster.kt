package edu.saihs.tripclip.ui.collage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import edu.saihs.tripclip.R
import edu.saihs.tripclip.data.Photo
import edu.saihs.tripclip.ui.components.Polaroid
import edu.saihs.tripclip.ui.theme.AccentLink
import edu.saihs.tripclip.ui.theme.BackgroundDark
import edu.saihs.tripclip.ui.theme.PolaroidInk
import edu.saihs.tripclip.ui.theme.TextPrimary
import edu.saihs.tripclip.ui.theme.TextSecondary
import edu.saihs.tripclip.ui.theme.handwritingFontFamily
import edu.saihs.tripclip.ui.theme.notebookGrid
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.random.Random

/** Output size of the collage image (Instagram Story). */
const val PosterWidthPx = 1080
const val PosterHeightPx = 1920

private val TimeFormat = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Poster scale: the design is laid out on a 360 × 640 grid; [u] converts design units to the
 * poster's real size, so the same layout renders identically in the preview and at 1080 × 1920.
 */
private class PosterScale(val unit: Dp, private val density: androidx.compose.ui.unit.Density) {
    fun dp(v: Number): Dp = unit * v.toFloat()
    fun sp(v: Number): TextUnit = with(density) { dp(v).toSp() }
}

/**
 * The daily collage. Lay it out at exactly [PosterWidthPx] × [PosterHeightPx] pixels; it scales its
 * content to whatever size it is given.
 */
@Composable
fun CollagePoster(
    date: LocalDate,
    photos: List<Photo>,
    options: CollageOptions,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val hand = handwritingFontFamily()
    val handBold = handwritingFontFamily(FontWeight.Bold)

    BoxWithConstraints(modifier.background(BackgroundDark)) {
        val s = remember(maxWidth, density) { PosterScale(maxWidth / 360f, density) }
        val places = photos.mapNotNull { it.placeName }.distinct()
        val featured = remember(photos, options.layout, options.seed) { pickFeatured(photos, options) }

        Box(Modifier.fillMaxSize().notebookGrid(minor = s.dp(18), major = s.dp(90), dark = true))

        Column(Modifier.fillMaxSize().padding(horizontal = s.dp(22), vertical = s.dp(26))) {
            // Header: "10.19 Mon." and the places of the day.
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    date.format(DateTimeFormatter.ofPattern("MM.dd", Locale.ENGLISH)),
                    style = TextStyle(fontFamily = handBold, fontSize = s.sp(44), color = TextPrimary),
                )
                Spacer(Modifier.width(s.dp(6)))
                Text(
                    date.format(DateTimeFormatter.ofPattern("EEE.", Locale.ENGLISH)),
                    style = TextStyle(fontFamily = hand, fontSize = s.sp(22), color = AccentLink),
                    modifier = Modifier.padding(bottom = s.dp(6)),
                )
            }
            if (places.isNotEmpty()) {
                Text(
                    places.take(3).joinToString("・"),
                    style = TextStyle(fontFamily = hand, fontSize = s.sp(14), color = TextSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = s.dp(14))
            ) {
                when (options.layout) {
                    CollageLayout.Scatter -> ScatterLayout(featured, options, s, hand)
                    CollageLayout.Grid -> GridLayout(featured, options, s, hand)
                    CollageLayout.Film -> FilmLayout(featured, options, s, hand)
                }
            }

            // Footer: "4 places · 6 photos" and the wordmark.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(
                        R.string.group_subtitle,
                        pluralStringResource(R.plurals.place_count, places.size, places.size),
                        pluralStringResource(R.plurals.photo_count, photos.size, photos.size),
                    ),
                    style = TextStyle(fontFamily = hand, fontSize = s.sp(12), color = TextSecondary),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.app_name),
                    style = TextStyle(fontFamily = handBold, fontSize = s.sp(24), color = TextPrimary),
                )
            }
        }
    }
}

private fun capacity(layout: CollageLayout) = when (layout) {
    CollageLayout.Scatter -> 4
    CollageLayout.Grid -> 6
    CollageLayout.Film -> 4
}

/**
 * Chooses which photos appear and in what order. Before any shuffle: capture order, spread evenly
 * across the day when there are more than fit. After a shuffle: a random subset in a random order,
 * so the photos land in different spots in every layout (grid cells, film frames, scatter spots).
 */
private fun pickFeatured(photos: List<Photo>, options: CollageOptions): List<Photo> {
    val max = capacity(options.layout)
    if (options.seed == 0) {
        if (photos.size <= max) return photos
        return List(max) { i -> photos[i * (photos.size - 1) / (max - 1)] }
    }
    return photos.shuffled(Random(options.seed)).take(max)
}

@Composable
private fun ScatterLayout(photos: List<Photo>, options: CollageOptions, s: PosterScale, hand: FontFamily) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val rows = ((photos.size + 1) / 2).coerceAtLeast(1)
        val rowHeight = maxHeight / rows
        val cardWidth = s.dp(150)
        photos.forEachIndexed { i, photo ->
            val random = Random(options.seed * 31 + i * 7 + photo.id.toInt())
            val col = if (photos.size == 1) 0.5f else (i % 2).toFloat()
            val x = (maxWidth - cardWidth) * col + s.dp(random.nextInt(-8, 9))
            val y = rowHeight * (i / 2) + s.dp(random.nextInt(0, 18))
            PosterPolaroid(
                photo = photo,
                options = options,
                s = s,
                hand = hand,
                tilt = random.nextInt(-60, 61) / 10f,
                modifier = Modifier
                    .offset(x = x.coerceAtLeast(0.dp), y = y)
                    .width(cardWidth),
            )
        }
    }
}

@Composable
private fun GridLayout(photos: List<Photo>, options: CollageOptions, s: PosterScale, hand: FontFamily) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(s.dp(12), Alignment.CenterVertically),
    ) {
        photos.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(s.dp(12)), modifier = Modifier.fillMaxWidth()) {
                row.forEach { photo ->
                    PosterPolaroid(photo, options, s, hand, tilt = 0f, modifier = Modifier.weight(1f), compact = true)
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun FilmLayout(photos: List<Photo>, options: CollageOptions, s: PosterScale, hand: FontFamily) {
    val zone = ZoneId.systemDefault()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .fillMaxHeight()
                .background(FilmBase, RoundedCornerShape(s.dp(4)))
                .padding(horizontal = s.dp(6))
        ) {
            Sprockets(s)
            Column(
                Modifier
                    .width(s.dp(210))
                    .fillMaxHeight()
                    .padding(vertical = s.dp(10)),
                verticalArrangement = Arrangement.spacedBy(s.dp(10), Alignment.CenterVertically),
            ) {
                photos.forEach { photo ->
                    Column(Modifier.weight(1f, fill = false)) {
                        AsyncImage(
                            model = photo.uri,
                            contentDescription = photo.note,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1.5f)
                                .background(Color.DarkGray),
                        )
                        val caption = listOfNotNull(
                            photo.note?.takeIf { options.showNote },
                            photo.placeName?.takeIf { options.showPlace },
                            Instant.ofEpochMilli(photo.takenAt).atZone(zone).format(TimeFormat).takeIf { options.showTime },
                        ).joinToString("・")
                        if (caption.isNotEmpty()) {
                            Text(
                                caption,
                                style = TextStyle(fontFamily = hand, fontSize = s.sp(10), color = FilmInk),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = s.dp(3)),
                            )
                        }
                    }
                }
            }
            Sprockets(s)
        }
    }
}

@Composable
private fun Sprockets(s: PosterScale) {
    Column(
        Modifier
            .fillMaxHeight()
            .padding(horizontal = s.dp(4), vertical = s.dp(6)),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        repeat(18) {
            Box(
                Modifier
                    .size(width = s.dp(8), height = s.dp(11))
                    .background(FilmHole, RoundedCornerShape(s.dp(2)))
            )
        }
    }
}

@Composable
private fun PosterPolaroid(
    photo: Photo,
    options: CollageOptions,
    s: PosterScale,
    hand: FontFamily,
    tilt: Float,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val time = remember(photo.takenAt) {
        Instant.ofEpochMilli(photo.takenAt).atZone(ZoneId.systemDefault()).format(TimeFormat)
    }
    Polaroid(
        model = photo.uri,
        tiltDegrees = tilt,
        border = s.dp(if (compact) 6 else 7),
        photoAspectRatio = if (compact) 1.2f else 1.08f,
        contentDescription = photo.note,
        modifier = modifier,
    ) {
        if (options.showNote && photo.note != null) {
            Text(
                photo.note,
                style = TextStyle(fontFamily = hand, fontSize = s.sp(11), lineHeight = s.sp(14), color = PolaroidInk),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (options.showPlace || options.showTime) {
            Row(Modifier.padding(top = s.dp(2)), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (options.showPlace) photo.placeName.orEmpty() else "",
                    style = TextStyle(fontFamily = hand, fontSize = s.sp(8), color = PolaroidInk.copy(alpha = 0.55f)),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (options.showTime) {
                    Text(
                        time,
                        style = TextStyle(fontFamily = hand, fontSize = s.sp(9), color = PolaroidInk.copy(alpha = 0.55f)),
                    )
                }
            }
        }
        if (!options.showNote && !options.showPlace && !options.showTime) {
            Spacer(Modifier.height(s.dp(10)))
        }
    }
}

private val FilmBase = Color(0xFF151413)
private val FilmHole = Color(0xFFE9E4DA)
private val FilmInk = Color(0xFFD9D3C7)
