package edu.saihs.tripclip.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    secondary = AccentLink,
    onSecondary = BackgroundDark,
    secondaryContainer = SurfaceDarkHigh,
    onSecondaryContainer = TextPrimary,
    background = BackgroundDark,
    onBackground = TextPrimary,
    surface = BackgroundDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceDark,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = SurfaceDark,
    surfaceContainer = SurfaceDark,
    surfaceContainerHigh = SurfaceDarkHigh,
    outline = OutlineDark,
    outlineVariant = OutlineDark,
    inverseSurface = PolaroidFrame,
    inverseOnSurface = PolaroidInk,
)

private val LightColorScheme = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    secondary = Accent,
    onSecondary = Color.White,
    secondaryContainer = SurfaceLightHigh,
    onSecondaryContainer = PolaroidInk,
    background = BackgroundLight,
    onBackground = PolaroidInk,
    surface = BackgroundLight,
    onSurface = PolaroidInk,
    surfaceVariant = SurfaceLight,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainerLowest = SurfaceLightLowest,
    surfaceContainer = SurfaceLight,
    surfaceContainerHigh = SurfaceLightHigh,
    outline = OutlineLight,
    outlineVariant = OutlineLight,
    inverseSurface = PolaroidInk,
    inverseOnSurface = PolaroidFrame,
)

@Composable
fun TripClipTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
