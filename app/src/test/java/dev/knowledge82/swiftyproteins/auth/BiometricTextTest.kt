package dev.knowledge82.swiftyproteins.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BiometricTextTest {

    @Test
    fun cancellingOnPurposeShowsNoDialog() {
        assertNull(BiometricText.dialogFor(BiometricError.CANCELED))
    }

    @Test
    fun everyRealErrorHasATitleAndAPlainMessage() {
        for (e in BiometricError.values().filter { it != BiometricError.CANCELED }) {
            val d = BiometricText.dialogFor(e)
            assertNotNull(e.name, d)
            assertTrue(d!!.title.isNotBlank() && d.message.isNotBlank())
        }
    }

    @Test
    fun tooManyAttemptsUsesTheWordsOfTheSubject() {
        assertTrue(BiometricText.dialogFor(BiometricError.LOCKOUT)!!.message.startsWith("Too many failed attempts."))
        assertTrue(BiometricText.dialogFor(BiometricError.OTHER)!!.message.startsWith("Authentication failed. Please try again."))
    }

    @Test
    fun theSystemTextIsAddedOnlyToUnknownErrors() {
        assertEquals(
            "Authentication failed. Please try again. (Sensor dirty)",
            BiometricText.dialogFor(BiometricError.OTHER, "Sensor dirty")!!.message,
        )
        assertEquals("Authentication failed. Please try again.", BiometricText.dialogFor(BiometricError.OTHER, " ")!!.message)
        assertFalse(BiometricText.dialogFor(BiometricError.TIMEOUT, "Sensor dirty")!!.message.contains("Sensor dirty"))
    }

    @Test
    fun aHiddenButtonIsAlwaysExplainedAndAnAvailableOneIsNot() {
        assertNull(BiometricText.availabilityNote(BiometricAvailability.AVAILABLE))
        for (a in BiometricAvailability.values().filter { it != BiometricAvailability.AVAILABLE }) {
            assertNotNull(a.name, BiometricText.availabilityNote(a))
        }
    }

    @Test
    fun aSessionStartsLockedAndRemembersWhoAfterLocking() {
        val s = SessionState()
        assertTrue(s.isLocked)
        assertNull(s.username)
        s.unlock("adept42")
        assertFalse(s.isLocked)
        s.lock()
        assertTrue(s.isLocked)
        assertEquals("adept42", s.username)
    }
}
