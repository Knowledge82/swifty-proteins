package dev.knowledge82.swiftyproteins.ui.theme

import kotlin.math.pow

/**
 * The two palettes of the app as plain numbers (0xRRGGBB), so that their CONTRAST can be tested on the JVM
 * (bonus VII.2 "dark mode", document 21). [SwiftyPalette] turns them into Compose colours.
 *
 * The ROLES are the same in both; only the values change. A role is named after what it is in the dark cogitator
 * look ("phosphor" = console text); in the light parchment look the same role is an ink of the same family
 * (verdigris green, rubric red, iron-gall black), the way an illuminated manuscript uses colour.
 *
 * Text roles must reach 4.5 : 1 (WCAG AA) on the panel glass [console] and on the background [abyss]; the tests check
 * every one, in both palettes. Decoration roles ([bronze], [phosphorDim]) and [disabled] are exempt, as in WCAG.
 */
data class PaletteSpec(
    val isDark: Boolean,
    val abyss: Int,        // screen background
    val console: Int,      // panel glass
    val iron: Int,         // metal plates, disabled buttons
    val plate: Int,        // the tab plates on frame edges
    val bronze: Int,       // frames: decoration only
    val brass: Int,        // trim, small labels (text)
    val gold: Int,         // headings (text)
    val bone: Int,         // main text
    val phosphor: Int,     // console text and data (text)
    val phosphorDim: Int,  // scanlines, rules: decoration only
    val marsRed: Int,      // primary button FILL
    val onMarsRed: Int,    // text on that fill
    val opticRed: Int,     // errors (text)
    val amber: Int,        // warnings (text)
    val ash: Int,          // secondary text
    val disabled: Int,     // disabled controls only
    val metalHighlight: Int,
    val metalShadow: Int,
    /** How strong the CRT text glow is (0 = none). A glow behind dark ink on parchment would only blur it. */
    val glow: Float,
    /** Opacity of the dark CRT and iron textures; on parchment they would only make it grey. */
    val crtTexture: Float,
    val ironTexture: Float,
) {
    /** The roles used as text, for the contrast tests and for anyone adding a palette. */
    val textRoles: Map<String, Int>
        get() = mapOf("brass" to brass, "gold" to gold, "bone" to bone, "phosphor" to phosphor, "opticRed" to opticRed, "amber" to amber, "ash" to ash)

    companion object {
        /** The cogitator (design v2, document 08c): the app's look since the beginning. */
        val DARK = PaletteSpec(
            isDark = true,
            abyss = 0x050806, console = 0x08120B, iron = 0x16161A, plate = 0x252125,
            bronze = 0x7A5A2E, brass = 0xB8893A, gold = 0xE0B65C, bone = 0xE6DCC3,
            phosphor = 0x43E07A, phosphorDim = 0x1F7A42,
            marsRed = 0x8E1B14, onMarsRed = 0xE6DCC3, opticRed = 0xFF3B2F, amber = 0xF2A12B,
            ash = 0xADAD9B, disabled = 0x7B796C,
            metalHighlight = 0xD6AA60, metalShadow = 0x4A361B,
            glow = 1f, crtTexture = 0.55f, ironTexture = 0.5f,
        )

        /** The parchment: vellum panels, iron-gall ink, rubric red headings, verdigris data. Same bronze frames. */
        val LIGHT = PaletteSpec(
            isDark = false,
            abyss = 0xE4D8BC, console = 0xF3EBD6, iron = 0xD9CBA8, plate = 0xCDBB8E,
            bronze = 0x8C6A36, brass = 0x6B4A17, gold = 0x7A1F12, bone = 0x2A2016,
            phosphor = 0x1B5E34, phosphorDim = 0x8FB39A,
            marsRed = 0x8E1B14, onMarsRed = 0xF3EBD6, opticRed = 0xA8201A, amber = 0x7A4E00,
            ash = 0x5C5545, disabled = 0x8A8372,
            metalHighlight = 0xE8C78A, metalShadow = 0x5E4522,
            glow = 0f, crtTexture = 0f, ironTexture = 0.06f,
        )

        fun of(dark: Boolean): PaletteSpec = if (dark) DARK else LIGHT
    }
}

/** The WCAG 2 contrast ratio between two 0xRRGGBB colours: 1 (none) to 21 (black on white). */
object Contrast {

    fun ratio(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /** Relative luminance (WCAG): each sRGB channel back to linear light, weighted by how the eye sees it. */
    fun luminance(rgb: Int): Double {
        fun channel(shift: Int): Double {
            val c = ((rgb shr shift) and 0xFF) / 255.0
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }
}
