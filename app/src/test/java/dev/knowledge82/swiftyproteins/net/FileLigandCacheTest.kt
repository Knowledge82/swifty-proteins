package dev.knowledge82.swiftyproteins.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class FileLigandCacheTest {

    private val dir: File = Files.createTempDirectory("ligands").toFile()
    private var clock = 1_700_000_000_000L
    private fun cache(maxBytes: Long = FileLigandCache.DEFAULT_MAX_BYTES) = FileLigandCache(dir, maxBytes) { clock }

    @Test
    fun whatIsWrittenIsReadBackWithItsDate() {
        val c = cache()
        c.write("ATP", "data_ATP\n")
        val back = c.read("ATP")!!
        assertEquals("data_ATP\n", back.text)
        assertEquals(clock, back.savedAtMillis)
    }

    @Test
    fun aMissingLigandIsNull() {
        assertNull(cache().read("HEM"))
    }

    @Test
    fun theFolderIsCreatedIfMissing() {
        val inner = File(dir, "deeper/ligands")
        FileLigandCache(inner) { clock }.write("001", "x")
        assertTrue(File(inner, "001.cif").isFile)
    }

    @Test
    fun noTemporaryFileStays() {
        cache().write("ATP", "abc")
        assertEquals(listOf("ATP.cif"), dir.list()!!.toList())
    }

    @Test
    fun overTheLimitTheOldestGoFirst() {
        val c = cache(maxBytes = 25)
        c.write("AAA", "1234567890"); clock += 1_000
        c.write("BBB", "1234567890"); clock += 1_000
        c.write("CCC", "1234567890")          // 30 bytes > 25: the oldest (AAA) goes
        assertNull(c.read("AAA"))
        assertEquals("1234567890", c.read("BBB")!!.text)
        assertEquals("1234567890", c.read("CCC")!!.text)
    }

    @Test
    fun removeDeletesTheCopy() {
        val c = cache()
        c.write("ATP", "abc")
        c.remove("ATP")
        assertNull(c.read("ATP"))
    }

    @Test
    fun aBrokenFolderNeverThrows() {
        val notADir = File(dir, "file").apply { writeText("I am a file") }
        val c = FileLigandCache(notADir) { clock }
        c.write("ATP", "abc")          // cannot write inside a file: silently not kept
        assertNull(c.read("ATP"))
    }

    @Test
    fun idsListsTheCopiesAndNothingElse() {
        val c = cache()
        c.write("ATP", "a"); c.write("HEM", "h")
        File(dir, "stray.tmp").writeText("half")
        assertEquals(setOf("ATP", "HEM"), c.ids())
        c.remove("ATP")
        assertEquals(setOf("HEM"), c.ids())
    }

    @Test
    fun idsOfAMissingFolderIsEmpty() {
        assertEquals(emptySet<String>(), FileLigandCache(File(dir, "nope")) { clock }.ids())
        assertEquals(emptySet<String>(), NoLigandCache.ids())
    }
}
