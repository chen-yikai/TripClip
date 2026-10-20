package edu.saihs.tripclip.ui.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith

/**
 * Google-style shared-axis page transition: a short horizontal slide (1/10 of the width)
 * with emphasized easing, cross-faded. [forward] is true for push, false for pop.
 */
internal fun pageTransition(forward: Boolean): ContentTransform {
    val direction = if (forward) 1 else -1
    return (
        slideInHorizontally(tween(300, easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f))) {
            direction * it / 10
        } + fadeIn(tween(210, delayMillis = 90))
    ) togetherWith (
        slideOutHorizontally(tween(300, easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f))) {
            -direction * it / 10
        } + fadeOut(tween(90))
    )
}
