package dev.knowledge82.swiftyproteins.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrtTransitionTest {

    private val w = 1080f
    private val h = 2400f
    private val line = 6f

    @Test
    fun atRestThePictureIsWholeAndUntouched() {
        val f = CrtTransition.frame(0f, w, h, line)
        assertEquals(w / 2, f.halfWidth, 0.001f)
        assertEquals(h / 2, f.halfHeight, 0.001f)
        assertEquals(0f, f.glow, 0f)
        assertEquals(0f, f.scan, 0f)
        assertEquals(0f, f.dot, 0f)
    }

    @Test
    fun theTubeSqueezesIntoALineThenShrinksIntoADot() {
        // At the end of the squeeze: a glowing line across the whole width, no dot yet.
        val lineFrame = CrtTransition.frame(CrtTransition.SQUEEZE, w, h, line)
        assertEquals(line / 2, lineFrame.halfHeight, 0.001f)
        assertEquals(w / 2, lineFrame.halfWidth, 0.001f)
        assertEquals(1f, lineFrame.glow, 0.001f)
        assertEquals(0f, lineFrame.dot, 0f)
        // Fully off: no width left, only the dot.
        val off = CrtTransition.frame(1f, w, h, line)
        assertEquals(0f, off.halfWidth, 0.001f)
        assertEquals(line / 2, off.halfHeight, 0.001f)
        assertEquals(1f, off.dot, 0.001f)
    }

    @Test
    fun theWindowOnlyEverShrinksAsTheCoverGrows() {
        var last = CrtTransition.frame(0f, w, h, line)
        for (i in 1..100) {
            val f = CrtTransition.frame(i / 100f, w, h, line)
            assertTrue("height at $i", f.halfHeight <= last.halfHeight)
            assertTrue("width at $i", f.halfWidth <= last.halfWidth)
            assertTrue("never under the line at $i", f.halfHeight >= line / 2)
            for (v in listOf(f.glow, f.scan, f.dot)) assertTrue("0..1 at $i", v in 0f..1f)
            last = f
        }
    }

    @Test
    fun aCoverOutsideTheRangeIsItsNearestEnd() {
        assertEquals(CrtTransition.frame(0f, w, h, line), CrtTransition.frame(-0.5f, w, h, line))
        assertEquals(CrtTransition.frame(1f, w, h, line), CrtTransition.frame(3f, w, h, line))
    }

    @Test
    fun switchingOnTakesItsTimeAndNeverGoesBack() {
        assertEquals(1f, CrtTransition.opening(1f, 0f), 0f)
        assertEquals(0.5f, CrtTransition.opening(1f, CrtTransition.ON_MILLIS / 2), 0.001f)
        assertEquals(0f, CrtTransition.opening(1f, CrtTransition.ON_MILLIS), 0.001f)
        assertEquals(0f, CrtTransition.opening(1f, 10_000f), 0f)
        // From half way it needs only half the time; a clock that went backwards does not move it.
        assertEquals(0.25f, CrtTransition.opening(0.5f, CrtTransition.ON_MILLIS / 4), 0.001f)
        assertEquals(0.5f, CrtTransition.opening(0.5f, -100f), 0f)
        // Short enough not to delay the boot screen much (its sequence starts after it, document 26).
        assertTrue(CrtTransition.ON_MILLIS < 600f)
    }
}
