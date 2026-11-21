package dev.knowledge82.swiftyproteins.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember

// Material 3 roles mapped onto the palette (design v2, document 08c; light version, document 21). The SAME mapping for
// both palettes: only the values differ. The Material components we use (text fields, dialogs, menus) read these.
private fun schemeOf(p: SwiftyPalette): ColorScheme =
    if (p.isDark) darkColorScheme(
        primary = p.marsRed,
        onPrimary = p.onMarsRed,
        primaryContainer = p.iron,
        onPrimaryContainer = p.bone,
        inversePrimary = p.gold,
        secondary = p.brass,
        onSecondary = p.abyss,
        secondaryContainer = p.console,
        onSecondaryContainer = p.phosphor,
        tertiary = p.phosphor,
        onTertiary = p.abyss,
        tertiaryContainer = p.console,
        onTertiaryContainer = p.phosphor,
        background = p.abyss,
        onBackground = p.bone,
        surface = p.iron,
        onSurface = p.bone,
        surfaceVariant = p.console,
        onSurfaceVariant = p.phosphor,
        surfaceTint = p.brass,
        inverseSurface = p.bone,
        inverseOnSurface = p.abyss,
        error = p.opticRed,
        onError = p.abyss,
        errorContainer = p.marsRed,
        onErrorContainer = p.onMarsRed,
        outline = p.brass,
        outlineVariant = p.bronze,
        scrim = p.abyss,
        surfaceContainerLowest = p.abyss,
        surfaceContainerLow = p.console,
        surfaceContainer = p.console,
        surfaceContainerHigh = p.iron,
        surfaceContainerHighest = p.iron,
    ) else lightColorScheme(
        primary = p.marsRed,
        onPrimary = p.onMarsRed,
        primaryContainer = p.iron,
        onPrimaryContainer = p.bone,
        inversePrimary = p.gold,
        secondary = p.brass,
        onSecondary = p.abyss,
        secondaryContainer = p.console,
        onSecondaryContainer = p.phosphor,
        tertiary = p.phosphor,
        onTertiary = p.abyss,
        tertiaryContainer = p.console,
        onTertiaryContainer = p.phosphor,
        background = p.abyss,
        onBackground = p.bone,
        surface = p.iron,
        onSurface = p.bone,
        surfaceVariant = p.console,
        onSurfaceVariant = p.phosphor,
        surfaceTint = p.brass,
        inverseSurface = p.bone,
        inverseOnSurface = p.abyss,
        error = p.opticRed,
        onError = p.abyss,
        errorContainer = p.marsRed,
        onErrorContainer = p.onMarsRed,
        outline = p.brass,
        outlineVariant = p.bronze,
        scrim = p.abyss,
        surfaceContainerLowest = p.abyss,
        surfaceContainerLow = p.console,
        surfaceContainer = p.console,
        surfaceContainerHigh = p.iron,
        surfaceContainerHighest = p.iron,
    )

/**
 * The theme of the whole app: the palette ([LocalSwiftyPalette]) and the matching Material scheme. NEVER "dynamic
 * colour" (Android 12+ would otherwise repaint the app with the colours of the user's wallpaper).
 *
 * @param palette dark or light (document 21); the app decides it from the settings and the phone
 */
@Composable
fun SwiftyProteinsTheme(palette: SwiftyPalette = SwiftyPalette.Dark, content: @Composable () -> Unit) {
    val scheme = remember(palette) { schemeOf(palette) }
    CompositionLocalProvider(LocalSwiftyPalette provides palette) {
        MaterialTheme(colorScheme = scheme, typography = SwiftyTypography, shapes = SwiftyShapes, content = content)
    }
}

/** A part of the tree that must be dark whatever the theme (the boot screen: a CRT starting up). */
@Composable
fun AlwaysDark(content: @Composable () -> Unit) = SwiftyProteinsTheme(SwiftyPalette.Dark, content)
