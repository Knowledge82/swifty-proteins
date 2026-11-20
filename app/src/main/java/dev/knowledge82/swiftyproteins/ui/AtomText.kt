package dev.knowledge82.swiftyproteins.ui

import dev.knowledge82.swiftyproteins.molecule.Atom
import dev.knowledge82.swiftyproteins.scene.ElementStyle
import java.util.Locale

/** The texts of the atom card. Pure Kotlin, tested. */
object AtomText {

    /** "C01 · Carbon (C)". */
    fun title(atom: Atom): String = "${atom.name} · ${ElementStyle.nameOf(atom.element)} (${atom.element})"

    /**
     * "X 12.184 Å", three decimals. Locale.US: a dot, never a comma, and a plain ASCII minus "-" (the design's
     * previews show a missing glyph where a typographic minus U+2212 was used).
     */
    fun coordinate(axis: String, value: Float): String = "$axis ${value(value)}"

    /** "12.184 Å" alone: the readout of design v2 puts the axis letter and the value in two columns. */
    fun value(value: Float): String = String.format(Locale.US, "%.3f Å", value)
}
