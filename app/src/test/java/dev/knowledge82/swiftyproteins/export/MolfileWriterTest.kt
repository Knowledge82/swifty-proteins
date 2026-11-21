package dev.knowledge82.swiftyproteins.export

import dev.knowledge82.swiftyproteins.molecule.Atom
import dev.knowledge82.swiftyproteins.molecule.Bond
import dev.knowledge82.swiftyproteins.molecule.BondOrder
import dev.knowledge82.swiftyproteins.molecule.Molecule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MolfileWriterTest {

    // Acetate: CH3-COO(-), with the charge on one oxygen. Heavy atoms only, enough for the format.
    private val acetate = Molecule(
        "ACT",
        listOf(
            Atom("C1", "C", 0f, 0f, 0f), Atom("C2", "C", 1.52f, 0f, 0f),
            Atom("O1", "O", 2.15f, 1.07f, 0f), Atom("O2", "O", 2.15f, -1.07f, 0f, charge = -1),
        ),
        listOf(Bond(0, 1, BondOrder.SINGLE), Bond(1, 2, BondOrder.DOUBLE), Bond(1, 3, BondOrder.SINGLE)),
        name = "ACETATE ION",
    )
    private val lines = MolfileWriter.write(acetate).lines()

    @Test
    fun theHeaderHasNameProgramWith3DAndComment() {
        assertEquals("ACT", lines[0])
        assertEquals("3D", lines[1].substring(20, 22))   // columns 21-22: dimension code
        assertEquals("ACETATE ION", lines[2])
    }

    @Test
    fun theCountsLineIsFixedWidth() {
        assertEquals("  4  3  0  0  0  0  0  0  0  0999 V2000", lines[3])
    }

    @Test
    fun atomLinesHaveCoordinatesInTenColumnsThenTheSymbol() {
        val o2 = lines[4 + 3]
        assertEquals("    2.1500   -1.0700    0.0000 O   0  5  0  0  0  0  0  0  0  0  0  0", o2)   // 5 = charge -1 (old code)
        assertEquals("C  ", lines[4].substring(31, 34))
        assertEquals(69, lines[4].length)
    }

    @Test
    fun bondLinesAreOneBasedWithTheirType() {
        assertEquals("  1  2  1  0  0  0  0", lines[8])
        assertEquals("  2  3  2  0  0  0  0", lines[9])
        assertEquals("  2  4  1  0  0  0  0", lines[10])
    }

    @Test
    fun chargesGoInTheMChgLineAndTheFileEnds() {
        assertEquals("M  CHG  1   4  -1", lines[11])
        assertEquals("M  END", lines[12])
    }

    @Test
    fun moreThanEightChargesTakeSeveralLines() {
        val ions = Molecule("ION", List(10) { Atom("N$it", "N", it.toFloat(), 0f, 0f, charge = 1) }, emptyList())
        val chg = MolfileWriter.write(ions).lines().filter { it.startsWith("M  CHG") }
        assertEquals(2, chg.size)
        assertTrue(chg[0].startsWith("M  CHG  8"))
        assertTrue(chg[1].startsWith("M  CHG  2"))
    }

    @Test
    fun everyOrderAndChargeHasItsCode() {
        assertEquals(listOf(1, 2, 3, 4, 8), BondOrder.entries.map(MolfileWriter::typeOf))
        assertEquals(listOf(1, 2, 3, 0, 5, 6, 7), listOf(3, 2, 1, 0, -1, -2, -3).map(MolfileWriter::chargeCode))
        assertEquals(0, MolfileWriter.chargeCode(4))   // only in M  CHG
    }

    @Test
    fun anUnknownElementIsAStarAndANameCannotBreakTheLayout() {
        val m = Molecule("X1", listOf(Atom("Q", "X", 0f, 0f, 0f)), emptyList(), name = "TWO\nLINES")
        val l = MolfileWriter.write(m).lines()
        assertEquals("TWO LINES", l[2])
        assertEquals("*  ", l[4].substring(31, 34))
    }

    @Test
    fun tooBigForV2000IsRefused() {
        val big = Molecule("BIG", List(1_000) { Atom("C$it", "C", 0f, 0f, it.toFloat()) }, emptyList())
        assertThrows(IllegalArgumentException::class.java) { MolfileWriter.write(big) }
    }
}
