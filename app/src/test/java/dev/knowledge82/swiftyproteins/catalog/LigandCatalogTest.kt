package dev.knowledge82.swiftyproteins.catalog

import dev.knowledge82.swiftyproteins.net.FetchResult
import dev.knowledge82.swiftyproteins.net.HttpFetcher
import dev.knowledge82.swiftyproteins.net.LigandRepository
import dev.knowledge82.swiftyproteins.net.LigandLoadResult
import dev.knowledge82.swiftyproteins.net.LoadError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale

class LigandCatalogTest {

    private fun catalog(vararg ids: String) = LigandCatalog.fromText(ids.joinToString("\n"))

    // ---------------------------------------------------------------- reading the text

    @Test
    fun oneIdPerLineInTheOrderOfTheFile() {
        assertEquals(listOf("ZN", "ATP", "001"), catalog("ZN", "ATP", "001").ids)
    }

    @Test
    fun windowsLineEndingsBlankLinesAndSpacesAreIgnored() {
        val catalog = LigandCatalog.fromText("ZN\r\n\r\n  ATP  \r\n\n001")
        assertEquals(listOf("ZN", "ATP", "001"), catalog.ids)
    }

    @Test
    fun repeatedIdsAppearOnlyOnce() {
        assertEquals(listOf("ATP", "ZN"), catalog("ATP", "ZN", "ATP", "ZN").ids)
    }

    @Test
    fun emptyTextIsAnEmptyCatalogNotAnError() {
        assertEquals(emptyList<String>(), LigandCatalog.fromText("").ids)
        assertEquals(emptyList<String>(), LigandCatalog.fromText("\n \n").ids)
        assertEquals(emptyList<String>(), LigandCatalog.fromText("").search("A"))
    }

    // ---------------------------------------------------------------- searching

    @Test
    fun emptyOrBlankQueryMatchesEverything() {
        val catalog = catalog("ATP", "ZN", "HEM")
        assertEquals(catalog.ids, catalog.search(""))
        assertEquals(catalog.ids, catalog.search("   "))
    }

    @Test
    fun caseDoesNotMatter() {
        val catalog = catalog("ATP", "ZN", "HEM")
        assertEquals(listOf("ATP"), catalog.search("atp"))
        assertEquals(listOf("ATP"), catalog.search("ATP"))
        assertEquals(listOf("ATP"), catalog.search("aTp"))
    }

    @Test
    fun theTextCanBeAnywhereInsideTheId() {
        val catalog = catalog("ATP", "DTP", "ZN")
        assertEquals(listOf("ATP", "DTP"), catalog.search("TP"))
    }

    @Test
    fun idsStartingWithTheTextComeFirstThenTheRestKeepingTheFileOrder() {
        val catalog = catalog("AAT", "ATP", "BAT", "CAT", "AT1", "ZN")
        assertEquals(listOf("ATP", "AT1", "AAT", "BAT", "CAT"), catalog.search("at"))
    }

    @Test
    fun spacesAroundTheQueryAreIgnoredButNotInsideIt() {
        val catalog = catalog("ATP", "ZN")
        assertEquals(listOf("ATP"), catalog.search("  atp "))
        assertEquals(emptyList<String>(), catalog.search("A TP"))
    }

    @Test
    fun noMatchGivesAnEmptyList() {
        assertEquals(emptyList<String>(), catalog("ATP", "ZN").search("XYZ"))
    }

    @Test
    fun digitsWorkLikeLetters() {
        val catalog = catalog("001", "011", "0A1", "ATP")
        assertEquals(listOf("001", "011", "0A1"), catalog.search("0"))
        // "011" starts with "01"; "001" only contains it, so it goes second although it comes first in the file
        assertEquals(listOf("011", "001"), catalog.search("01"))
    }

    @Test
    fun theSearchDoesNotDependOnTheLanguageOfThePhone() {
        // In Turkish "i".uppercase() is "İ" (with a dot): a search written with uppercase() would not find "ZIP".
        val saved = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals(listOf("ZIP"), catalog("ZIP", "ATP").search("zip"))
            assertEquals(listOf("ZIP"), catalog("ZIP", "ATP").search("ZIP"))
        } finally {
            Locale.setDefault(saved)
        }
    }

    // ---------------------------------------------------------------- the real file

    private val realText: String by lazy {
        // Gradle and Android Studio run unit tests with the working directory in the module folder (app/).
        val file = File("src/main/assets/ligands.txt")
        assertTrue("ligands.txt not found at ${file.absolutePath}", file.exists())
        file.readText()
    }

    @Test
    fun theRealFileHas1243DifferentIds() {
        val catalog = LigandCatalog.fromText(realText)
        assertEquals(1243, catalog.ids.size)
        assertEquals("001", catalog.ids.first())
        assertEquals("ZZS", catalog.ids.last())
    }

    @Test
    fun everyIdOfTheRealFileIsAcceptedByTheRepository() {
        // If one id were not valid, tapping it would fail with InvalidId. So the list and the repository must agree.
        val fetcher = object : HttpFetcher {
            override fun fetch(url: String, isCancelled: () -> Boolean) = FetchResult.HttpError(404)
        }
        val repository = LigandRepository(fetcher)
        for (id in LigandCatalog.fromText(realText).ids) {
            val result = repository.load(id)
            assertTrue("$id rejected: $result", !(result is LigandLoadResult.Failure && result.error is LoadError.InvalidId))
        }
    }

    @Test
    fun searchesOnTheRealFile() {
        val catalog = LigandCatalog.fromText(realText)
        assertEquals(listOf("ATP"), catalog.search("atp").filter { it == "ATP" })
        assertEquals("ATP", catalog.search("ATP").first())
        assertEquals(catalog.ids.size, catalog.search("").size)
        assertTrue(catalog.search("ZN").contains("ZN"))
        assertEquals("ZN", catalog.search("zn").first())
        // every result really contains the text, and nothing that contains it is lost
        val expected = catalog.ids.filter { it.contains("AT", ignoreCase = true) }.toSet()
        assertEquals(expected, catalog.search("at").toSet())
        assertEquals(expected.size, catalog.search("at").size)
    }
}
