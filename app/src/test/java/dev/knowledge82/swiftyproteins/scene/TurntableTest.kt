package dev.knowledge82.swiftyproteins.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TurntableTest {

    private val turn = Turntable.TURN_MILLIS * 1_000_000L

    @Test
    fun oneFullTurnAtAConstantSpeed() {
        assertEquals(0f, Turntable.angle(0), 0f)
        assertEquals(90f, Turntable.angle(turn / 4), 1e-3f)
        assertEquals(180f, Turntable.angle(turn / 2), 1e-3f)
        assertEquals(360f, Turntable.angle(turn), 0f)
        // Constant speed: equal times, equal angles (the loop meets itself without a jump).
        assertEquals(Turntable.angle(turn / 10), Turntable.angle(turn / 2 + turn / 10) - Turntable.angle(turn / 2), 1e-3f)
    }

    @Test
    fun neverBeyondOneTurnNorBackwards() {
        assertEquals(360f, Turntable.angle(turn * 3), 0f)
        assertEquals(0f, Turntable.angle(-5_000_000L), 0f)
        assertEquals(360f, Turntable.angle(1, turnNanos = 0), 0f)
    }

    @Test
    fun theTurnEndsExactlyAtItsDuration() {
        assertFalse(Turntable.isDone(turn - 1))
        assertTrue(Turntable.isDone(turn))
    }

    @Test
    fun theRecordingOutlastsTheTurnAndTheSettleOutlastsTheCameraGlide() {
        assertTrue(Turntable.MAX_RECORDING_MILLIS > Turntable.TURN_MILLIS)
        assertTrue(Turntable.SETTLE_MILLIS >= 600)   // SceneView glides a rebuilt camera for ~0.6 s
    }

    @Test
    fun theRecLineCountsTheSecondsOfTheTurn() {
        assertEquals("REC 0.0 / 6.0 s", Turntable.label(0))
        assertEquals("REC 2.4 / 6.0 s", Turntable.label(2_400))
        assertEquals("REC 6.0 / 6.0 s", Turntable.label(9_000))
    }
}
