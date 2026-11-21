package dev.knowledge82.swiftyproteins.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * A [PaletteSpec] as Compose colours. Provided to the whole app through [LocalSwiftyPalette] (document 21).
 *
 * Why a CompositionLocal and not a global variable: a part of the tree can have ANOTHER palette. The boot screen is a
 * CRT starting up, so it is always the dark one, whatever the theme. The palette goes where the tree says it goes.
 */
@Immutable
class SwiftyPalette(val spec: PaletteSpec) {
    private fun c(rgb: Int) = Color(0xFF000000.toInt() or rgb)

    val isDark = spec.isDark
    val abyss = c(spec.abyss)
    val console = c(spec.console)
    val iron = c(spec.iron)
    val plate = c(spec.plate)
    val bronze = c(spec.bronze)
    val brass = c(spec.brass)
    val gold = c(spec.gold)
    val bone = c(spec.bone)
    val phosphor = c(spec.phosphor)
    val phosphorDim = c(spec.phosphorDim)
    val marsRed = c(spec.marsRed)
    val onMarsRed = c(spec.onMarsRed)
    val opticRed = c(spec.opticRed)
    val amber = c(spec.amber)
    val ash = c(spec.ash)
    val disabled = c(spec.disabled)
    val metalHighlight = c(spec.metalHighlight)
    val metalShadow = c(spec.metalShadow)

    companion object {
        val Dark = SwiftyPalette(PaletteSpec.DARK)
        val Light = SwiftyPalette(PaletteSpec.LIGHT)
        fun of(dark: Boolean): SwiftyPalette = if (dark) Dark else Light
    }
}

/**
 * The palette of this part of the tree. STATIC: reading it is free, and changing it recomposes everything below,
 * which is right for a theme (it changes once in a while, and then everything must change).
 */
val LocalSwiftyPalette = staticCompositionLocalOf { SwiftyPalette.Dark }

/**
 * The colours of the cogitator look, by role (design v2, document 08c; light version, document 21). Each one reads the
 * palette of the place where it is used, so every screen follows the theme without knowing about it.
 *
 * Contrast is measured with the WCAG formula and TESTED for both palettes (PaletteSpec). Dark, on the console glass:
 * phosphor 11.1, bone 14.0, gold 10.0, brass 6.1, ash 8.4, amber 9.0, optic red 5.4. Not for text:
 * - [MarsRed]: FILLS only, with [OnMarsRed] text (6.6 dark, 7.6 light).
 * - [PhosphorDim], [Bronze]: decoration, borders, never text.
 * - [Disabled]: only for disabled controls, which WCAG exempts.
 *
 * These are `@Composable` getters: usable while composing, NOT inside a `drawBehind { }` (a drawing lambda is not
 * composable). There, read the colours into local values first (see `CogitatorPanel`).
 */
object SwiftyColors {
    val palette: SwiftyPalette @Composable @ReadOnlyComposable get() = LocalSwiftyPalette.current

    val Abyss: Color @Composable @ReadOnlyComposable get() = palette.abyss          // app background
    val Console: Color @Composable @ReadOnlyComposable get() = palette.console      // CRT glass of the panels
    val Iron: Color @Composable @ReadOnlyComposable get() = palette.iron            // metal
    val Plate: Color @Composable @ReadOnlyComposable get() = palette.plate          // tab plates on frames
    val Bronze: Color @Composable @ReadOnlyComposable get() = palette.bronze        // outer frame, decoration only
    val Brass: Color @Composable @ReadOnlyComposable get() = palette.brass          // engraved inner trim, small labels
    val Gold: Color @Composable @ReadOnlyComposable get() = palette.gold            // gothic headings
    val Bone: Color @Composable @ReadOnlyComposable get() = palette.bone            // main text
    val Phosphor: Color @Composable @ReadOnlyComposable get() = palette.phosphor    // console text and data
    val PhosphorDim: Color @Composable @ReadOnlyComposable get() = palette.phosphorDim  // decoration only
    val MarsRed: Color @Composable @ReadOnlyComposable get() = palette.marsRed      // primary button FILLS only
    val OnMarsRed: Color @Composable @ReadOnlyComposable get() = palette.onMarsRed  // text on a mars-red fill
    val OpticRed: Color @Composable @ReadOnlyComposable get() = palette.opticRed    // errors (text and icons)
    val Amber: Color @Composable @ReadOnlyComposable get() = palette.amber          // warnings
    val Ash: Color @Composable @ReadOnlyComposable get() = palette.ash              // secondary text
    val Disabled: Color @Composable @ReadOnlyComposable get() = palette.disabled    // disabled controls only

    /** Panels drawn OVER the 3D scene: solid, 94 % opaque (no blur: it cannot reach the GPU surface below). */
    val ConsoleOverlay: Color @Composable @ReadOnlyComposable get() = palette.console.copy(alpha = 0.94f)
}
