package dev.knowledge82.swiftyproteins.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingTest {

    @Test
    fun fourPagesEachWithATitleAndSomethingToSay() {
        assertEquals(4, Onboarding.pages.size)
        for (p in Onboarding.pages) {
            assertTrue(p.title.isNotBlank())
            assertTrue(p.lines.isNotEmpty())
        }
        assertEquals(Onboarding.pages.size, Onboarding.pages.map { it.art }.toSet().size)   // a different picture each
    }

    @Test
    fun thePagesStayInRange() {
        assertEquals(1, Onboarding.next(0))
        assertEquals(Onboarding.last, Onboarding.next(Onboarding.last))
        assertEquals(0, Onboarding.previous(0))
        assertTrue(Onboarding.isLast(3))
        assertFalse(Onboarding.isLast(2))
    }

    @Test
    fun aSwipeLeftBringsTheNextPageAndAShortOneNothing() {
        assertEquals(2, Onboarding.afterSwipe(1, dragPx = -200f, thresholdPx = 150f))
        assertEquals(0, Onboarding.afterSwipe(1, dragPx = 200f, thresholdPx = 150f))
        assertEquals(1, Onboarding.afterSwipe(1, dragPx = -100f, thresholdPx = 150f))
        assertEquals(2, Onboarding.afterSwipe(1, dragPx = -150f, thresholdPx = 150f))   // exactly the threshold counts
        assertEquals(Onboarding.last, Onboarding.afterSwipe(Onboarding.last, -500f, 150f))
    }

    @Test
    fun theCounterSaysWhereWeAre() {
        assertEquals("1 / 4", Onboarding.counter(0))
        assertEquals("4 / 4", Onboarding.counter(3))
        assertEquals("4 / 4", Onboarding.counter(9))
    }

    @Test
    fun theTutorialMentionsEveryKeyOfTheMoleculeScreen() {
        val all = Onboarding.pages.flatMap { it.lines }.joinToString(" ")
        for (key in listOf("MODEL", "H ", "LABELS", "RULER", "SHARE", "CMP")) assertTrue(key, all.contains(key))
    }
}
