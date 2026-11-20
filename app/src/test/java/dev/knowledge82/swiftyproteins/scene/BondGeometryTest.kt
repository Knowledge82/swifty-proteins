package dev.knowledge82.swiftyproteins.scene

import dev.knowledge82.swiftyproteins.molecule.Atom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class BondGeometryTest {

    private fun atom(x: Float, y: Float, z: Float) = Atom("A", "C", x, y, z)

    // ---- the rotations the scene applies, written as plain formulas (right-handed axes) ----
    private fun rotateZ(v: DoubleArray, deg: Double): DoubleArray {
        val a = deg * PI / 180
        return doubleArrayOf(v[0] * cos(a) - v[1] * sin(a), v[0] * sin(a) + v[1] * cos(a), v[2])
    }

    private fun rotateY(v: DoubleArray, deg: Double): DoubleArray {
        val a = deg * PI / 180
        return doubleArrayOf(v[0] * cos(a) + v[2] * sin(a), v[1], -v[0] * sin(a) + v[2] * cos(a))
    }

    /** Where the axis of a standing cylinder ends up: first the inner rotation (tilt, Z), then the outer one (spin, Y). */
    private fun axisAfter(pose: BondPose): DoubleArray =
        rotateY(rotateZ(doubleArrayOf(0.0, 1.0, 0.0), pose.tiltDegrees.toDouble()), pose.spinDegrees.toDouble())

    private fun assertPointsFromAtoB(a: Atom, b: Atom) {
        val pose = BondGeometry.poseOf(a, b)
        assertNotNull("no pose for $a -> $b", pose)
        pose!!
        val d = doubleArrayOf((b.x - a.x).toDouble(), (b.y - a.y).toDouble(), (b.z - a.z).toDouble())
        val len = sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2])
        val axis = axisAfter(pose)
        for (i in 0..2) assertEquals("axis $i for $a -> $b", d[i] / len, axis[i], 1e-4)
    }

    @Test
    fun theThreeAxesAndTheirOppositesAreReachedExactly() {
        val o = atom(0f, 0f, 0f)
        for (b in listOf(
            atom(1f, 0f, 0f), atom(-1f, 0f, 0f), atom(0f, 1f, 0f), atom(0f, -1f, 0f), atom(0f, 0f, 1f), atom(0f, 0f, -1f),
        )) assertPointsFromAtoB(o, b)
    }

    @Test
    fun theSevenBondsOfTheSpikeSceneStillPointTheSameWay() {
        val center = atom(0f, 0f, 0f)
        for (tip in listOf(
            atom(1.5f, 0f, 0f), atom(0f, 1.5f, 0f), atom(0f, 0f, 1.5f), atom(1f, 1f, 1f),
            atom(1f, 1f, 0f), atom(-1f, -1.2f, 0.8f), atom(0f, -1.5f, 0f),
        )) assertPointsFromAtoB(center, tip)
    }

    @Test
    fun anyDirectionWorks_twoThousandRandomPairs() {
        val random = Random(42)
        fun r() = (random.nextDouble() * 20 - 10).toFloat()
        repeat(2_000) {
            val a = atom(r(), r(), r())
            val b = atom(r(), r(), r())
            if (BondGeometry.poseOf(a, b) != null) assertPointsFromAtoB(a, b)
        }
    }

    @Test
    fun theOrderOfTheTwoAtomsOnlyReversesTheAxisSoTheCylinderIsTheSame() {
        val a = atom(1f, 2f, 3f)
        val b = atom(-2f, 0.5f, 4f)
        val ab = BondGeometry.poseOf(a, b)!!
        val ba = BondGeometry.poseOf(b, a)!!
        assertEquals(ab.x, ba.x, 0f); assertEquals(ab.y, ba.y, 0f); assertEquals(ab.z, ba.z, 0f)
        assertEquals(ab.length, ba.length, 1e-6f)
        val u = axisAfter(ab); val v = axisAfter(ba)
        for (i in 0..2) assertEquals(-u[i], v[i], 1e-4)
    }

    @Test
    fun theCylinderIsCenteredOnTheMidpointAndHasTheLengthOfTheBond() {
        val pose = BondGeometry.poseOf(atom(0f, 0f, 0f), atom(3f, 4f, 0f))!!
        assertEquals(1.5f, pose.x, 1e-6f)
        assertEquals(2f, pose.y, 1e-6f)
        assertEquals(0f, pose.z, 1e-6f)
        assertEquals(5f, pose.length, 1e-5f)
    }

    @Test
    fun twoAtomsAtTheSamePlaceHaveNoBondToDraw() {
        assertNull(BondGeometry.poseOf(atom(1f, 1f, 1f), atom(1f, 1f, 1f)))
        assertNull(BondGeometry.poseOf(atom(1f, 1f, 1f), atom(1f, 1f, 1.00001f)))
    }

    @Test
    fun aVeryShortButRealBondIsStillDrawn() {
        assertNotNull(BondGeometry.poseOf(atom(0f, 0f, 0f), atom(0.5f, 0f, 0f)))
    }

    @Test
    fun aNearlyVerticalBondNeverProducesNaN() {
        // Guards the NaN case. NOTE: in Double arithmetic dy / length cannot exceed 1, so no test can tell the guard from its absence (equivalent mutant, document 07).
        val pose = BondGeometry.poseOf(atom(0f, 0f, 0f), atom(1e-4f, 123.456f, 0f))!!
        assertEquals(false, pose.tiltDegrees.isNaN())
        assertEquals(false, pose.spinDegrees.isNaN())
    }
}
