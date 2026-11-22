package dev.knowledge82.swiftyproteins.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlintTest {

    @Test
    fun theBandIsOutsideTheTextAtBothEnds() {
        // At the start the whole band is left of the first letter, at the end right of the last: no light "pops" on or off.
        assertTrue(Glint.center(0f) + Glint.HALF_BAND <= 0f)
        assertTrue(Glint.center(1f) - Glint.HALF_BAND >= 1f)
        // A progress past either end (a late frame) stays at that end: the band never comes back over the letters.
        assertEquals(Glint.center(0f), Glint.center(-0.3f), 0f)
        assertEquals(Glint.center(1f), Glint.center(1.4f), 0f)
    }

    @Test
    fun theBandOnlyMovesRightAndEasesAtTheEdges() {
        var last = Glint.center(0f)
        for (i in 1..100) {
            val c = Glint.center(i / 100f)
            assertTrue("at $i", c >= last)
            last = c
        }
        // Slow at the edges, fast in the middle: the first tenth covers less ground than the tenth in the middle.
        val edge = Glint.center(0.1f) - Glint.center(0f)
        val middle = Glint.center(0.55f) - Glint.center(0.45f)
        assertTrue(edge < middle)
        assertEquals((Glint.FROM + Glint.TO) / 2, Glint.center(0.5f), 0.0001f)
    }

    @Test
    fun aPassTakesItsTimeAndThenRests() {
        assertEquals(0f, Glint.progress(0f), 0f)
        assertEquals(0.5f, Glint.progress(Glint.SWEEP_MILLIS / 2f), 0.001f)
        assertEquals(1f, Glint.progress(Glint.SWEEP_MILLIS.toFloat()), 0f)
        assertEquals(1f, Glint.progress(60_000f), 0f)
        assertEquals(0f, Glint.progress(-5f), 0f)
        // Most of the cycle is rest (a glint, not a strobe), and the first pass waits for the screen to switch on.
        assertTrue(Glint.SWEEP_MILLIS * 4 < Glint.EVERY_MILLIS)
        assertTrue(Glint.FIRST_DELAY_MILLIS > CrtTransition.ON_MILLIS.toLong())
        assertTrue(Glint.FIRST_DELAY_MILLIS > ChannelChange.MILLIS.toLong())
    }
}
