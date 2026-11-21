package dev.knowledge82.swiftyproteins.ui

import dev.knowledge82.swiftyproteins.loading.LoadStage
import dev.knowledge82.swiftyproteins.ui.LoadStageText.Mark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LoadStageTextTest {

    private fun marks(stage: LoadStage) = LoadStageText.lines(stage).map { it.mark }

    @Test
    fun fourLinesAlwaysInTheOrderOfTheWork() {
        assertEquals(listOf("Archive", "Connect", "Download", "Parse"), LoadStageText.lines(LoadStage.Starting).map { it.label })
    }

    @Test
    fun beforeTheFirstReportEverythingWaits() {
        assertEquals(List(4) { Mark.WAITING }, marks(LoadStage.Starting))
    }

    @Test
    fun linesBeforeTheCurrentOneAreDoneAndAfterItWait() {
        assertEquals(listOf(Mark.ACTIVE, Mark.WAITING, Mark.WAITING, Mark.WAITING), marks(LoadStage.Archive))
        assertEquals(listOf(Mark.DONE, Mark.ACTIVE, Mark.WAITING, Mark.WAITING), marks(LoadStage.Connecting))
        assertEquals(listOf(Mark.DONE, Mark.DONE, Mark.ACTIVE, Mark.WAITING), marks(LoadStage.Downloading(1, 2)))
        assertEquals(listOf(Mark.DONE, Mark.DONE, Mark.DONE, Mark.ACTIVE), marks(LoadStage.Parsing))
    }

    @Test
    fun theDownloadLineSaysHowMuchArrived() {
        val line = LoadStageText.lines(LoadStage.Downloading(4_710, 7_578))[2]
        assertEquals("62 % · 4.6 / 7.4 KB", line.detail)
        assertEquals("OK", LoadStageText.lines(LoadStage.Parsing)[2].detail)
        assertEquals("", LoadStageText.lines(LoadStage.Connecting)[2].detail)
    }

    @Test
    fun withoutAnAnnouncedLengthOnlyTheBytes() {
        assertEquals("4.6 KB", LoadStageText.downloaded(LoadStage.Downloading(4_710, -1)))
        assertNull(LoadStageText.percent(LoadStage.Downloading(4_710, -1)))
        assertNull(LoadStageText.fraction(LoadStage.Downloading(4_710, -1)))
    }

    @Test
    fun anAnnouncedLengthOfZeroIsNoLengthNotADivisionByZero() {
        // "Content-Length: 0" exists (an empty file): it must not crash the dialog.
        assertNull(LoadStageText.percent(LoadStage.Downloading(0, 0)))
        assertNull(LoadStageText.fraction(LoadStage.Downloading(0, 0)))
        assertEquals("0.0 KB", LoadStageText.downloaded(LoadStage.Downloading(0, 0)))
    }

    @Test
    fun thePercentageRoundsDownSoOneHundredMeansEverything() {
        assertEquals(99, LoadStageText.percent(LoadStage.Downloading(999, 1_000)))
        assertEquals(100, LoadStageText.percent(LoadStage.Downloading(1_000, 1_000)))
        assertEquals(0, LoadStageText.percent(LoadStage.Downloading(0, 1_000)))
        assertEquals(100, LoadStageText.percent(LoadStage.Downloading(1_200, 1_000)))   // a server that lied: never above 100
    }

    @Test
    fun theBarIsRealOnlyWhileDownloadingAKnownLength() {
        assertEquals(0.5f, LoadStageText.fraction(LoadStage.Downloading(500, 1_000))!!, 1e-6f)
        assertNull(LoadStageText.fraction(LoadStage.Connecting))
        assertNull(LoadStageText.fraction(LoadStage.Parsing))
        assertEquals(1f, LoadStageText.fraction(LoadStage.Downloading(5_000, 1_000))!!, 0f)
    }

    @Test
    fun marksAreTextNotOnlyColour() {
        assertEquals(listOf("[OK]", "[>>]", "[  ]"), Mark.entries.map { LoadStageText.markText(it) })
    }

    @Test
    fun talkBackHearsTheStateAndTheDetail() {
        val lines = LoadStageText.lines(LoadStage.Downloading(512, 1_024))
        assertEquals("Archive: done", LoadStageText.spoken(lines[0]))
        assertEquals("Download: in progress, 50 % · 0.5 / 1.0 KB", LoadStageText.spoken(lines[2]))
        assertEquals("Parse: waiting", LoadStageText.spoken(lines[3]))
    }
}
