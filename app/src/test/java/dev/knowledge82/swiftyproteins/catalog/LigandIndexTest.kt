package dev.knowledge82.swiftyproteins.catalog

import dev.knowledge82.swiftyproteins.cif.CifParser
import dev.knowledge82.swiftyproteins.cif.ParseResult
import dev.knowledge82.swiftyproteins.molecule.MolecularFormula
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LigandIndexTest {

    private val text = """
        # comment
        ATP	ADENOSINE-5'-TRIPHOSPHATE	C10H16N5O13P3	47
        CFF	CAFFEINE	C8H10N4O2	24
        K		K	1
        BAD	only three	fields
        NEG	x	C	-3
        NUM	x	C	many
    """.trimIndent()

    @Test
    fun goodLinesAreReadAndDamagedOnesSkipped() {
        val index = LigandIndex.fromText(text)
        assertEquals(3, index.size)
        assertEquals(LigandIndex.Entry("ATP", "ADENOSINE-5'-TRIPHOSPHATE", "C10H16N5O13P3", 47), index["ATP"])
        assertNull(index["BAD"]); assertNull(index["NEG"]); assertNull(index["NUM"])
    }

    @Test
    fun anEmptyNameIsNoName() {
        val index = LigandIndex.fromText(text)
        assertEquals("", index["K"]!!.name)
        assertEquals("CAFFEINE", index["CFF"]!!.name)
        assertNull(index["ZZZ"])
        assertEquals(0, LigandIndex.EMPTY.size)
    }

    // ---------------------------------------------------------------- the search is by id only (document 23)

    @Test
    fun theNameIsNoLongerSearched() {
        // CFF is CAFFEINE in the index, but the list searches ids only: a hit nobody could see on the row looked wrong.
        val catalog = LigandCatalog.fromText("ATP\nCFF\nK\nCAF\nAC")
        assertTrue(catalog.search("caffeine").isEmpty())
        assertEquals(listOf("CAF"), catalog.search("caf"))
    }

    // ---------------------------------------------------------------- the real file in the app

    private val real: LigandIndex by lazy { LigandIndex.fromText(File("src/main/assets/ligand_index.tsv").readText()) }

    @Test
    fun theBundledIndexCoversTheCatalogue() {
        val ids = LigandCatalog.fromText(File("src/main/assets/ligands.txt").readText()).ids
        val missing = ids.filter { real[it] == null }
        // UNL ("unknown ligand") has no atoms in the dictionary: its cell shows only the id, like before.
        assertEquals(listOf("UNL"), missing)
        assertEquals(ids.size - 1, real.size)
    }

    @Test
    fun everyFormulaOfTheIndexParsesBackAndMatchesItsAtomCount() {
        val ids = LigandCatalog.fromText(File("src/main/assets/ligands.txt").readText()).ids
        for (id in ids) {
            val e = real[id] ?: continue
            val parts = MolecularFormula.parse(e.formula)
            assertNotNull("$id: ${e.formula}", parts)
            assertEquals("$id atoms", e.atoms, parts!!.sumOf { it.count })
            assertTrue("$id has a name", e.name.isNotEmpty())
        }
    }

    @Test
    fun theIndexSaysWhatTheParserSaysAfterLoading() {
        // The same real file the parser tests use: the cell must not promise another formula than the screen shows.
        val parsed = CifParser.parse(javaClass.getResourceAsStream("/cif/001.cif")!!.bufferedReader().use { it.readText() })
        val m = (parsed as ParseResult.Success).molecule
        assertEquals(MolecularFormula.plain(m.atoms), real["001"]!!.formula)
        assertEquals(m.atoms.size, real["001"]!!.atoms)
    }
}
