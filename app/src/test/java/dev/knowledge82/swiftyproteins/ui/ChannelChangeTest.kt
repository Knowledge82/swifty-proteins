package dev.knowledge82.swiftyproteins.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelChangeTest {

    @Test
    fun thePulseHidesTheSwapAndNothingIsLeftAtEitherEnd() {
        assertEquals(0f, ChannelChange.frame(0f).pulse, 0f)
        assertEquals(0f, ChannelChange.frame(1f).pulse, 0.0001f)
        // At the swap the pulse is COMPLETE: neither screen shows while they change places.
        assertEquals(1f, ChannelChange.frame(ChannelChange.SWAP_AT).pulse, 0.0001f)
        assertFalse(ChannelChange.swapped(ChannelChange.SWAP_AT - 0.01f))
        assertTrue(ChannelChange.swapped(ChannelChange.SWAP_AT))
    }

    @Test
    fun upToTheSwapThenOnlyDownAndFastFirst() {
        var last = -1f
        for (step in 0..1000) {
            val p = step / 1000f
            val f = ChannelChange.frame(p)
            if (p < ChannelChange.SWAP_AT) assertTrue("rises at $p", f.pulse >= last)
            else if (p > ChannelChange.SWAP_AT) assertTrue("falls at $p", f.pulse <= last)
            assertTrue("0..1 at $p", f.pulse in 0f..1f && f.scan in 0f..1f)
            last = f.pulse
        }
        // Half of the pulse is gone in the first third of the fall (a soft tail after): it does not linger.
        val fall = 1f - ChannelChange.SWAP_AT
        assertTrue(ChannelChange.frame(ChannelChange.SWAP_AT + fall / 3f).pulse < 0.5f)
        assertTrue(ChannelChange.frame(ChannelChange.SWAP_AT + fall * 0.8f).pulse > 0f)
    }

    @Test
    fun theScanlinesFollowThePulse() {
        assertEquals(0f, ChannelChange.frame(0f).scan, 0f)
        assertEquals(ChannelChange.SCAN_MAX, ChannelChange.frame(ChannelChange.SWAP_AT).scan, 0.0001f)
        assertEquals(ChannelChange.SCAN_MAX / 2f, ChannelChange.frame(ChannelChange.SWAP_AT / 2f).scan, 0.0001f)
        assertTrue(ChannelChange.SCAN_MAX < 0.5f)   // a texture, not stripes
    }

    @Test
    fun itIsShort() {
        assertEquals(0f, ChannelChange.progress(0f, 0f), 0f)
        assertEquals(0.5f, ChannelChange.progress(0f, ChannelChange.MILLIS / 2), 0.001f)
        assertEquals(1f, ChannelChange.progress(0f, 60_000f), 0f)
        assertEquals(0.3f, ChannelChange.progress(0.3f, -50f), 0f)   // a clock going backwards moves nothing
        assertTrue(ChannelChange.MILLIS <= 160f)
        assertTrue(ChannelChange.SWAP_AT < 0.5f)
    }

    @Test
    fun aChangeDuringAnotherStartsFromThePulseOnScreen() {
        val falling = ChannelChange.frame(0.6f).pulse
        val restart = ChannelChange.restartAt(falling)
        assertTrue(restart < ChannelChange.SWAP_AT)
        assertEquals(falling, ChannelChange.frame(restart).pulse, 0.0001f)
        assertEquals(0f, ChannelChange.restartAt(0f), 0f)
        assertEquals(ChannelChange.SWAP_AT, ChannelChange.restartAt(1f), 0f)
    }
}
