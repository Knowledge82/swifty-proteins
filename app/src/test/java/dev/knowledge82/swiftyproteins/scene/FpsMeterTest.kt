package dev.knowledge82.swiftyproteins.scene

import org.junit.Assert.assertEquals
import org.junit.Test

class FpsMeterTest {

    private val ms = 1_000_000L

    /** Frames every [stepMs] ms from [startMs], [n] of them. Returns the time of the last one. */
    private fun FpsMeter.frames(startMs: Long, stepMs: Double, n: Int): Long {
        var last = 0L
        for (k in 0 until n) {
            last = ((startMs + k * stepMs) * ms).toLong()
            onFrame(last)
        }
        return last
    }

    @Test
    fun nothingDrawnIsIdle() {
        assertEquals(FpsReading.Idle, FpsMeter().reading(5_000 * ms))
    }

    @Test
    fun aSteady60HzSceneReads60() {
        val m = FpsMeter()
        val last = m.frames(1_000, 1000.0 / 60, 120)
        assertEquals(FpsReading.Running(60, 17), m.reading(last + 5 * ms))
    }

    @Test
    fun a120HzPanelReads120() {
        val m = FpsMeter()
        val last = m.frames(1_000, 1000.0 / 120, 240)
        assertEquals(120, (m.reading(last) as FpsReading.Running).fps)
    }

    @Test
    fun aStutterShowsInTheWorstFrame() {
        val m = FpsMeter()
        var last = m.frames(1_000, 1000.0 / 60, 30)
        last += 100 * ms            // one frame took 100 ms
        m.onFrame(last)
        last = m.frames(last / ms + 16, 1000.0 / 60, 20)
        val r = m.reading(last) as FpsReading.Running
        assertEquals(100, r.worstFrameMillis)
    }

    @Test
    fun onlyTheLastSecondCounts() {
        val m = FpsMeter()
        m.frames(0, 1000.0 / 120, 120)                 // one second at 120
        val last = m.frames(1_100, 1000.0 / 30, 40)    // then a slow scene at 30
        assertEquals(30, (m.reading(last) as FpsReading.Running).fps)
    }

    @Test
    fun aSceneThatStoppedDrawingIsIdleAfterHalfASecond() {
        val m = FpsMeter()
        val last = m.frames(1_000, 1000.0 / 60, 60)
        assertEquals(60, (m.reading(last + 499 * ms) as FpsReading.Running).fps)
        assertEquals(FpsReading.Idle, m.reading(last + 501 * ms))
    }

    @Test
    fun aSingleFrameIsNotARate() {
        val m = FpsMeter()
        m.onFrame(1_000 * ms)
        assertEquals(FpsReading.Idle, m.reading(1_010 * ms))
    }

    @Test
    fun theRingKeepsWorkingAfterWrappingAround() {
        val m = FpsMeter(capacity = 16)
        val last = m.frames(0, 1000.0 / 60, 1000)    // wraps the 16-slot ring many times
        // Only 16 frames fit, spanning 250 ms: still 60 per second.
        assertEquals(60, (m.reading(last) as FpsReading.Running).fps)
    }

    @Test
    fun aTimestampFromThePastIsIgnored() {
        val m = FpsMeter()
        val last = m.frames(1_000, 1000.0 / 60, 60)
        m.onFrame(last - 500 * ms)
        assertEquals(FpsReading.Running(60, 17), m.reading(last))
    }

    @Test
    fun theLabelAndTheLevel() {
        assertEquals("IDLE", FpsText.label(FpsReading.Idle))
        assertEquals("60 FPS", FpsText.label(FpsReading.Running(60, 17)))
        assertEquals(FpsText.Level.GOOD, FpsText.level(FpsReading.Running(55, 20)))
        assertEquals(FpsText.Level.FAIR, FpsText.level(FpsReading.Running(54, 20)))
        assertEquals(FpsText.Level.FAIR, FpsText.level(FpsReading.Running(30, 40)))
        assertEquals(FpsText.Level.POOR, FpsText.level(FpsReading.Running(29, 40)))
        assertEquals(FpsText.Level.IDLE, FpsText.level(FpsReading.Idle))
    }
}
