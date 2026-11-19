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
    fun notFoundNamesTheLigandAndTheCode() {
        val text = LoadError.NotFound.userMessage("ZZZ")
        assertTrue(text, "ZZZ" in text && "404" in text)
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
}
