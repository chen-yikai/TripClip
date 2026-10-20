package edu.saihs.tripclip.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Notebook grid background (24dp minor cells, 120dp major cells).
 * Only the journal list uses this; other screens are a flat background.
 */
@Composable
fun Modifier.notebookGrid(
    minor: Dp = 24.dp,
    major: Dp = 120.dp,
    dark: Boolean = isSystemInDarkTheme(),
): Modifier {
    val background = if (dark) BackgroundDark else BackgroundLight
    val minorColor = if (dark) GridLineDark else GridLineLight
    val majorColor = if (dark) GridLineDarkMajor else GridLineLightMajor
    return drawBehind {
        drawRect(background)
        val minorPx = minor.toPx()
        val ratio = (major / minor).toInt().coerceAtLeast(1)
        val stroke = 1.dp.toPx()
        fun lineColor(i: Int): Color = if (i % ratio == 0) majorColor else minorColor

        var i = 0
        var x = 0f
        while (x <= size.width) {
            drawLine(lineColor(i), Offset(x, 0f), Offset(x, size.height), stroke)
            x += minorPx; i++
        }
        i = 0
        var y = 0f
        while (y <= size.height) {
            drawLine(lineColor(i), Offset(0f, y), Offset(size.width, y), stroke)
            y += minorPx; i++
        }
    }
}
