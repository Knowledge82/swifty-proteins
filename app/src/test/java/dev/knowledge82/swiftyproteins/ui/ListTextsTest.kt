package dev.knowledge82.swiftyproteins.ui

import dev.knowledge82.swiftyproteins.molecule.MolecularFormula
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

    @Test
    fun withTheFavouritesFilterTheCountSaysFavourites() {
        assertEquals("FAVOURITES FOUND: 3", ListTexts.patternsFound(true, 1243, "", 3, onlyFavorites = true))
        assertEquals("FAVOURITES FOUND: 0", ListTexts.patternsFound(true, 1243, "A", 0, onlyFavorites = true))
        assertEquals("ARCHIVE UNREADABLE", ListTexts.patternsFound(false, 0, "", 0, onlyFavorites = true))
    }

    @Test
    fun anEmptyFavouritesListExplainsWhy() {
        assertEquals("No favourites yet. Tap the star of a ligand to keep it here.", ListTexts.noFavorites("  "))
        assertEquals("No favourite matches 'AT'.", ListTexts.noFavorites(" AT "))
    }

    // ---------------------------------------------------------------- rich cells (document 21)

    @Test
    fun atomCountsReadNaturally() {
        assertEquals("1 ATOM", ListTexts.atoms(1))
        assertEquals("47 ATOMS", ListTexts.atoms(47))
        assertEquals("1,200 ATOMS", ListTexts.atoms(1_200))
    }

    @Test
    fun talkBackHearsTheWholeCell() {
        assertEquals(
            "Ligand ATP, ADENOSINE-5'-TRIPHOSPHATE, formula C 10 H 16 N 5 O 13 P 3, 47 atoms, saved on the phone. Inspect",
            ListTexts.cellDescription("ATP", "ADENOSINE-5'-TRIPHOSPHATE", "C10H16N5O13P3", 47, archived = true),
        )
        assertEquals("Ligand K, formula K, 1 atom. Inspect", ListTexts.cellDescription("K", "", "K", 1, archived = false))
        assertEquals("Ligand UNL. Inspect", ListTexts.cellDescription("UNL", null, null, null, archived = false))
        // The comparison picker says what its tap does (document 24).
        assertEquals("Ligand K, formula K, 1 atom. Compare", ListTexts.cellDescription("K", "", "K", 1, archived = false, action = "Compare"))
    }

    @Test
    fun theCompositionBarIsTheShareOfEachElement() {
        val parts = MolecularFormula.parse("C2H6O")!!
        assertEquals(listOf("C" to 2f / 9, "H" to 6f / 9, "O" to 1f / 9), ListTexts.composition(parts))
        assertEquals(1f, ListTexts.composition(parts).sumOf { it.second.toDouble() }.toFloat(), 1e-6f)
        assertEquals(emptyList<Pair<String, Float>>(), ListTexts.composition(emptyList()))
    }
}
