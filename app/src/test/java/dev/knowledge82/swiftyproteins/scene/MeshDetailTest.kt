package dev.knowledge82.swiftyproteins.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshDetailTest {

    @Test
    fun theBiggestLigandGetsExactlyWhatRanAt60Fps() {
        // CDL, 256 atoms: the fixed values used before the rule (20 / 24 / 12 bands, 24-sided sticks at most).
        assertEquals(20, MeshDetail.ball(256))
        assertEquals(24, MeshDetail.bigBall(256))
        assertEquals(12, MeshDetail.smallBall(256))
        assertTrue(MeshDetail.cylinderSides(256) <= 24)
    }

    @Test
    fun smallerMoleculesGetMoreDetailNeverLess() {
        assertEquals(32, MeshDetail.ball(47))     // ATP
        assertEquals(32, MeshDetail.ball(1))      // a lone ion
        for (n in 1..256) assertTrue("n=$n", MeshDetail.ball(n) >= 20)
    }

    @Test
    fun detailNeverGrowsWithSize() {
        for (n in 1 until 2_000) {
            assertTrue("ball n=$n", MeshDetail.ball(n + 1) <= MeshDetail.ball(n))
            assertTrue("sides n=$n", MeshDetail.cylinderSides(n + 1) <= MeshDetail.cylinderSides(n))
        }
    }

    @Test
    fun theBudgetHoldsUpToTheCatalogueLimit() {
        for (n in listOf(64, 100, 128, 200, 256)) {
            val triangles = n * MeshDetail.ballTriangles(MeshDetail.ball(n))
            assertTrue("n=$n: $triangles", triangles <= MeshDetail.TRIANGLE_BUDGET)
        }
    }

    @Test
    fun sticksNeverGetMoreSidesThanSceneViewsDefault() {
        for (n in 0..2_000) assertTrue("n=$n", MeshDetail.cylinderSides(n) in 8..24)
    }

    @Test
    fun hugeOrEmptyInputsStayInRange() {
        assertEquals(MeshDetail.MIN, MeshDetail.ball(100_000))
        assertEquals(MeshDetail.MAX, MeshDetail.ball(0))
        assertEquals(8, MeshDetail.smallBall(100_000))
        assertEquals(9, MeshDetail.cylinderSides(100_000))   // 3/4 of the minimum ball
        assertEquals(MeshDetail.MAX, MeshDetail.bigBall(1))
    }

    @Test
    fun meshSizesMatchHowSceneViewBuildsASphere() {
        // stacks x slices quads, minus one triangle per slice at each pole; (stacks + 1) x (slices + 1) vertices.
        assertEquals(2 * 20 * 19, MeshDetail.ballTriangles(20))
        assertEquals(441, MeshDetail.ballVertices(20))
    }
}
