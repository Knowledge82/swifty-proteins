package dev.knowledge82.swiftyproteins.auth

import dev.knowledge82.swiftyproteins.auth.PasswordPolicy.Rule
import dev.knowledge82.swiftyproteins.auth.UsernamePolicy.Problem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class PoliciesTest {

    @Test
    fun aUserNameIsTrimmedAndLowerCased() {
        assertEquals("adept42", UsernamePolicy.normalize("  Adept42 "))
    }

    @Test
    fun theNameIsLowerCasedTheSameWayOnATurkishPhone() {
        val before = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals("iris", UsernamePolicy.normalize("IRIS"))   // with the phone's locale it would be "ırıs"
        } finally {
            Locale.setDefault(before)
        }
    }

    @Test
    fun theLengthLimitsOfAUserNameAreExact() {
        assertEquals(Problem.EMPTY, UsernamePolicy.check("   "))
        assertEquals(Problem.TOO_SHORT, UsernamePolicy.check("ab"))
        assertNull(UsernamePolicy.check("abc"))
        assertNull(UsernamePolicy.check("a".repeat(20)))
        assertEquals(Problem.TOO_LONG, UsernamePolicy.check("a".repeat(21)))
    }

    @Test
    fun onlyLettersDigitsAndThreeSignsAreAllowed() {
        assertNull(UsernamePolicy.check("tech_priest.mk-7"))
        assertEquals(Problem.BAD_CHARACTERS, UsernamePolicy.check("adept 42"))
        assertEquals(Problem.BAD_CHARACTERS, UsernamePolicy.check("adépt"))
        assertEquals(Problem.BAD_CHARACTERS, UsernamePolicy.check("аdept"))   // the first letter is Cyrillic
        assertEquals(Problem.BAD_CHARACTERS, UsernamePolicy.check("adept@42"))
    }

    @Test
    fun everyProblemHasAMessage() {
        for (p in Problem.values()) assertTrue(UsernamePolicy.message(p).isNotBlank())
    }

    @Test
    fun aGoodPasswordMeetsEveryRule() {
        assertEquals(emptySet<Rule>(), PasswordPolicy.unmet("omnissiah7", "adept42"))
        assertTrue(PasswordPolicy.isAcceptable("omnissiah7", "adept42"))
    }

    @Test
    fun theLengthLimitsOfAPasswordAreExact() {
        assertTrue(Rule.LENGTH in PasswordPolicy.unmet("abcdef1", "x"))            // 7
        assertFalse(Rule.LENGTH in PasswordPolicy.unmet("abcdefg1", "x"))          // 8
        assertFalse(Rule.LENGTH in PasswordPolicy.unmet("a1" + "b".repeat(62), "x"))   // 64
        assertTrue(Rule.LENGTH in PasswordPolicy.unmet("a1" + "b".repeat(63), "x"))    // 65
    }

    @Test
    fun theLengthCountsWhatTheUserSeesNotUtf16Units() {
        // 7 characters: 6 letters/digits + one emoji (2 UTF-16 units). Still too short.
        assertTrue(Rule.LENGTH in PasswordPolicy.unmet("abcde1💀", "x"))
    }

    @Test
    fun aLetterAndADigitAreRequired() {
        assertEquals(setOf(Rule.DIGIT), PasswordPolicy.unmet("omnissiah", "adept42"))
        assertEquals(setOf(Rule.LETTER), PasswordPolicy.unmet("12345678", "adept42"))
        assertFalse(Rule.LETTER in PasswordPolicy.unmet("ñandú123", "x"))   // any alphabet counts as a letter
    }

    @Test
    fun thePasswordMustNotContainTheUserNameWhateverTheCase() {
        assertTrue(Rule.NOT_USERNAME in PasswordPolicy.unmet("xxADEPT42xx", "Adept42"))
        assertFalse(Rule.NOT_USERNAME in PasswordPolicy.unmet("omnissiah7", ""))   // no name yet: not a failure
    }
}
