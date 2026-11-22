package dev.knowledge82.swiftyproteins.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelChangeTest {

    private fun frames() = (0..1000).map { it / 1000f to ChannelChange.frame(it / 1000f) }

    @Test
    fun theBlackHidesTheSwapAndNothingIsLeftAtEitherEnd() {
        val start = ChannelChange.frame(0f)
        val end = ChannelChange.frame(1f)
        for (v in listOf(start.black, start.glow, start.scan, end.black, end.glow, end.scan)) assertEquals(0f, v, 0.0001f)
        // At the swap the black is COMPLETE and there is no glow yet: neither screen shows while they change places.
        val swap = ChannelChange.frame(ChannelChange.SWAP_AT)
        assertEquals(1f, swap.black, 0.0001f)
        assertEquals(0f, swap.glow, 0.0001f)
        assertFalse(ChannelChange.swapped(ChannelChange.SWAP_AT - 0.01f))
        assertTrue(ChannelChange.swapped(ChannelChange.SWAP_AT))
    }

    @Test
    fun darkFirstThenTheAfterglowOnlyOverTheNewPicture() {
        var lastBlack = -1f
        for ((p, f) in frames()) {
            if (p < ChannelChange.SWAP_AT) {
                assertTrue("darkens at $p", f.black >= lastBlack)
                assertEquals("no glow over the OLD screen at $p", 0f, f.glow, 0f)
            } else if (p > ChannelChange.SWAP_AT) {
                assertTrue("the black only lifts at $p", f.black <= lastBlack)
            }
            assertTrue(f.black in 0f..1f && f.glow in 0f..ChannelChange.GLOW_MAX + 0.0001f && f.scan in 0f..1f)
            lastBlack = f.black
        }
    }

    @Test
    fun theAfterglowRisesToItsPeakThenFades() {
        val after = frames().filter { it.first > ChannelChange.SWAP_AT }.map { it.second.glow }
        val peak = after.max()
        assertEquals(ChannelChange.GLOW_MAX, peak, 0.01f)   // bright enough to be seen: the point of this version
        val top = after.indexOf(peak)
        assertTrue(top in 1 until after.size - 1)            // it rises, then it falls
        for (i in 1..top) assertTrue(after[i] >= after[i - 1])
        for (i in top + 1 until after.size) assertTrue(after[i] <= after[i - 1])
        // Bright enough to be SEEN (asked for after the first try on the A33), dim enough to read the new screen through.
        assertTrue(ChannelChange.GLOW_MAX >= 0.5f)
        assertTrue(ChannelChange.GLOW_MAX < 0.8f)
    }

    @Test
    fun itIsABlink() {
        assertEquals(0f, ChannelChange.progress(0f, 0f), 0f)
        assertEquals(0.5f, ChannelChange.progress(0f, ChannelChange.MILLIS / 2), 0.001f)
        assertEquals(1f, ChannelChange.progress(0f, 60_000f), 0f)
        assertEquals(0.3f, ChannelChange.progress(0.3f, -50f), 0f)   // a clock going backwards moves nothing
        assertTrue(ChannelChange.MILLIS <= 200f)
        assertTrue(ChannelChange.SWAP_AT < 0.5f)
    }

    @Test
    fun aChangeDuringAnotherStartsFromTheBlackOnScreen() {
        val lifting = ChannelChange.frame(0.6f).black
        val restart = ChannelChange.restartAt(lifting)
        assertTrue(restart < ChannelChange.SWAP_AT)
        assertEquals(lifting, ChannelChange.frame(restart).black, 0.0001f)
        assertEquals(0f, ChannelChange.restartAt(0f), 0f)
        assertEquals(ChannelChange.SWAP_AT, ChannelChange.restartAt(1f), 0f)
    }
}
