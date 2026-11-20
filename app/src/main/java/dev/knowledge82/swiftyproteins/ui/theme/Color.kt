package dev.knowledge82.swiftyproteins.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The palette of the "Cult of the Machine" look (document 08a). Every TEXT colour here was checked against the
 * backgrounds (WCAG contrast, document 08a section 3 and 08b): bone, ash, gold, brass, phosphor, amber and [Error]
 * pass 4.5:1 on [Void], [Iron] and [Plate].
 *
 * Two traps, kept as names that warn:
 * - [MartianRed] and [Blood] are FILLS only. As text on a dark background they give 2.0:1 and 3.1:1 (unreadable).
 * - [Rivet] is decoration only (1.6:1).
 */
object SwiftyColors {
    val Void = Color(0xFF0A0A0B)        // app background
    val Iron = Color(0xFF17171A)        // panels, bars
    val Plate = Color(0xFF232328)       // raised cards, inputs, dialogs
    val Rivet = Color(0xFF3A3A42)       // decorative seams ONLY
    val MartianRed = Color(0xFF8E1B14)  // primary button fill ONLY (bone text on it: 6.9:1)
    val Blood = Color(0xFFC0291D)       // small decorative fills ONLY (bone on it: 4.47:1, fails)
    val Brass = Color(0xFFB8893A)       // borders, icons, secondary text
    val Gold = Color(0xFFE0B65C)        // headings, selected controls
    val Bone = Color(0xFFE9E0C9)        // main text
    val Ash = Color(0xFFA39D8E)         // secondary text
    val Phosphor = Color(0xFF43E07A)    // machine data: counts, coordinates
    val Amber = Color(0xFFF2A12B)       // warnings
    val Error = Color(0xFFFF5A4A)       // error text and icons

    /** Panels drawn OVER the 3D scene: solid, 96 % opaque (no blur: it cannot reach the GPU surface below). */
    val IronOverlay = Iron.copy(alpha = 0.96f)
}
