package dev.knowledge82.swiftyproteins.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssemblyTest {

    @Test
    fun atomsComeFirstThenBonds() {
        // 3 atoms, 2 bonds: pieces 0..2 are the atoms, 3..4 the bonds.
        assertTrue(Assembly.hasAtom(2, built = 3))
        assertFalse(Assembly.hasBond(0, atoms = 3, built = 3))
        assertTrue(Assembly.hasBond(0, atoms = 3, built = 4))
        assertFalse(Assembly.hasBond(1, atoms = 3, built = 4))
        assertTrue(Assembly.hasBond(1, atoms = 3, built = 5))
        assertFalse(Assembly.hasAtom(0, built = 0))
    }

    @Test
    fun theBatchDoublesWhileFramesAreFastAndHalvesWhenOneIsSlow() {
        assertEquals(32, Assembly.nextBatch(16, 16.7f))
        assertEquals(8, Assembly.nextBatch(16, 40f))
        assertEquals(16, Assembly.nextBatch(16, 20f))   // in between: keep it
    }

    @Test
    fun theBatchStaysWithinItsLimits() {
        assertEquals(Assembly.MAX_BATCH, Assembly.nextBatch(Assembly.MAX_BATCH, 1f))
        assertEquals(Assembly.MIN_BATCH, Assembly.nextBatch(Assembly.MIN_BATCH, 500f))
    }

    @Test
    fun advancingNeverPassesTheEnd() {
        assertEquals(20, Assembly.advance(10, 16, 20))
        assertEquals(26, Assembly.advance(10, 16, 100))
    }

    @Test
    fun theBiggestLigandIsCompleteInAFewFastFrames() {
        // CDL: 256 atoms + 255 bonds. On a phone where every frame stays fast.
        val total = Assembly.total(256, 255)
        var built = 0
        var batch = Assembly.FIRST_BATCH
        var frames = 0
        while (built < total) { built = Assembly.advance(built, batch, total); batch = Assembly.nextBatch(batch, 16.7f); frames++ }
        assertEquals(total, built)
        assertTrue("frames=$frames", frames <= 7)
    }

    @Test
    fun theLabelGoesAwayWhenComplete() {
        assertEquals("ASSEMBLY 312 / 511", Assembly.label(312, 511))
        assertEquals("ASSEMBLY 0 / 511", Assembly.label(0, 511))
        assertNull(Assembly.label(511, 511))
        assertNull(Assembly.label(0, 0))   // a molecule with nothing to build shows no bar
    }
}
