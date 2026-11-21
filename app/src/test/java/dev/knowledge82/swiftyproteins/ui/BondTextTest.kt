package dev.knowledge82.swiftyproteins.ui

import dev.knowledge82.swiftyproteins.molecule.Atom
import dev.knowledge82.swiftyproteins.molecule.Bond
import dev.knowledge82.swiftyproteins.molecule.BondOrder
import dev.knowledge82.swiftyproteins.molecule.Molecule
import org.junit.Assert.assertEquals
import org.junit.Test

class BondTextTest {

    // A carbonyl: C1 = O2 at 1.23 A along x; and a 3-4-5 triangle to check the length in 3D.
    private val m = Molecule(
        "TST",
        listOf(Atom("C1", "C", 0f, 0f, 0f), Atom("O2", "O", 1.23f, 0f, 0f), Atom("N3", "N", 3f, 4f, 0f), Atom("CL4", "Cl", 0f, 3f, 4f)),
        listOf(Bond(0, 1, BondOrder.DOUBLE), Bond(0, 2, BondOrder.SINGLE), Bond(0, 3, BondOrder.OTHER)),
    )

    @Test
    fun theTitleNamesBothAtomsAndTheOrder() {
        assertEquals("C1 – O2 · Double bond", BondText.title(m, 0))
        assertEquals("C1 – N3 · Single bond", BondText.title(m, 1))
    }

    @Test
    fun theElementsAreWrittenInWords() {
        assertEquals("Carbon – Oxygen", BondText.elements(m, 0))
        assertEquals("Carbon – Chlorine", BondText.elements(m, 2))
    }

    @Test
    fun theLengthIsMeasuredInThreeDimensions() {
        assertEquals("1.230 Å", BondText.length(m, 0))
        assertEquals("5.000 Å", BondText.length(m, 1))   // 3-4-5 in the xy plane
        assertEquals("5.000 Å", BondText.length(m, 2))   // 3-4-5 in the yz plane
    }

    @Test
    fun everyOrderHasItsOwnWordsAndAnUnknownOneIsNotGuessed() {
        val names = BondOrder.entries.map(BondText::orderName)
        assertEquals(BondOrder.entries.size, names.toSet().size)
        assertEquals("Other bond", BondText.orderName(BondOrder.OTHER))
        assertEquals("Aromatic bond", BondText.orderName(BondOrder.AROMATIC))
        assertEquals("Triple bond", BondText.orderName(BondOrder.TRIPLE))
    }
}
