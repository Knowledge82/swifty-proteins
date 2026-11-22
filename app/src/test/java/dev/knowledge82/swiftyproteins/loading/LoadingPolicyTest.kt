package dev.knowledge82.swiftyproteins.loading

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoadingPolicyTest {

    @Test
    fun aDownloadKeepsItsDialogTwoSecondsAnArchivedLigandNone() {
        assertEquals(2_000L, LoadingPolicy.MIN_SHOWN_MILLIS)
        assertEquals(2_000L, LoadingPolicy.minimumMillis(quiet = false))
        assertEquals(0L, LoadingPolicy.minimumMillis(quiet = true))
        assertEquals(500L, LoadingPolicy.minimumMillis(quiet = false, minimum = 500L))
    }

    @Test
    fun theRestIsWhatIsMissingNeverNegative() {
        assertEquals(1_700L, LoadingPolicy.remainingMillis(2_000L, 300L))
        assertEquals(0L, LoadingPolicy.remainingMillis(2_000L, 2_000L))
        assertEquals(0L, LoadingPolicy.remainingMillis(2_000L, 9_000L))   // a slow download waits for nothing more
        assertEquals(0L, LoadingPolicy.remainingMillis(0L, 10L))
    }

    @Test
    fun aQuietLoadShowsItsDialogOnlyIfItIsSlow() {
        // Long enough for a copy on the phone (a few ms) never to flash a dialog; short enough not to leave anyone waiting.
        assertTrue(LoadingPolicy.QUIET_GRACE_MILLIS in 300L..1_000L)
        assertTrue(LoadingPolicy.QUIET_GRACE_MILLIS < LoadingPolicy.MIN_SHOWN_MILLIS)
    }
}
