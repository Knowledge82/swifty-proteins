package dev.knowledge82.swiftyproteins.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelChangeTest {

    @Test
    fun theLightRisesHidesTheSwapAndFallsAway() {
        // Nothing at the start, nothing at the end: no light is left over either screen.
        assertEquals(0f, ChannelChange.frame(0f).flash, 0f)
        assertEquals(0f, ChannelChange.frame(1f).flash, 0.0001f)
        // At the swap the light is COMPLETE: neither screen shows while they change places.
        assertEquals(1f, ChannelChange.frame(ChannelChange.SWAP_AT).flash, 0.0001f)
        assertFalse(ChannelChange.swapped(ChannelChange.SWAP_AT - 0.01f))
        assertTrue(ChannelChange.swapped(ChannelChange.SWAP_AT))
    }

    @Test
    fun upToTheSwapThenOnlyDownAndFastFirst() {
        var last = -1f
        for (step in 0..1000) {
            val p = step / 1000f
            val f = ChannelChange.frame(p)
            if (p < ChannelChange.SWAP_AT) assertTrue("rises at $p", f.flash >= last)
            else if (p > ChannelChange.SWAP_AT) assertTrue("falls at $p", f.flash <= last)
            for (v in listOf(f.flash, f.scan)) assertTrue("0..1 at $p", v in 0f..1f)
            last = f.flash
        }
        // A flash: half of the light is gone in the first third of the fall (a soft tail after).
        val fall = 1f - ChannelChange.SWAP_AT
        assertTrue(ChannelChange.frame(ChannelChange.SWAP_AT + fall / 3f).flash < 0.5f)
        assertTrue(ChannelChange.frame(ChannelChange.SWAP_AT + fall * 0.8f).flash > 0f)
    }

    @Test
    fun theScanlinesFollowTheLight() {
        assertEquals(0f, ChannelChange.frame(0f).scan, 0f)
        assertEquals(ChannelChange.SCAN_MAX, ChannelChange.frame(ChannelChange.SWAP_AT).scan, 0.0001f)
        assertTrue(ChannelChange.SCAN_MAX < 0.5f)   // faint: the flash must stay bright
    }

    @Test
    fun itIsABlink() {
        assertEquals(0f, ChannelChange.progress(0f, 0f), 0f)
        assertEquals(0.5f, ChannelChange.progress(0f, ChannelChange.MILLIS / 2), 0.001f)
        assertEquals(1f, ChannelChange.progress(0f, 60_000f), 0f)
        assertEquals(0.3f, ChannelChange.progress(0.3f, -50f), 0f)   // a clock going backwards moves nothing
        // Half of the first version (320 ms); the swap early (the old screen goes at once).
        assertTrue(ChannelChange.MILLIS <= 160f)
        assertTrue(ChannelChange.SWAP_AT < 0.5f)
    }

    @Test
    fun aChangeDuringAnotherStartsFromTheLightOnScreen() {
        val falling = ChannelChange.frame(0.6f).flash
        val restart = ChannelChange.restartAt(falling)
        assertTrue(restart < ChannelChange.SWAP_AT)
        assertEquals(falling, ChannelChange.frame(restart).flash, 0.0001f)
        assertEquals(0f, ChannelChange.restartAt(0f), 0f)
        assertEquals(ChannelChange.SWAP_AT, ChannelChange.restartAt(1f), 0f)
    }
}
