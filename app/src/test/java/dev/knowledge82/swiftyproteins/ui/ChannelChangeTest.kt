package dev.knowledge82.swiftyproteins.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelChangeTest {

    private fun covered(f: ChannelChange.Frame) = f.blackTo - f.blackFrom

    @Test
    fun theScreenIsAllBlackAtTheSwapAndClearAtBothEnds() {
        assertEquals(0f, covered(ChannelChange.frame(0f)), 0f)
        assertEquals(0f, covered(ChannelChange.frame(1f)), 0.0001f)
        // At the swap the black covers everything: neither screen shows while they change places.
        val swap = ChannelChange.frame(ChannelChange.SWAP_AT)
        assertEquals(0f, swap.blackFrom, 0f)
        assertEquals(1f, swap.blackTo, 0f)
        assertFalse(ChannelChange.swapped(ChannelChange.SWAP_AT - 0.01f))
        assertTrue(ChannelChange.swapped(ChannelChange.SWAP_AT))
    }

    @Test
    fun theLineWipesDownThenDrawsDownAndIsAlwaysAtTheEdgeOfTheBlack() {
        var lastLine = -1f
        for (step in 0..1000) {
            val p = step / 1000f
            val f = ChannelChange.frame(p)
            if (p < ChannelChange.SWAP_AT) {
                assertEquals("wiping: black from the top at $p", 0f, f.blackFrom, 0f)
                assertEquals("the line leads the black at $p", f.blackTo, f.line, 0f)
            } else {
                assertEquals("drawing: black down to the bottom at $p", 1f, f.blackTo, 0f)
                assertEquals("the line leads the picture at $p", f.blackFrom, f.line, 0f)
            }
            // Within each sweep the line only moves DOWN (it jumps back to the top once, at the swap).
            if (p != ChannelChange.SWAP_AT && step > 0 && (p < ChannelChange.SWAP_AT) == ((step - 1) / 1000f < ChannelChange.SWAP_AT)) {
                assertTrue("down at $p", f.line >= lastLine)
            }
            assertTrue(f.line in 0f..1f)
            lastLine = f.line
        }
    }

    @Test
    fun theLineMovesAtAConstantSpeed() {
        // A tube's beam scans evenly: equal times, equal distances (in each sweep).
        val a = ChannelChange.frame(ChannelChange.SWAP_AT * 0.25f).line
        val b = ChannelChange.frame(ChannelChange.SWAP_AT * 0.5f).line
        assertEquals(0.25f, a, 0.0001f)
        assertEquals(0.5f, b, 0.0001f)
        val mid = ChannelChange.SWAP_AT + (1f - ChannelChange.SWAP_AT) / 2f
        assertEquals(0.5f, ChannelChange.frame(mid).line, 0.0001f)
    }

    @Test
    fun itIsQuickButCanBeFollowed() {
        assertEquals(0f, ChannelChange.progress(0f, 0f), 0f)
        assertEquals(0.5f, ChannelChange.progress(0f, ChannelChange.MILLIS / 2), 0.001f)
        assertEquals(1f, ChannelChange.progress(0f, 60_000f), 0f)
        assertEquals(0.3f, ChannelChange.progress(0.3f, -50f), 0f)   // a clock going backwards moves nothing
        // The speed of the proposal, chosen on the A33: 150 ms was too fast for the eye to follow the line; much
        // slower and it becomes a curtain that makes every Back wait. Each sweep about a tenth of a second.
        assertTrue(ChannelChange.MILLIS in 200f..250f)
        assertTrue(ChannelChange.MILLIS * ChannelChange.SWAP_AT in 80f..130f)
        assertTrue(ChannelChange.MILLIS * (1f - ChannelChange.SWAP_AT) in 80f..130f)
    }

    @Test
    fun aChangeDuringAnotherWipesOnOrStartsANewWipe() {
        // Still wiping: it simply goes on, the black already hides that much of the leaving screen.
        assertEquals(0.2f, ChannelChange.restartAt(0.2f), 0f)
        // Already drawing the new screen: a new wipe from the top.
        assertEquals(0f, ChannelChange.restartAt(ChannelChange.SWAP_AT), 0f)
        assertEquals(0f, ChannelChange.restartAt(0.8f), 0f)
    }
}
