package dev.knowledge82.swiftyproteins.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The palette of the cogitator look (design v2, document 08c). Contrast measured with the WCAG formula on the
 * console glass (#08120B): phosphor 11.1, bone 14.0, gold 10.0, brass 6.1, ash 8.4, amber 9.0, optic red 5.4 -> all
 * pass 4.5:1 as text. The ones that do NOT, kept as warnings in the names and comments:
 * - [MarsRed]: FILLS only (bone text on it: 6.6:1).
 * - [PhosphorDim] (3.6), [Bronze] (3.0): decoration, borders, never text.
 * - [Disabled] (4.35): only for disabled controls, which WCAG exempts.
 */
object SwiftyColors {
    val Abyss = Color(0xFF050806)        // app background, behind the 3D scene
    val Console = Color(0xFF08120B)      // CRT glass of the panels
    val Iron = Color(0xFF16161A)         // metal
    val Plate = Color(0xFF252125)        // tab plates on frames
    val Bronze = Color(0xFF7A5A2E)       // outer frame, decoration only
    val Brass = Color(0xFFB8893A)        // engraved inner trim, small labels
    val Gold = Color(0xFFE0B65C)         // gothic headings
    val Bone = Color(0xFFE6DCC3)         // main light text
    val Phosphor = Color(0xFF43E07A)     // console text and data: the main UI text colour
    val PhosphorDim = Color(0xFF1F7A42)  // scanlines, binary streams, dividers: decoration only
    val MarsRed = Color(0xFF8E1B14)      // primary button and wax-seal FILLS only
    val OpticRed = Color(0xFFFF3B2F)     // errors (text and icons)
    val Amber = Color(0xFFF2A12B)        // warnings, candle glow
    val Ash = Color(0xFFADAD9B)          // secondary text
    val Disabled = Color(0xFF7B796C)     // disabled controls only
    val Selected = Color(0xFFFFED46)     // selected atom (the scene uses its own, close yellow)

    /** Panels drawn OVER the 3D scene: solid, 94 % opaque (no blur: it cannot reach the GPU surface below). */
    val ConsoleOverlay = Console.copy(alpha = 0.94f)
}
