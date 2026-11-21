package dev.knowledge82.swiftyproteins.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class AtomLabelsTest {

    @Test
    fun theLabelSitsOnTheBallFacingTheCamera() {
        // Atom at (1, 2, 3), radius 0.4, camera straight along +z at distance 10.
        val (x, y, z) = AtomLabels.position(1f, 2f, 3f, 0.4f, 1f, 2f, 13f)
        assertEquals(1f, x, 1e-6f); assertEquals(2f, y, 1e-6f)
        assertEquals(3f + 0.4f + AtomLabels.GAP, z, 1e-5f)
    }

    @Test
    fun fromAnyAngleTheLabelIsJustOutsideTheSurfaceTowardTheCamera() {
        val (x, y, z) = AtomLabels.position(0f, 0f, 0f, 1.7f, 3f, -4f, 12f)   // camera at distance 13
        assertEquals(1.7f + AtomLabels.GAP, sqrt(x * x + y * y + z * z), 1e-5f)
        // Same direction as the camera: the dot product of unit vectors is 1.
        val d = sqrt(x * x + y * y + z * z)
        assertEquals(1f, (x * 3f + y * -4f + z * 12f) / (d * 13f), 1e-5f)
    }

    @Test
    fun aCameraInsideTheAtomLeavesTheLabelAtTheCentre() {
        assertEquals(Triple(1f, 1f, 1f), AtomLabels.position(1f, 1f, 1f, 0.4f, 1f, 1f, 1f))
    }

    @Test
    fun theSizeFollowsTheBallWithinLimits() {
        assertEquals(0.4f * 1.4f, AtomLabels.height(0.4f), 1e-6f)                     // ball and stick
        assertEquals(AtomLabels.MIN_HEIGHT, AtomLabels.height(0.05f), 0f)             // wire joint: still readable
        assertEquals(AtomLabels.MAX_HEIGHT, AtomLabels.height(1.7f), 0f)              // space-filling: not huge
        assertEquals(AtomLabels.height(0.4f) * 1.5f, AtomLabels.width(0.4f), 1e-6f)   // 3:2, like the picture
    }

    @Test
    fun aLabelInPlaceIsNotMovedSoTheSceneCanRest() {
        assertFalse(AtomLabels.needsMove(1f, 2f, 3f, 1f, 2f, 3f))
        assertFalse(AtomLabels.needsMove(1f, 2f, 3f, 1.0005f, 2f, 3f))   // below the threshold
        assertTrue(AtomLabels.needsMove(1f, 2f, 3f, 1f, 2.01f, 3f))
    }
}
