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
        assertNull(index.nameOf("K"))
        assertEquals("CAFFEINE", index.nameOf("CFF"))
        assertNull(index.nameOf("ZZZ"))
        assertEquals(0, LigandIndex.EMPTY.size)
    }

    // ---------------------------------------------------------------- the search by name

    private val catalog = LigandCatalog.fromText("ATP\nCFF\nK\nCAF\nAC")

    @Test
    fun theNameFindsWhatTheIdCannot() {
        val index = LigandIndex.fromText(text)
        assertEquals(listOf("CFF"), catalog.search("caffeine", index::nameOf))
        assertEquals(listOf("ATP"), catalog.search("triphosphate", index::nameOf))
    }

    @Test
    fun idMatchesComeFirstAndNothingTwice() {
        // "CAF" matches the id CAF and the NAME of CFF ("CAFFEINE"): the id first, then the name, each once.
        val index = LigandIndex.fromText(text)
        assertEquals(listOf("CAF", "CFF"), catalog.search("caf", index::nameOf))
        // "A": ids starting with it, ids containing it, then names (ATP's and CFF's names contain A but they are already in).
        val r = catalog.search("a", index::nameOf)
        assertEquals(r.size, r.toSet().size)
        assertEquals(listOf("ATP", "AC", "CAF", "CFF"), r)
    }

    @Test
    fun withoutNamesTheSearchIsTheOldOne() {
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
