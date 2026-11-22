package dev.knowledge82.swiftyproteins.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelChangeTest {

    @Test
    fun theStaticRisesHidesTheSwapAndClears() {
        // Nothing at the start, nothing at the end: no static is left over either screen.
        assertEquals(0f, ChannelChange.frame(0f).snow, 0f)
        assertEquals(0f, ChannelChange.frame(1f).snow, 0.0001f)
        // At the swap the static is COMPLETE: neither screen shows while they change places.
        assertEquals(1f, ChannelChange.frame(ChannelChange.SWAP_AT).snow, 0.0001f)
        assertFalse(ChannelChange.swapped(ChannelChange.SWAP_AT - 0.01f))
        assertTrue(ChannelChange.swapped(ChannelChange.SWAP_AT))
    }

    @Test
    fun upToTheSwapThenOnlyDown() {
        var last = -1f
        var steps = 0
        while (steps <= 1000) {
            val p = steps / 1000f
            val f = ChannelChange.frame(p)
            if (p < ChannelChange.SWAP_AT) assertTrue("rises at $p", f.snow >= last) else if (p > ChannelChange.SWAP_AT) assertTrue("clears at $p", f.snow <= last)
            for (v in listOf(f.snow, f.scan)) assertTrue("0..1 at $p", v in 0f..1f)
            last = f.snow
            steps++
        }
    }

    @Test
    fun theRollingBarOnlyComesWithTheNewPicture() {
        assertNull(ChannelChange.frame(0.1f).roll)
        assertEquals(0f, ChannelChange.frame(ChannelChange.SWAP_AT).roll!!, 0.0001f)
        assertEquals(1f, ChannelChange.frame(1f).roll!!, 0.0001f)
        assertTrue(ChannelChange.frame(0.7f).roll!! > ChannelChange.frame(0.5f).roll!!)
    }

    @Test
    fun itIsShort() {
        assertEquals(0f, ChannelChange.progress(0f, 0f), 0f)
        assertEquals(0.5f, ChannelChange.progress(0f, ChannelChange.MILLIS / 2), 0.001f)
        assertEquals(1f, ChannelChange.progress(0f, 60_000f), 0f)
        assertEquals(0.3f, ChannelChange.progress(0.3f, -50f), 0f)   // a clock going backwards moves nothing
        // "A flick of the dial": well under half a second, and the swap comes early (the old screen goes fast).
        assertTrue(ChannelChange.MILLIS < 500f)
        assertTrue(ChannelChange.SWAP_AT < 0.5f)
    }

    @Test
    fun aChangeDuringAnotherStartsFromTheStaticOnScreen() {
        // Half covered while clearing: a new change starts on the rising side with the same static, never lower.
        val clearing = ChannelChange.frame(0.7f).snow
        val restart = ChannelChange.restartAt(clearing)
        assertTrue(restart < ChannelChange.SWAP_AT)
        assertEquals(clearing, ChannelChange.frame(restart).snow, 0.0001f)
        assertEquals(0f, ChannelChange.restartAt(0f), 0f)
        assertEquals(ChannelChange.SWAP_AT, ChannelChange.restartAt(1f), 0f)
    }
}
