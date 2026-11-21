package dev.knowledge82.swiftyproteins.share

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

class ShareImageStoreTest {

    private lateinit var root: File
    private lateinit var dir: File

    @Before
    fun setUp() {
        root = Files.createTempDirectory("share-test").toFile()
        dir = File(root, ShareImageStore.DIRECTORY)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun theFileNameSaysWhichLigandItIs() {
        assertEquals("ligand_001.png", ShareImageStore.fileNameFor("001"))
        assertEquals("ligand_ATP.png", ShareImageStore.fileNameFor("ATP"))
    }

    @Test
    fun aPathTrickCannotLeaveTheFolder() {
        val name = ShareImageStore.fileNameFor("../x")
        assertFalse(name.contains('/'))
        assertFalse(name.contains(".."))
        assertEquals("ligand____x.png", name)
    }

    @Test
    fun anEmptyOrNonLatinIdStillGivesAValidName() {
        assertEquals("ligand_unknown.png", ShareImageStore.fileNameFor(""))
        assertEquals("ligand__.png", ShareImageStore.fileNameFor("Å"))
    }

    @Test
    fun savingCreatesTheFolderAndWritesTheBytes() {
        val file = ShareImageStore(dir).save("001") { it.write(byteArrayOf(1, 2, 3)) }
        assertEquals(File(dir, "ligand_001.png"), file)
        assertArrayEquals(byteArrayOf(1, 2, 3), file.readBytes())
    }

    @Test
    fun onlyTheLastImageIsKept() {
        val store = ShareImageStore(dir)
        store.save("001") { it.write(1) }
        store.save("ATP") { it.write(2) }
        assertEquals(listOf("ligand_ATP.png"), dir.list()!!.toList())
    }

    @Test
    fun sharingTheSameLigandAgainReplacesTheFile() {
        val store = ShareImageStore(dir)
        store.save("001") { it.write(1) }
        val file = store.save("001") { it.write(9) }
        assertArrayEquals(byteArrayOf(9), file.readBytes())
        assertEquals(1, dir.list()!!.size)
    }

    @Test
    fun aFailedWriteLeavesNoFileBehindAndKeepsThePreviousImage() {
        val store = ShareImageStore(dir)
        store.save("001") { it.write(1) }
        try {
            store.save("ATP") { it.write(5); throw IllegalStateException("disk full") }
            fail("should have thrown")
        } catch (e: IOException) {
            assertTrue(e.cause is IllegalStateException)
        }
        assertEquals(listOf("ligand_001.png"), dir.list()!!.toList())   // no .part, no half image
    }

    @Test
    fun otherFilesInTheCacheAreNotTouched() {
        dir.mkdirs()
        File(dir, "notes.txt").writeText("keep me")
        ShareImageStore(dir).save("001") { it.write(1) }
        assertTrue(File(dir, "notes.txt").exists())
    }

    @Test
    fun aFolderThatCannotBeCreatedIsAnIOException() {
        val blocker = File(root, "blocker").apply { writeText("I am a file, not a folder") }
        try {
            ShareImageStore(File(blocker, "shared")).save("001") { it.write(1) }
            fail("should have thrown")
        } catch (e: IOException) {
            // expected
        }
    }

    @Test
    fun aPartFileLeftByACancelledCaptureIsCleanedUp() {
        dir.mkdirs()
        File(dir, "ligand_OLD.png.part").writeText("half")
        ShareImageStore(dir).save("001") { it.write(1) }
        assertEquals(listOf("ligand_001.png"), dir.list()!!.toList())
    }

    @Test
    fun anyExportReplacesThePreviousOneButOtherFilesStay() {
        val store = ShareImageStore(dir)
        store.save("ATP") { it.write(1) }
        File(dir, "notes.txt").writeText("mine")
        val glb = store.save("ATP", "glb") { it.write(2) }
        assertEquals("ligand_ATP.glb", glb.name)
        assertEquals(setOf("ligand_ATP.glb", "notes.txt"), dir.list()!!.toSet())
    }

    @Test
    fun anExtensionFromOutsideTheListIsRefused() {
        assertThrows(IllegalArgumentException::class.java) { ShareImageStore.fileNameFor("ATP", "apk") }
        assertThrows(IllegalArgumentException::class.java) { ShareImageStore.fileNameFor("ATP", "png/../x") }
        assertEquals("ligand_ATP.mol", ShareImageStore.fileNameFor("ATP", "mol"))
    }
}
