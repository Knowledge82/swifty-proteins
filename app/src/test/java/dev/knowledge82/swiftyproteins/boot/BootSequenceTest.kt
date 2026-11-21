package dev.knowledge82.swiftyproteins.boot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class BootSequenceTest {

    @Test
    fun withoutATapTheBootScreenLastsItsFullTime() {
        assertEquals(4_200L, BootSequence.DURATION_MILLIS)
        assertFalse(BootSequence.isDone(999))
        assertFalse(BootSequence.isDone(BootSequence.DURATION_MILLIS - 1))
        assertTrue(BootSequence.isDone(BootSequence.DURATION_MILLIS))
    }

    @Test
    fun aTapEndsItButNeverBeforeTheSecondOfTheEvaluationSheet() {
        assertTrue(BootSequence.MIN_VISIBLE_MILLIS >= 1_000)
        assertFalse(BootSequence.isDone(0, skipRequested = true))
        assertFalse(BootSequence.isDone(BootSequence.MIN_VISIBLE_MILLIS - 1, skipRequested = true))
        assertTrue(BootSequence.isDone(BootSequence.MIN_VISIBLE_MILLIS, skipRequested = true))
        assertFalse(BootSequence.isDone(2_000, skipRequested = false))
    }

    @Test
    fun theTapHintAppearsWhenATapIsAllowed() {
        assertFalse(BootSequence.canSkip(BootSequence.MIN_VISIBLE_MILLIS - 1))
        assertTrue(BootSequence.canSkip(BootSequence.MIN_VISIBLE_MILLIS))
    }

    @Test
    fun theDataVaultLineShowsTheRealCountWithAComma() {
        assertEquals("> DATA-VAULT ... 1,243", BootSequence.lines(1243)[2])
        assertEquals(5, BootSequence.lines(1243).size)
    }

    @Test
    fun theCountDoesNotDependOnThePhoneLanguage() {
        val before = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("> DATA-VAULT ... 1,243", BootSequence.lines(1243)[2])
        } finally {
            Locale.setDefault(before)
        }
    }

    @Test
    fun anUnreadableListIsSaidOnTheBootScreen() {
        assertEquals("> DATA-VAULT ... UNREADABLE", BootSequence.lines(null)[2])
    }

    @Test
    fun theFirstPhaseIs65PercentOfTheTime() {
        assertEquals(2_730L, BootSequence.LINES_PHASE_MILLIS)
        assertFalse(BootSequence.isComplete(2_729))
        assertTrue(BootSequence.isComplete(2_730))
    }

    @Test
    fun linesAppearOneByOneAndAllAreVisibleBeforeTheEnd() {
        assertEquals(1, BootSequence.visibleLines(0, 5))
        assertEquals(1, BootSequence.visibleLines(545, 5))
        assertEquals(2, BootSequence.visibleLines(546, 5))
        assertEquals(4, BootSequence.visibleLines(2_183, 5))
        assertEquals(5, BootSequence.visibleLines(2_184, 5))
        assertEquals(5, BootSequence.visibleLines(BootSequence.LINES_PHASE_MILLIS - 1, 5))   // all before "DONE"
        assertEquals(5, BootSequence.visibleLines(BootSequence.DURATION_MILLIS - 1, 5))
        assertEquals(5, BootSequence.visibleLines(60_000, 5))   // never more than there are
    }

    @Test
    fun reducedMotionShowsEverythingAtOnce() {
        assertEquals(5, BootSequence.visibleLines(0, 5, reduceMotion = true))
    }

    @Test
    fun aClockGoingBackwardsStillShowsTheFirstLineAndNoProgress() {
        // -1000 and not -50: integer division would hide a missing guard for small negative values.
        assertEquals(1, BootSequence.visibleLines(-1_000, 5))
        assertEquals(0, BootSequence.progressBlocks(-1_000))
    }

    @Test
    fun theProgressBarFillsWithTimeAndEndsFull() {
        assertEquals(0, BootSequence.progressBlocks(0))
        assertEquals(2, BootSequence.progressBlocks(BootSequence.LINES_PHASE_MILLIS / 2))
        assertEquals(4, BootSequence.progressBlocks(BootSequence.LINES_PHASE_MILLIS - 1))
        assertEquals(5, BootSequence.progressBlocks(BootSequence.LINES_PHASE_MILLIS))   // full exactly when "DONE" starts
        assertEquals(5, BootSequence.progressBlocks(BootSequence.DURATION_MILLIS))
        assertEquals(5, BootSequence.progressBlocks(10_000))
        assertEquals("[##][##][  ][  ][  ]", BootSequence.progressBar(2))
        assertEquals("[##][##][##][##][##]", BootSequence.progressBar(5))
    }

    @Test
    fun doneBlinksThreeTimesInTheSecondPhaseStartingLit() {
        val start = BootSequence.LINES_PHASE_MILLIS
        assertFalse(BootSequence.doneVisible(start - 1))
        assertTrue(BootSequence.doneVisible(start))
        assertTrue(BootSequence.doneVisible(start + 244))
        assertFalse(BootSequence.doneVisible(start + 245))
        assertTrue(BootSequence.doneVisible(start + 490))
        // 1470 ms of second phase / (2 x 245 ms) = exactly three blinks.
        assertEquals(1_470L, BootSequence.DURATION_MILLIS - start)
        assertEquals(0L, (BootSequence.DURATION_MILLIS - start) % (2 * BootSequence.DONE_BLINK_MILLIS))
    }

    @Test
    fun withReducedMotionDoneIsSteady() {
        val start = BootSequence.LINES_PHASE_MILLIS
        assertFalse(BootSequence.doneVisible(start - 1, reduceMotion = true))
        assertTrue(BootSequence.doneVisible(start + 245, reduceMotion = true))
    }
}
