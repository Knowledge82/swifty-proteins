package dev.knowledge82.swiftyproteins.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ElementStyleTest {

    // The 50 real elements that appear in the 1242 ligands of the corpus (document 07, section 4), as the parser writes them.
    private val corpusElements = listOf(
        "H", "C", "O", "N", "P", "S", "F", "Cl", "Br", "B", "Fe", "Mo", "Cu", "I", "Mg", "Ru", "Mn", "Pt", "V", "Co",
        "Se", "Ca", "As", "Eu", "Hg", "U", "W", "Cr", "Al", "Au", "Ba", "Be", "Cd", "Cs", "Ga", "K", "La", "Li", "Na",
        "Ni", "Pb", "Pd", "Rb", "Rh", "Sr", "Tb", "Tl", "Xe", "Yb", "Zn",
    )

    @Test
    fun theCorpusHas50ElementsAndEveryOneHasItsOwnColor() {
        assertEquals(50, corpusElements.toSet().size)
        for (e in corpusElements) {
            assertTrue("no color for $e", ElementStyle.isKnown(e))
            assertNotEquals("$e uses the unknown color", ElementStyle.UNKNOWN_COLOR, ElementStyle.colorOf(e))
        }
    }

    @Test
    fun theCommonElementsHaveTheCpkColors() {
        assertEquals(0xFFFFFF, ElementStyle.colorOf("H"))   // white
        assertEquals(0x909090, ElementStyle.colorOf("C"))   // grey
        assertEquals(0x3050F8, ElementStyle.colorOf("N"))   // blue
        assertEquals(0xFF0D0D, ElementStyle.colorOf("O"))   // red
        assertEquals(0xFFFF30, ElementStyle.colorOf("S"))   // yellow
        assertEquals(0xFF8000, ElementStyle.colorOf("P"))   // orange
    }

    @Test
    fun theFiveMostFrequentElementsCanBeToldApartByColor() {
        val five = listOf("H", "C", "O", "N", "S").map { ElementStyle.colorOf(it) }
        assertEquals(5, five.toSet().size)
    }

    @Test
    fun anUnknownElementAndThePlaceholderXGetTheUnknownColor() {
        assertEquals(ElementStyle.UNKNOWN_COLOR, ElementStyle.colorOf("Zz"))
        assertEquals(ElementStyle.UNKNOWN_COLOR, ElementStyle.colorOf("X"))
        assertEquals(ElementStyle.UNKNOWN_COLOR, ElementStyle.colorOf(""))
        assertFalse(ElementStyle.isKnown("X"))
    }

    @Test
    fun symbolsAreCaseSensitiveBecauseTheParserAlreadyNormalizesThem() {
        assertTrue(ElementStyle.isKnown("Cl"))
        assertFalse(ElementStyle.isKnown("CL"))
        assertFalse(ElementStyle.isKnown("cl"))
    }

    @Test
    fun colorsFitInThreeBytes() {
        for (e in corpusElements + "X") assertTrue(e, ElementStyle.colorOf(e) in 0..0xFFFFFF)
    }

    @Test
    fun hydrogenIsTheSmallestBallAndEveryRadiusIsReasonable() {
        val radii = (corpusElements + "X" + "Zz").map { it to ElementStyle.radiusOf(it) }
        for ((e, r) in radii) assertTrue("$e: $r", r in 0.2f..0.6f)
        assertEquals(0.25f, ElementStyle.radiusOf("H"), 0f)
        assertTrue(radii.filter { it.first != "H" }.all { it.second > ElementStyle.radiusOf("H") })
    }

    @Test
    fun aBallIsSmallerThanTheShortestBondSoTheSticksStayVisible() {
        // The shortest real bond of the corpus is O-H / N-H, about 0.86 A. Two hydrogens or a hydrogen and an
        // oxygen, side by side, must not swallow the stick between them.
        val shortest = 0.86f
        assertTrue(ElementStyle.radiusOf("H") + ElementStyle.radiusOf("O") < shortest + 0.1f)
        assertTrue(ElementStyle.radiusOf("H") + ElementStyle.radiusOf("N") < shortest + 0.1f)
    }

    @Test
    fun everyElementHasAReadableName() {
        assertEquals("Carbon", ElementStyle.nameOf("C"))
        assertEquals("Iron", ElementStyle.nameOf("Fe"))
        assertEquals("Unknown element", ElementStyle.nameOf("X"))
        for (e in corpusElements) assertNotEquals("$e has no name", e, ElementStyle.nameOf(e))
    }

    @Test
    fun anElementWithoutANameIsShownByItsSymbol() {
        assertEquals("Zz", ElementStyle.nameOf("Zz"))
    }
}
