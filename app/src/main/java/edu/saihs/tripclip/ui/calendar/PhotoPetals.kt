package edu.saihs.tripclip.ui.calendar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.saihs.tripclip.data.Photo
import edu.saihs.tripclip.ui.components.Polaroid
import edu.saihs.tripclip.ui.theme.PolaroidInk
import edu.saihs.tripclip.ui.theme.handwritingFontFamily
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.random.Random

private val TimeFormat = DateTimeFormatter.ofPattern("HH:mm")

/** Fan angles (0° = upright) for 1, 2 or 3 polaroids, left to right. */
private fun fanAngles(count: Int): List<Float> = when (count) {
    1 -> listOf(-2f)
    2 -> listOf(-12f, 12f)
    else -> listOf(-20f, 0f, 20f)
}

/**
 * Up to three photos of [day], picked at random (stable for that day and photo set), shown as
 * framed polaroids that overlap and fan out from a shared point below them, like petals.
 * The middle one sits on top. Tapping a polaroid opens that photo.
 */
@Composable
fun PhotoPetals(
    day: LocalDate,
    photos: List<Photo>,
    onOpenPhoto: (Photo) -> Unit,
    modifier: Modifier = Modifier,
    polaroidWidth: Dp = 118.dp,
) {
    val picked = remember(day, photos.map { it.id }) {
        photos.shuffled(Random(day.toEpochDay())).take(3).sortedBy { it.takenAt }
    }
    val angles = fanAngles(picked.size)
    val hand = handwritingFontFamily()
    val zone = ZoneId.systemDefault()
    // Draw the outer polaroids first so the middle one overlaps them.
    val drawOrder = picked.indices.sortedByDescending { kotlin.math.abs(it - (picked.size - 1) / 2f) }

    Box(
        modifier
            .fillMaxWidth()
            .height(polaroidWidth * 1.6f)
            // Room above for the tilted corners, and below so the fan clears the day card.
            .padding(top = 12.dp, bottom = 20.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        drawOrder.forEach { index ->
            val photo = picked[index]
            val angle = angles[index]
            Box(
                Modifier
                    .width(polaroidWidth)
                    .graphicsLayer {
                        // Pivot well below the polaroids so they spread out like petals from one base;
                        // the further down, the further apart the side ones end up.
                        transformOrigin = TransformOrigin(0.5f, 2f)
                        rotationZ = angle
                    },
            ) {
                Polaroid(
                    model = photo.uri,
                    border = 6.dp,
                    contentDescription = photo.note,
                    onClick = { onOpenPhoto(photo) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        Instant.ofEpochMilli(photo.takenAt).atZone(zone).format(TimeFormat),
                        style = TextStyle(fontFamily = hand, fontSize = 13.sp, color = PolaroidInk.copy(alpha = 0.7f)),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
