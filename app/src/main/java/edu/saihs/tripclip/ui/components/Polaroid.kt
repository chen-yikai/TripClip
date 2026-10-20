package edu.saihs.tripclip.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import edu.saihs.tripclip.ui.navigation.sharedPhoto
import edu.saihs.tripclip.ui.navigation.sharedPhotoShadow
import edu.saihs.tripclip.ui.theme.PolaroidFrame

/**
 * A polaroid frame: photo on top, [caption] in the wider bottom margin.
 * Callers style the caption with [PolaroidInk] and the handwriting font.
 */
@Composable
fun Polaroid(
    model: Any?,
    modifier: Modifier = Modifier,
    tiltDegrees: Float = 0f,
    photoAspectRatio: Float = 1.08f,
    border: Dp = 10.dp,
    contentDescription: String? = null,
    onClick: (() -> Unit)? = null,
    /** Morphs this polaroid (and its shadow) to/from one with the same id on the next/previous screen. */
    sharedPhotoId: Long? = null,
    /** Replaces the photo of [model] (e.g. a live camera preview); laid out in the same photo area. */
    photo: (@Composable () -> Unit)? = null,
    caption: @Composable ColumnScope.() -> Unit = {},
) {
    val shape = RoundedCornerShape(2.dp)
    val frameModifier = (if (sharedPhotoId != null) Modifier.sharedPhoto(sharedPhotoId) else Modifier)
        .rotate(tiltDegrees)
    val content: @Composable () -> Unit = {
        Column(Modifier.padding(start = border, end = border, top = border)) {
            val photoArea = Modifier
                .fillMaxWidth()
                .aspectRatio(photoAspectRatio)
                .background(PhotoPlaceholder)
            if (photo != null) {
                Box(photoArea.clipToBounds()) { photo() }
            } else {
                AsyncImage(
                    model = model,
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Crop,
                    modifier = photoArea,
                )
            }
            Column(Modifier.padding(vertical = border), content = caption)
        }
    }
    Box(modifier) {
        // The shadow is its own layer behind the frame (see sharedPhotoShadow), drawn rather than
        // elevation-based so it stays soft and correctly sized while moving between screens.
        Box(
            Modifier
                .matchParentSize()
                .then(if (sharedPhotoId != null) Modifier.sharedPhotoShadow(sharedPhotoId) else Modifier)
                .rotate(tiltDegrees)
                .dropShadow(shape, Shadow(radius = 10.dp, color = Color.Black, offset = DpOffset(0.dp, 3.dp), alpha = 0.22f))
        )
        if (onClick != null) {
            Surface(onClick = onClick, modifier = frameModifier, shape = shape, color = PolaroidFrame, content = content)
        } else {
            Surface(modifier = frameModifier, shape = shape, color = PolaroidFrame, content = content)
        }
    }
}

/** Stable, slight random tilt (about ±2.5°) for a photo, so it doesn't change between frames. */
fun polaroidTilt(seed: Long): Float {
    val bucket = ((seed * 2_654_435_761L) ushr 16).mod(11L).toInt() // 0..10
    return (bucket - 5) * 0.5f
}

private val PhotoPlaceholder = Color(0xFFD9D4CA)
