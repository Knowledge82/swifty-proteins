package dev.knowledge82.swiftyproteins.scene

import dev.knowledge82.swiftyproteins.cif.CifParser
import dev.knowledge82.swiftyproteins.cif.ParseResult
import dev.knowledge82.swiftyproteins.molecule.Atom
import dev.knowledge82.swiftyproteins.molecule.Molecule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ElementLegendTest {

    private fun molecule(vararg elements: String) =
        Molecule("T", elements.mapIndexed { i, e -> Atom("$e$i", e, i.toFloat(), 0f, 0f) }, emptyList())

    @Test
    fun mostFrequentFirstThenAlphabetical() {
        val m = molecule("O", "C", "H", "C", "N", "H", "C", "H", "S")
        assertEquals(listOf("C", "H", "N", "O", "S"), ElementLegend.of(m, showHydrogens = true))
    }

    @Test
    fun hiddenHydrogensLeaveTheKey() {
        val m = molecule("C", "H", "H", "O")
        assertEquals(listOf("C", "O"), ElementLegend.of(m, showHydrogens = false))
    }

    @Test
    fun neverMoreKeysThanFitInOneRow() {
        val m = molecule("C", "N", "O", "S", "P", "F", "CL", "BR", "I")
        assertEquals(ElementLegend.MAX_KEYS, ElementLegend.of(m, true).size)
    }

    @Test
    fun aMoleculeOfOnlyHydrogenWithHydrogensHiddenHasAnEmptyKey() {
        assertTrue(ElementLegend.of(molecule("H", "H"), showHydrogens = false).isEmpty())
    }

    @Test
    fun theRealLigand001() {
        val text = javaClass.getResource("/cif/001.cif")!!.readText()
        val m = (CifParser.parse(text) as ParseResult.Success).molecule
        val key = ElementLegend.of(m, showHydrogens = true)
        assertEquals("H", key[0])   // 42 of the 87 atoms are hydrogens
        assertTrue(key.containsAll(listOf("C", "N", "O")))
        assertTrue("H" !in ElementLegend.of(m, showHydrogens = false))
    }
}
