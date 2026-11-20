package dev.knowledge82.swiftyproteins.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class ListTextsTest {

    @Test
    fun theArchiveTabShowsTheTotalWithoutSeparator() {
        assertEquals("ARCHIVE ACCESS / 1243", ListTexts.archiveLabel(true, 1243))
        assertEquals("ARCHIVE ACCESS / ERROR", ListTexts.archiveLabel(false, 0))
    }

    @Test
    fun theWholeCatalogIsCountedWithAThousandsComma() {
        assertEquals("PATTERNS FOUND: 1,243", ListTexts.patternsFound(true, 1243, "", 1243))
    }

    @Test
    fun theCommaDoesNotDependOnThePhoneLanguage() {
        val before = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)   // German would write "1.243"
            assertEquals("PATTERNS FOUND: 1,243", ListTexts.patternsFound(true, 1243, "", 1243))
        } finally {
            Locale.setDefault(before)
        }
    }

    @Test
    fun aSearchCountsItsMatches() {
        assertEquals("PATTERNS FOUND: 2", ListTexts.patternsFound(true, 1243, "at", 2))
        assertEquals("PATTERNS FOUND: 0", ListTexts.patternsFound(true, 1243, "xyz", 0))
    }

    @Test
    fun spacesOnlyCountAsAnEmptySearch() {
        // shown = 999 on purpose: a search of only spaces must report the TOTAL, whatever the row count says.
        assertEquals("PATTERNS FOUND: 1,243", ListTexts.patternsFound(true, 1243, "   ", 999))
    }

    @Test
    fun anUnreadableCatalogSaysSo() {
        assertEquals("ARCHIVE UNREADABLE", ListTexts.patternsFound(false, 0, "", 0))
    }

    @Test
    fun theNoMatchMessageQuotesTheTrimmedQuery() {
        assertEquals("No pattern matches 'xyz'.", ListTexts.noMatch("  xyz "))
    }
}
