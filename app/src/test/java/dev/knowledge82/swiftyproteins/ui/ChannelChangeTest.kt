package dev.knowledge82.swiftyproteins.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelChangeTest {

    private val n = 240   // about a phone screen in 4 dp bands

    private fun blackCount(p: Float) = (0 until n).count { ChannelChange.band(it, n, p).black }

    @Test
    fun everyBandIsBlackAtTheSwapAndNoneAtEitherEnd() {
        assertEquals(0, blackCount(0f))
        assertEquals(n, blackCount(ChannelChange.SWAP_AT))
        assertEquals(0, blackCount(1f))
        // The end is clean: no blink left over the new picture.
        assertTrue((0 until n).all { ChannelChange.band(it, n, 1f).blink == 0f })
        assertFalse(ChannelChange.swapped(ChannelChange.SWAP_AT - 0.01f))
        assertTrue(ChannelChange.swapped(ChannelChange.SWAP_AT))
    }

    @Test
    fun theOddBandsGoFirstThenTheEvenOnesTopToBottom() {
        // Just before half way through going out: every odd band is out, no even band yet (the interlace).
        val half = ChannelChange.SWAP_AT * 0.5f - 0.001f
        for (k in 0 until n) assertEquals("band $k", k % 2 == 1, ChannelChange.band(k, n, half).black)
        // Within a pass, a band never changes before the one above it.
        for (k in 3 until n) assertTrue(ChannelChange.bandTime(k, n) >= ChannelChange.bandTime(k - 2, n))
        // Every band changes inside its half (always before its end).
        for (k in 0 until n) assertTrue(ChannelChange.bandTime(k, n) in 0f..0.999999f)
    }

    @Test
    fun goingOutOnlyAddsBlackAndComingBackOnlyRemovesIt() {
        var last = -1
        for (step in 0..1000) {
            val p = step / 1000f
            val c = blackCount(p)
            if (p <= ChannelChange.SWAP_AT) assertTrue("out at $p", c >= last) else assertTrue("back at $p", c <= last)
            last = c
        }
    }

    @Test
    fun aBandBlinksGreenOnlyTheMomentItChanges() {
        // Band 1 is the first to go out (odd, top): at the very start it blinks, a moment later it no longer does.
        val atOnce = ChannelChange.band(1, n, 0.0001f)
        assertTrue(atOnce.black)
        assertEquals(ChannelChange.BLINK_OUT, atOnce.blink, 0f)
        assertEquals(0f, ChannelChange.band(1, n, ChannelChange.SWAP_AT * 0.2f).blink, 0f)
        // Coming back the blink is softer: over the new picture, not over the black.
        assertTrue(ChannelChange.BLINK_IN < ChannelChange.BLINK_OUT)
        // At any moment only a thin slice of the screen blinks: a line, not a flash.
        for (step in 1 until 100) {
            val p = step / 100f
            val blinking = (0 until n).count { ChannelChange.band(it, n, p).blink > 0f }
            assertTrue("$blinking bands blink at $p", blinking <= n / 10)
        }
    }

    @Test
    fun itIsShortAndTheBandsFitTheScreen() {
        assertEquals(0.5f, ChannelChange.progress(0f, ChannelChange.MILLIS / 2), 0.001f)
        assertEquals(1f, ChannelChange.progress(0f, 60_000f), 0f)
        assertEquals(0.3f, ChannelChange.progress(0.3f, -50f), 0f)
        assertTrue(ChannelChange.MILLIS in 150f..250f)
        assertEquals(240, ChannelChange.bandCount(2400f, 10f))
        assertEquals(241, ChannelChange.bandCount(2405f, 10f))   // the last band is cut, never missing
        assertEquals(1, ChannelChange.bandCount(5f, 10f))
    }

    @Test
    fun aChangeDuringAnotherGoesOutOnOrStartsOver() {
        assertEquals(0.2f, ChannelChange.restartAt(0.2f), 0f)
        assertEquals(0f, ChannelChange.restartAt(ChannelChange.SWAP_AT), 0f)
        assertEquals(0f, ChannelChange.restartAt(0.8f), 0f)
    }
}
