package dev.knowledge82.swiftyproteins.scene

import dev.knowledge82.swiftyproteins.molecule.Atom
import dev.knowledge82.swiftyproteins.molecule.Molecule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

class MeasurementTest {

    private fun at(x: Double, y: Double, z: Double) = Atom("A", "C", x.toFloat(), y.toFloat(), z.toFloat())

    // ---------------------------------------------------------------- the list of tapped atoms

    @Test
    fun tapsAddUpToFourThenStartAgain() {
        var l = emptyList<Int>()
        for (i in listOf(5, 7, 9, 11)) l = Measurement.tap(l, i)
        assertEquals(listOf(5, 7, 9, 11), l)
        assertEquals(listOf(3), Measurement.tap(l, 3))
    }

    @Test
    fun tappingAMeasuredAtomAgainRemovesIt() {
        assertEquals(listOf(5, 9), Measurement.tap(listOf(5, 7, 9), 7))
        // Even when the list is full: undoing wins over starting again.
        assertEquals(listOf(5, 7, 9), Measurement.tap(listOf(5, 7, 9, 11), 11))
    }

    @Test
    fun hiddenHydrogensLeaveTheMeasurement() {
        val m = Molecule("T", listOf(at(0.0, 0.0, 0.0), Atom("H1", "H", 1f, 0f, 0f), at(0.0, 1.0, 0.0)), emptyList())
        assertEquals(listOf(0, 2), Measurement.visible(listOf(0, 1, 2), m, showHydrogens = false))
        assertEquals(listOf(0, 1, 2), Measurement.visible(listOf(0, 1, 2), m, showHydrogens = true))
    }

    // ---------------------------------------------------------------- distance and angle

    @Test
    fun theDistanceIsEuclidean() {
        assertEquals(13.0, Measurement.distance(at(1.0, 1.0, 1.0), at(4.0, 5.0, 13.0)), 1e-5)
    }

    @Test
    fun anglesOfKnownShapes() {
        val o = at(0.0, 0.0, 0.0)
        assertEquals(90.0, Measurement.angle(at(1.0, 0.0, 0.0), o, at(0.0, 2.0, 0.0))!!, 1e-6)
        assertEquals(180.0, Measurement.angle(at(1.0, 0.0, 0.0), o, at(-3.0, 0.0, 0.0))!!, 1e-6)
        assertEquals(0.0, Measurement.angle(at(1.0, 0.0, 0.0), o, at(5.0, 0.0, 0.0))!!, 1e-3)
        assertEquals(60.0, Measurement.angle(at(1.0, 0.0, 0.0), o, at(0.5, Math.sqrt(3.0) / 2, 0.0))!!, 1e-4)
    }

    @Test
    fun theTetrahedralAngleOfMethane() {
        // Carbon at the centre of a cube, hydrogens on alternate corners: H-C-H = acos(-1/3) = 109.47 degrees.
        val c = at(0.0, 0.0, 0.0)
        val expected = Math.toDegrees(acos(-1.0 / 3.0))
        assertEquals(109.47, expected, 0.01)
        assertEquals(expected, Measurement.angle(at(1.0, 1.0, 1.0), c, at(-1.0, -1.0, 1.0))!!, 1e-4)
    }

    @Test
    fun theAngleIsAtTheMiddleAtom() {
        // Same three atoms, another one in the middle: another angle.
        val a = at(0.0, 0.0, 0.0); val b = at(1.0, 0.0, 0.0); val c = at(1.0, 1.0, 0.0)
        assertEquals(90.0, Measurement.angle(a, b, c)!!, 1e-6)
        assertEquals(45.0, Measurement.angle(b, a, c)!!, 1e-4)
    }

    @Test
    fun anAngleWithAnAtomOnTopOfTheMiddleOneIsUndefined() {
        assertNull(Measurement.angle(at(0.0, 0.0, 0.0), at(0.0, 0.0, 0.0), at(1.0, 0.0, 0.0)))
    }

    // ---------------------------------------------------------------- torsion

    // B at the origin, C on +z; A on +x. D turns around the B-C axis by phi: the torsion must be phi.
    private fun torsionFor(phiDegrees: Double): Double? {
        val phi = Math.toRadians(phiDegrees)
        return Measurement.torsion(at(1.0, 0.0, 0.0), at(0.0, 0.0, 0.0), at(0.0, 0.0, 1.5), at(cos(phi), sin(phi), 1.5))
    }

    @Test
    fun cisIsZeroTransIs180() {
        assertEquals(0.0, torsionFor(0.0)!!, 1e-6)
        assertEquals(180.0, Math.abs(torsionFor(180.0)!!), 1e-6)
    }

    @Test
    fun theSignFollowsIupacClockwiseIsPositive() {
        // Looking along B -> C (+z), +x appears on the LEFT and +y UP: turning the front bond (+x) to +y is clockwise.
        assertEquals(90.0, torsionFor(90.0)!!, 1e-6)
        assertEquals(-90.0, torsionFor(-90.0)!!, 1e-6)
        assertEquals(60.0, torsionFor(60.0)!!, 1e-6)
        assertEquals(-120.0, torsionFor(240.0)!!, 1e-6)   // always reported in -180..180
    }

    @Test
    fun theTorsionDoesNotDependOnBondLengths() {
        val phi = Math.toRadians(73.0)
        val t = Measurement.torsion(at(7.0, 0.0, 0.0), at(0.0, 0.0, 0.0), at(0.0, 0.0, 0.3), at(4 * cos(phi), 4 * sin(phi), 9.0))
        assertEquals(73.0, t!!, 1e-6)
    }

    @Test
    fun threeAtomsInALineMakeTheTorsionUndefined() {
        assertNull(Measurement.torsion(at(0.0, 0.0, -1.0), at(0.0, 0.0, 0.0), at(0.0, 0.0, 1.0), at(1.0, 0.0, 1.0)))
        assertNull(Measurement.torsion(at(1.0, 0.0, 0.0), at(0.0, 0.0, 0.0), at(0.0, 0.0, 1.0), at(0.0, 0.0, 2.0)))
    }
}
