package dev.knowledge82.swiftyproteins.molecule

import org.junit.Assert.assertEquals
import org.junit.Test

class MolecularFormulaTest {

    /** Atoms from a string of symbols separated by spaces: "C C H O" (positions do not matter for a formula). */
    private fun atoms(symbols: String) = symbols.split(" ").mapIndexed { i, e -> Atom("A$i", e, 0f, 0f, 0f) }

    @Test
    fun carbonFirstThenHydrogenThenTheRestAlphabetically() {
        // ATP: C10 H16 N5 O13 P3, given in a shuffled order.
        val atp = atoms(("P ".repeat(3) + "O ".repeat(13) + "N ".repeat(5) + "H ".repeat(16) + "C ".repeat(10)).trim())
        assertEquals("C10H16N5O13P3", MolecularFormula.plain(atp))
    }

    @Test
    fun withoutCarbonEverythingIsAlphabeticalHydrogenIncluded() {
        // Hydrogen chloride is "ClH" in Hill order, not "HCl"; water is "H2O"; ammonia "H3N".
        assertEquals("ClH", MolecularFormula.plain(atoms("H Cl")))
        assertEquals("H2O", MolecularFormula.plain(atoms("O H H")))
        assertEquals("H3N", MolecularFormula.plain(atoms("N H H H")))
    }

    @Test
    fun withCarbonHydrogenComesSecondEvenBeforeElementsThatSortEarlier() {
        // B, Br and Cl sort before H alphabetically, but with carbon present H is second.
        assertEquals("CHBBrCl", MolecularFormula.plain(atoms("Cl Br B H C")))
    }

    @Test
    fun twoLetterSymbolsSortAsWords() {
        // "C" < "Ca" < "Cl" < "Co" with carbon first anyway; without carbon "Ca" < "Cl" < "Co".
        assertEquals("CaClCo", MolecularFormula.plain(atoms("Co Cl Ca")))
    }

    @Test
    fun aCountOfOneIsNotWritten() {
        assertEquals("CH4O", MolecularFormula.plain(atoms("C O H H H H")))
        assertEquals("Zn", MolecularFormula.plain(atoms("Zn")))
    }

    @Test
    fun theCcdStyleSeparatesElementsWithSpaces() {
        // As in _chem_comp.formula of the real file 001: "C35 H42 F2 N2 O6".
        val real001 = atoms(("C ".repeat(35) + "H ".repeat(42) + "F F N N " + "O ".repeat(6)).trim())
        assertEquals("C35 H42 F2 N2 O6", MolecularFormula.spaced(real001))
    }

    @Test
    fun thePartsKeepEveryElementAndItsCount() {
        val parts = MolecularFormula.of(atoms("O C H H O"))
        assertEquals(listOf(MolecularFormula.Part("C", 1), MolecularFormula.Part("H", 2), MolecularFormula.Part("O", 2)), parts)
    }

    // ---------------------------------------------------------------- parse (document 21)

    @Test
    fun parseReadsWhatPlainWrites() {
        assertEquals(
            listOf(MolecularFormula.Part("C", 10), MolecularFormula.Part("H", 16), MolecularFormula.Part("N", 5), MolecularFormula.Part("O", 13), MolecularFormula.Part("P", 3)),
            MolecularFormula.parse("C10H16N5O13P3"),
        )
        assertEquals(listOf(MolecularFormula.Part("K", 1)), MolecularFormula.parse("K"))
        assertEquals(listOf(MolecularFormula.Part("C", 34), MolecularFormula.Part("H", 32), MolecularFormula.Part("Fe", 1), MolecularFormula.Part("N", 4), MolecularFormula.Part("O", 4)), MolecularFormula.parse("C34H32FeN4O4"))
    }

    @Test
    fun parseRefusesAnythingPlainWouldNeverWrite() {
        for (bad in listOf("", "c10", "C1", "C0H2", "C10 H16", "Cl-", "10C", "CL2x", "Ç2"))
            assertEquals(bad, null, MolecularFormula.parse(bad))
    }
}
