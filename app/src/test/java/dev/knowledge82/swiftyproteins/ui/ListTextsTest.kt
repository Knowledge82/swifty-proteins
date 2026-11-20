package dev.knowledge82.swiftyproteins.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class ListTextsTest {

    @Test
    fun theWholeCatalogIsCountedWithAThousandsComma() {
        assertEquals("1,243 ENTRIES", ListTexts.status(available = true, total = 1243, query = "", shown = 1243))
        assertEquals("CATALOG READY // 1,243 INDEXED LIGANDS", ListTexts.footer(true, 1243, "", 1243))
    }

    @Test
    fun theCommaDoesNotDependOnThePhoneLanguage() {
        val before = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)   // German would write "1.243"
            assertEquals("1,243 ENTRIES", ListTexts.status(true, 1243, "", 1243))
        } finally {
            Locale.setDefault(before)
        }
    }

    @Test
    fun aSearchCountsItsResultsWithTheRightPlural() {
        assertEquals("2 RESULTS", ListTexts.status(true, 1243, "at", 2))
        assertEquals("1 RESULT", ListTexts.status(true, 1243, "cdl", 1))
        assertEquals("0 RESULTS", ListTexts.status(true, 1243, "xyz", 0))
    }

    @Test
    fun spacesOnlyCountAsAnEmptySearch() {
        assertEquals("1,243 ENTRIES", ListTexts.status(true, 1243, "   ", 1243))
    }

    @Test
    fun theFooterFollowsTheSearch() {
        assertEquals("FILTER LIVE // CASE INSENSITIVE", ListTexts.footer(true, 1243, "at", 2))
        assertEquals("SEARCH COMPLETE // NO MATCH", ListTexts.footer(true, 1243, "xyz", 0))
    }

    @Test
    fun anUnreadableOrEmptyCatalogSaysSo() {
        assertEquals("CATALOG UNAVAILABLE", ListTexts.status(false, 0, "", 0))
        assertEquals("CATALOG ERROR // LIST NOT READABLE", ListTexts.footer(false, 0, "", 0))
        assertEquals("CATALOG EMPTY", ListTexts.footer(true, 0, "", 0))
        assertEquals("1 ENTRY", ListTexts.status(true, 1, "", 1))
    }
}
