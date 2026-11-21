package dev.knowledge82.swiftyproteins.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileFavoritesStoreTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun store(dir: File = File(tmp.root, "favorites")) = FileFavoritesStore(dir)

    @Test
    fun whatIsSavedIsLoadedBack() {
        val s = store()
        assertTrue(s.save("adept42", setOf("ATP", "HEM", "001")))
        assertEquals(setOf("001", "ATP", "HEM"), s.load("adept42"))
    }

    @Test
    fun anAccountWithoutAFileHasNoFavourites() {
        assertEquals(emptySet<String>(), store().load("nobody"))
    }

    @Test
    fun eachAccountHasItsOwnFile() {
        val s = store()
        s.save("adept42", setOf("ATP"))
        s.save("magos7", setOf("HEM"))
        assertEquals(setOf("ATP"), s.load("adept42"))
        assertEquals(setOf("HEM"), s.load("magos7"))
        assertNotEquals(s.fileOf("adept42"), s.fileOf("magos7"))
    }

    @Test
    fun theFileNameDoesNotRevealTheAccount() {
        val name = store().fileOf("adept42").name
        assertFalse(name, name.contains("adept"))
        assertTrue(name, Regex("fav_[0-9a-f]{32}\\.txt").matches(name))
    }

    @Test
    fun theFileIsOneSortedIdPerLineAndLeavesNoTemporary() {
        val dir = File(tmp.root, "favorites")
        val s = store(dir)
        s.save("adept42", setOf("HEM", "ATP"))
        assertEquals("ATP\nHEM\n", s.fileOf("adept42").readText())
        assertEquals(listOf(s.fileOf("adept42").name), dir.list()!!.toList())
    }

    @Test
    fun aTamperedFileOnlyGivesValidIds() {
        val s = store()
        s.save("adept42", setOf("ATP"))
        s.fileOf("adept42").writeText("ATP\n../../etc\n<script>\natp\nTOOLONG1\n  HEM  \n\n")
        assertEquals(setOf("ATP", "HEM"), s.load("adept42"))   // trimmed, upper-case only, 1-5 characters
    }

    @Test
    fun invalidIdsAreNeverWritten() {
        val s = store()
        s.save("adept42", setOf("ATP", "bad id", ""))
        assertEquals("ATP\n", s.fileOf("adept42").readText())
    }

    @Test
    fun aHugeFileIsCut() {
        val s = store()
        s.save("adept42", emptySet())
        val ids = (0 until 3_000).map { "%05d".format(it) }
        s.fileOf("adept42").writeText(ids.joinToString("\n"))
        assertEquals(FileFavoritesStore.MAX_FAVORITES, s.load("adept42").size)
    }

    @Test
    fun anEmptySetLeavesAnEmptyFile() {
        val s = store()
        s.save("adept42", setOf("ATP"))
        s.save("adept42", emptySet())
        assertEquals(emptySet<String>(), s.load("adept42"))
        assertEquals("", s.fileOf("adept42").readText())
    }

    @Test
    fun aFolderThatCannotBeCreatedNeverThrows() {
        val blocker = tmp.newFile("blocker")   // a FILE where the folder should be
        val s = FileFavoritesStore(File(blocker, "favorites"))
        assertFalse(s.save("adept42", setOf("ATP")))
        assertEquals(emptySet<String>(), s.load("adept42"))
    }
}
