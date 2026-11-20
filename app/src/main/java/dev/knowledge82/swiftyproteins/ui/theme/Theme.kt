package dev.knowledge82.swiftyproteins.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// Material 3 roles mapped onto the palette (design v2, document 08c). Only a DARK scheme: the app has one look (D26).
private val SwiftyColorScheme = darkColorScheme(
    primary = SwiftyColors.MarsRed,
    onPrimary = SwiftyColors.Bone,
    primaryContainer = SwiftyColors.Iron,
    onPrimaryContainer = SwiftyColors.Bone,
    inversePrimary = SwiftyColors.Gold,
    secondary = SwiftyColors.Brass,
    onSecondary = SwiftyColors.Abyss,
    secondaryContainer = SwiftyColors.Console,
    onSecondaryContainer = SwiftyColors.Phosphor,
    tertiary = SwiftyColors.Phosphor,
    onTertiary = SwiftyColors.Abyss,
    tertiaryContainer = SwiftyColors.Console,
    onTertiaryContainer = SwiftyColors.Phosphor,
    background = SwiftyColors.Abyss,
    onBackground = SwiftyColors.Bone,
    surface = SwiftyColors.Iron,
    onSurface = SwiftyColors.Bone,
    surfaceVariant = SwiftyColors.Console,
    onSurfaceVariant = SwiftyColors.Phosphor,
    surfaceContainerLowest = SwiftyColors.Abyss,
    surfaceContainerLow = SwiftyColors.Console,
    surfaceContainer = SwiftyColors.Console,
    surfaceContainerHigh = SwiftyColors.Iron,
    surfaceContainerHighest = SwiftyColors.Iron,
    outline = SwiftyColors.Brass,
    outlineVariant = SwiftyColors.Bronze,
    error = SwiftyColors.OpticRed,
    onError = SwiftyColors.Abyss,
    errorContainer = SwiftyColors.MarsRed,
    onErrorContainer = SwiftyColors.Bone,
    inverseSurface = SwiftyColors.Bone,
    inverseOnSurface = SwiftyColors.Abyss,
    surfaceTint = SwiftyColors.Brass,
    scrim = SwiftyColors.Abyss,
)

/**
 * The theme of the whole app. Always dark and NEVER "dynamic colour" (Android 12+ would otherwise repaint the app
 * with the colours of the user's wallpaper, which was the template's default).
 */
@Composable
fun SwiftyProteinsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SwiftyColorScheme,
        typography = SwiftyTypography,
        shapes = SwiftyShapes,
        content = content,
    )
}
