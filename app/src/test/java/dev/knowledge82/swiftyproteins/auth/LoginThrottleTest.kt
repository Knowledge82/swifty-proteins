package dev.knowledge82.swiftyproteins.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class LoginThrottleTest {

    @Test
    fun theFirstFourFailuresCostNothing() {
        for (n in 0..4) assertEquals(0L, LoginThrottle.waitAfter(n))
    }

    @Test
    fun theFifthFailureWaitsThirtySecondsAndEachNextOneDoubles() {
        assertEquals(30_000L, LoginThrottle.waitAfter(5))
        assertEquals(60_000L, LoginThrottle.waitAfter(6))
        assertEquals(120_000L, LoginThrottle.waitAfter(7))
        assertEquals(240_000L, LoginThrottle.waitAfter(8))
    }

    @Test
    fun theWaitNeverPassesFiveMinutesNorOverflows() {
        assertEquals(300_000L, LoginThrottle.waitAfter(9))
        assertEquals(300_000L, LoginThrottle.waitAfter(70))          // 30 000 shl 65 would be garbage
        assertEquals(300_000L, LoginThrottle.waitAfter(Int.MAX_VALUE))
    }

    @Test
    fun theMessageRoundsUpAndSpeaksInMinutesWhenLong() {
        assertEquals("Too many failed attempts. Try again in 1 s.", LoginThrottle.message(1))
        assertEquals("Too many failed attempts. Try again in 30 s.", LoginThrottle.message(29_001))
        assertEquals("Too many failed attempts. Try again in 1 min.", LoginThrottle.message(60_000))
        assertEquals("Too many failed attempts. Try again in 4 min 10 s.", LoginThrottle.message(250_000))
        assertEquals("Too many failed attempts. Try again in 1 s.", LoginThrottle.message(0))   // never "0 s"
    }
}
