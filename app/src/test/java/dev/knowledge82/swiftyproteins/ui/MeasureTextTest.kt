package dev.knowledge82.swiftyproteins.ui

import dev.knowledge82.swiftyproteins.molecule.Atom
import dev.knowledge82.swiftyproteins.molecule.Molecule
import org.junit.Assert.assertEquals
import org.junit.Test

class MeasureTextTest {

    // A right angle at C2; C4 up and beyond C3: a 90 degree torsion, and 135 degrees at C3 (chosen so that the angle
    // at C3 differs from any wrong triple of atoms, which a first version with C4 level with C3 could not tell apart).
    private val m = Molecule(
        "T",
        listOf(Atom("C1", "C", 1f, 0f, 0f), Atom("C2", "C", 0f, 0f, 0f), Atom("C3", "C", 0f, 0f, 1.5f), Atom("C4", "C", 0f, 1f, 2.5f)),
        emptyList(),
    )

    @Test
    fun withNothingOrOneAtomTheCardSaysWhatToDo() {
        assertEquals("Ruler", MeasureText.title(m, emptyList()))
        assertEquals(MeasureText.Line("Tap", "2 atoms: distance"), MeasureText.lines(m, emptyList())[0])
        assertEquals(listOf(MeasureText.Line("Tap", "another atom")), MeasureText.lines(m, listOf(0)))
    }

    @Test
    fun twoAtomsGiveTheDistance() {
        assertEquals("C1 – C2", MeasureText.title(m, listOf(0, 1)))
        assertEquals(listOf(MeasureText.Line("Distance", "1.000 Å")), MeasureText.lines(m, listOf(0, 1)))
    }

    @Test
    fun threeAtomsGiveBothDistancesAndTheAngle() {
        assertEquals(
            listOf(MeasureText.Line("C1–C2", "1.000 Å"), MeasureText.Line("C2–C3", "1.500 Å"), MeasureText.Line("Angle", "90.00°")),
            MeasureText.lines(m, listOf(0, 1, 2)),
        )
    }

    @Test
    fun fourAtomsGiveTheTorsionAndItsTwoAngles() {
        assertEquals(
            listOf(MeasureText.Line("Torsion", "90.00°"), MeasureText.Line("Angle at C2", "90.00°"), MeasureText.Line("Angle at C3", "135.00°")),
            MeasureText.lines(m, listOf(0, 1, 2, 3)),
        )
    }

    @Test
    fun anUndefinedAngleIsSaidInWordsAndNegativesUseAPlainMinus() {
        assertEquals("undefined", MeasureText.degrees(null))
        assertEquals("-60.00°", MeasureText.degrees(-60.0))
    }
}
