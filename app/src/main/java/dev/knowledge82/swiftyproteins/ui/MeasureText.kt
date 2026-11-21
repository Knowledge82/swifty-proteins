package dev.knowledge82.swiftyproteins.ui

import dev.knowledge82.swiftyproteins.molecule.Molecule
import dev.knowledge82.swiftyproteins.scene.Measurement
import java.util.Locale

/** The texts of the ruler card (document 16). Pure, tested. */
object MeasureText {

    /** One line of the card: a label and a value. */
    data class Line(val label: String, val value: String)

    /** "C1 – C2 – O3": the measured atoms, in the order they were tapped. */
    fun title(molecule: Molecule, atoms: List<Int>): String = when (atoms.size) {
        0 -> "Ruler"
        else -> atoms.joinToString(" – ") { molecule.atoms[it].name }
    }

    /**
     * What to show for [atoms] (0..4 indexes). Always some guidance or a result, never an empty card:
     * 0 and 1 atoms say what to do; 2 the distance; 3 the two distances and the angle; 4 the torsion and the two
     * angles it is built from (they say how reliable it is: near 180 it is undefined).
     */
    fun lines(molecule: Molecule, atoms: List<Int>): List<Line> {
        val a = atoms.map { molecule.atoms[it] }
        return when (a.size) {
            0 -> listOf(Line("Tap", "2 atoms: distance"), Line("", "3: angle · 4: torsion"))
            1 -> listOf(Line("Tap", "another atom"))
            2 -> listOf(Line("Distance", distance(Measurement.distance(a[0], a[1]))))
            3 -> listOf(
                Line("${a[0].name}–${a[1].name}", distance(Measurement.distance(a[0], a[1]))),
                Line("${a[1].name}–${a[2].name}", distance(Measurement.distance(a[1], a[2]))),
                Line("Angle", degrees(Measurement.angle(a[0], a[1], a[2]))),
            )
            else -> listOf(
                Line("Torsion", degrees(Measurement.torsion(a[0], a[1], a[2], a[3]))),
                Line("Angle at ${a[1].name}", degrees(Measurement.angle(a[0], a[1], a[2]))),
                Line("Angle at ${a[2].name}", degrees(Measurement.angle(a[1], a[2], a[3]))),
            )
        }
    }

    /** "2.345 Å": the same format as the coordinates. */
    fun distance(value: Double): String = AtomText.value(value.toFloat())

    /** "109.47°", or "undefined" (atoms in a line, or at the same place). Locale.US: a dot, a plain minus. */
    fun degrees(value: Double?): String = if (value == null) "undefined" else String.format(Locale.US, "%.2f°", value)
}
