package dev.knowledge82.swiftyproteins.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// Material 3 roles mapped onto the palette (document 08b). Only a DARK scheme: the app has one look (D26).
private val SwiftyColorScheme = darkColorScheme(
    primary = SwiftyColors.MartianRed,
    onPrimary = SwiftyColors.Bone,
    primaryContainer = SwiftyColors.MartianRed,
    onPrimaryContainer = SwiftyColors.Bone,
    inversePrimary = SwiftyColors.Gold,
    secondary = SwiftyColors.Brass,
    onSecondary = SwiftyColors.Void,
    secondaryContainer = SwiftyColors.Plate,
    onSecondaryContainer = SwiftyColors.Bone,
    tertiary = SwiftyColors.Phosphor,
    onTertiary = SwiftyColors.Void,
    tertiaryContainer = SwiftyColors.Plate,
    onTertiaryContainer = SwiftyColors.Phosphor,
    background = SwiftyColors.Void,
    onBackground = SwiftyColors.Bone,
    surface = SwiftyColors.Iron,
    onSurface = SwiftyColors.Bone,
    surfaceVariant = SwiftyColors.Plate,
    onSurfaceVariant = SwiftyColors.Ash,
    surfaceContainerLowest = SwiftyColors.Void,
    surfaceContainerLow = SwiftyColors.Iron,
    surfaceContainer = SwiftyColors.Plate,
    surfaceContainerHigh = SwiftyColors.Plate,
    surfaceContainerHighest = SwiftyColors.Plate,
    outline = SwiftyColors.Brass,
    outlineVariant = SwiftyColors.Rivet,
    error = SwiftyColors.Error,
    onError = SwiftyColors.Void,
    errorContainer = SwiftyColors.MartianRed,
    onErrorContainer = SwiftyColors.Bone,
    inverseSurface = SwiftyColors.Bone,
    inverseOnSurface = SwiftyColors.Void,
    surfaceTint = SwiftyColors.Brass,
    scrim = SwiftyColors.Void,
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
