package dev.knowledge82.swiftyproteins.splash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SplashHoldTest {

    @Test
    fun itKeepsTheSplashRightAtTheStart() {
        assertTrue(SplashHold(startMillis = 10_000).shouldKeep(10_000))
    }

    @Test
    fun itKeepsTheSplashUntilTheMinimumHasPassed() {
        val hold = SplashHold(startMillis = 10_000, minVisibleMillis = 1_500)
        assertTrue(hold.shouldKeep(10_000 + 1_499))
        assertFalse(hold.shouldKeep(10_000 + 1_500))
    }

    @Test
    fun itNeverKeepsTheSplashForeverEvenIfNothingElseHappens() {
        val hold = SplashHold(startMillis = 0)
        assertFalse(hold.shouldKeep(Long.MAX_VALUE / 2))
    }

    @Test
    fun aClockGoingBackwardsDoesNotFreezeTheApp() {
        assertFalse(SplashHold(startMillis = 10_000).shouldKeep(9_000))
    }

    @Test
    fun theDefaultIsComfortablyOverTheOneSecondOfTheEvaluationSheet() {
        assertTrue(SplashHold.MIN_VISIBLE_MILLIS >= 1_200)
        assertEquals(1_500L, SplashHold.MIN_VISIBLE_MILLIS)
    }
}
