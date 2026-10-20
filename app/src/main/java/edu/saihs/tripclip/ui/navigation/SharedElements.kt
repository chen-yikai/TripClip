package edu.saihs.tripclip.ui.navigation

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.navigation3.ui.LocalNavAnimatedContentScope

/** The app-wide [SharedTransitionScope] wrapping the NavDisplay; null outside of it (e.g. previews). */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

private val PhotoBoundsTransform = BoundsTransform { _, _ -> spring(dampingRatio = 0.85f, stiffness = 380f) }

/**
 * Morphs a photo's polaroid between screens: give the same [photoId] on both sides.
 * Must be used inside a NavDisplay entry.
 */
@Composable
fun Modifier.sharedPhoto(photoId: Long): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val animatedScope = LocalNavAnimatedContentScope.current
    return with(shared) {
        this@sharedPhoto.sharedBounds(
            sharedContentState = rememberSharedContentState(key = "photo-$photoId"),
            animatedVisibilityScope = animatedScope,
            boundsTransform = PhotoBoundsTransform,
        )
    }
}

/**
 * The shadow under a [sharedPhoto]. Kept separate from the cross-fading polaroid so there's exactly
 * one shadow during the transition: it's re-laid out at the animated bounds every frame (so it never
 * stretches), only the arriving side's copy is drawn, and it sits just under the moving polaroid.
 */
@Composable
fun Modifier.sharedPhotoShadow(photoId: Long): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val animatedScope = LocalNavAnimatedContentScope.current
    return with(shared) {
        this@sharedPhotoShadow.sharedBounds(
            sharedContentState = rememberSharedContentState(key = "photo-shadow-$photoId"),
            animatedVisibilityScope = animatedScope,
            enter = EnterTransition.None,
            exit = fadeOut(snap()),
            boundsTransform = PhotoBoundsTransform,
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
            zIndexInOverlay = -1f,
        )
    }
}

/**
 * For UI floating over a list of shared photos (pinned header, FAB): drawn above the photo while it
 * flies to or from another screen, instead of under it. It fades in/out with the same timing as
 * the page transition, since in the overlay it no longer inherits the page's fade.
 */
@Composable
fun Modifier.aboveSharedPhotos(): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val animatedScope = LocalNavAnimatedContentScope.current
    return with(shared) {
        with(animatedScope) {
            this@aboveSharedPhotos
                .renderInSharedTransitionScopeOverlay(zIndexInOverlay = 1f)
                .animateEnterExit(
                    enter = fadeIn(tween(210, delayMillis = 90)),
                    exit = fadeOut(tween(90)),
                )
        }
    }
}
