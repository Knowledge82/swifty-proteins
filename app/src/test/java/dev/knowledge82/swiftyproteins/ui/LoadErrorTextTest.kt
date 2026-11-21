package dev.knowledge82.swiftyproteins.ui

import dev.knowledge82.swiftyproteins.cif.CifFailure
import dev.knowledge82.swiftyproteins.net.LoadError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoadErrorTextTest {

    // One example of EVERY kind of error. That every kind HAS a message is already enforced by the compiler (the
    // `when` in userMessage has no `else`); what these tests check is that the messages are usable.
    private fun everyKind(): List<LoadError> = listOf(
        LoadError.NoConnection,
        LoadError.Timeout,
        LoadError.NotFound,
        LoadError.ServerError(503),
        LoadError.Incomplete,
        LoadError.TooLarge(2_000_000),
        LoadError.Corrupt(CifFailure.NoAtoms),
        LoadError.InvalidId("a/b"),
        LoadError.NetworkError("TLS"),
        LoadError.Unexpected("bug"),
    )

    @Test
    fun everyErrorHasANonBlankMessage() {
        for (error in everyKind()) {
            assertTrue("$error", error.userMessage("ATP").isNotBlank())
        }
    }

    @Test
    fun theFourErrorsOfTheTaskReadDifferently() {
        val four = listOf(LoadError.NoConnection, LoadError.NotFound, LoadError.Corrupt(CifFailure.NoAtoms), LoadError.Timeout)
        assertEquals(4, four.map { it.userMessage("ATP") }.toSet().size)
    }

    @Test
    fun theFourCasesOfTheSubjectUseItsWordsExactly() {
        // VI.3 of the subject, word for word: what an evaluator expects to read.
        assertEquals("No internet connection. Please check your network.", LoadError.NoConnection.userMessage("001"))
        assertEquals("Ligand not found (404). This ligand may not exist in the database.", LoadError.NotFound.userMessage("001"))
        assertEquals("Failed to parse ligand data. The file may be corrupted.", LoadError.Corrupt(CifFailure.NoAtoms).userMessage("001"))
        assertEquals("Request timeout. Please try again.", LoadError.Timeout.userMessage("001"))
    }

    @Test
    fun serverErrorShowsTheStatusCode() {
        assertTrue("503" in LoadError.ServerError(503).userMessage("ATP"))
    }

    @Test
    fun technicalDetailsNeverReachTheUser() {
        val text = LoadError.NetworkError("javax.net.ssl.SSLHandshakeException: secret detail").userMessage("ATP")
        assertFalse(text, "SSL" in text || "secret" in text)
        assertFalse("bug" in LoadError.Unexpected("bug stack trace").userMessage("ATP"))
    }

    @Test
    fun retryIsOfferedOnlyWhenRepeatingCanHelp() {
        for (e in listOf(LoadError.NotFound, LoadError.InvalidId("x"), LoadError.TooLarge(1))) {
            assertFalse("$e", e.canRetry())
        }
        val retryable = listOf(
            LoadError.NoConnection, LoadError.Timeout, LoadError.ServerError(500),
            LoadError.Incomplete, LoadError.NetworkError("x"), LoadError.Unexpected("x"),
        )
        for (e in retryable) assertTrue("$e", e.canRetry())
    }

    @Test
    fun aCorruptFileCanBeRetriedBecauseALoginPageOfAWifiLooksLikeOne() {
        assertTrue(LoadError.Corrupt(CifFailure.NoAtoms).canRetry())
    }

    // --- flavour titles, panel labels and pictures of the designed error dialog ---

    @Test
    fun everyErrorHasAShortFlavourTitleAndAPanelLabel() {
        for (error in everyKind()) {
            val title = error.flavourTitle()
            assertTrue("$error", title.isNotBlank() && title.length <= 34 && '\n' !in title)
            assertTrue("$error", error.panelLabel().contains(" / "))
        }
    }

    @Test
    fun theFourErrorsOfTheTaskHaveDifferentTitlesLabelsAndPictures() {
        val four = listOf(LoadError.NoConnection, LoadError.NotFound, LoadError.Corrupt(CifFailure.NoAtoms), LoadError.Timeout)
        assertEquals(4, four.map { it.flavourTitle() }.toSet().size)
        assertEquals(4, four.map { it.panelLabel() }.toSet().size)
        assertEquals(4, four.map { it.glyph() }.toSet().size)
    }

    @Test
    fun theDesignedPicturesGoToTheirErrors() {
        assertEquals(ErrorGlyph.OFFLINE, LoadError.NoConnection.glyph())
        assertEquals(ErrorGlyph.NOT_FOUND, LoadError.NotFound.glyph())
        assertEquals(ErrorGlyph.TIMEOUT, LoadError.Timeout.glyph())
        assertEquals(ErrorGlyph.CORRUPT, LoadError.Corrupt(CifFailure.NoAtoms).glyph())
        assertEquals(ErrorGlyph.INVALID, LoadError.InvalidId("a/b").glyph())
        assertEquals(ErrorGlyph.WARNING, LoadError.ServerError(503).glyph())
    }

    @Test
    fun theHttpLabelsCarryTheStatusCode() {
        assertEquals("HTTP / 404", LoadError.NotFound.panelLabel())
        assertEquals("HTTP / 503", LoadError.ServerError(503).panelLabel())
    }

    @Test
    fun theFlavourNeverReplacesThePlainMessage() {
        // The flavour title is decoration: the plain message must still say what happened, in ordinary words.
        assertTrue(LoadError.NoConnection.userMessage("001").contains("network", ignoreCase = true))
        assertTrue(LoadError.NotFound.userMessage("001").contains("404"))
    }
}
