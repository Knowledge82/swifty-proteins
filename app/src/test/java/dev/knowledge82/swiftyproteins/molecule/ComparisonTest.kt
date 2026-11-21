package dev.knowledge82.swiftyproteins.molecule

import dev.knowledge82.swiftyproteins.cif.CifParser
import dev.knowledge82.swiftyproteins.cif.ParseResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComparisonTest {

    // Small hand-made molecules: the numbers can be checked by eye.
    private fun atom(element: String, x: Float = 0f, charge: Int = 0) = Atom(element, element, x, 0f, 0f, charge)

    /** Water: O with two H, a chain (no ring). */
    private val water = Molecule("HOH", listOf(atom("O"), atom("H", 1f), atom("H", -1f)), listOf(Bond(0, 1, BondOrder.SINGLE), Bond(0, 2, BondOrder.SINGLE)))

    /** A triangle of three carbons (cyclopropane without its hydrogens): one ring. */
    private val ring = Molecule(
        "CPR",
        listOf(atom("C", 0f), atom("C", 1.5f), Atom("C", "C", 0.75f, 1.3f, 0f)),
        listOf(Bond(0, 1, BondOrder.SINGLE), Bond(1, 2, BondOrder.SINGLE), Bond(2, 0, BondOrder.SINGLE)),
    )

    /** A salt: Na+ and Cl-, two pieces, no bond. */
    private val salt = Molecule("NAC", listOf(atom("Na", 0f, +1), atom("Cl", 3f, -1)), emptyList())

    private val ligand001: Molecule by lazy { parse("/cif/001.cif") }

    private fun parse(path: String): Molecule {
        val parsed = CifParser.parse(javaClass.getResourceAsStream(path)!!.bufferedReader().use { it.readText() })
        return (parsed as ParseResult.Success).molecule
    }

    @Test
    fun ringsAreBondsMinusAtomsPlusPieces() {
        assertEquals(0, Comparison.rings(water))
        assertEquals(1, Comparison.rings(ring))
        assertEquals(2, Comparison.pieces(salt))
        assertEquals(0, Comparison.rings(salt))
        assertEquals(1, Comparison.pieces(water))
    }

    @Test
    fun theTableHasEveryLineInOrderAndSaysWhichIsLarger() {
        val rows = Comparison.rows(water, ring)
        assertEquals(listOf("Formula", "Mass", "Atoms", "Heavy atoms", "Bonds", "Rings", "Aromatic bonds", "Charge", "Span"), rows.map { it.label })
        val byLabel = rows.associateBy { it.label }
        assertEquals("H2O", byLabel["Formula"]!!.first)
        assertEquals("C3", byLabel["Formula"]!!.second)
        assertEquals("18.0 g/mol", byLabel["Mass"]!!.first)   // 15.999 + 2 x 1.008 = 18.015
        assertEquals("36.0 g/mol", byLabel["Mass"]!!.second)  // 3 x 12.011
        assertEquals(1, byLabel["Mass"]!!.larger)
        // Three atoms each: the same, nobody larger.
        assertTrue(byLabel["Atoms"]!!.same)
        assertEquals(0, byLabel["Atoms"]!!.larger)
        // Heavy atoms: water 1, the ring 3.
        assertEquals("1", byLabel["Heavy atoms"]!!.first)
        assertEquals(1, byLabel["Heavy atoms"]!!.larger)
        // Rings: the first has none, the second one.
        assertEquals(1, byLabel["Rings"]!!.larger)
        // Swapped, the larger one is on the other side.
        assertEquals(-1, Comparison.rows(ring, water).associateBy { it.label }["Rings"]!!.larger)
    }

    @Test
    fun chargesAndSpansAreWrittenPlainly() {
        assertEquals("+1", Comparison.charge(1))
        assertEquals("-2", Comparison.charge(-2))
        assertEquals("0", Comparison.charge(0))
        assertEquals(0, Comparison.netCharge(salt))
        assertEquals("2.0 Å", Comparison.angstrom(Comparison.span(water)))   // H at -1 and +1
        assertEquals("3.0 Å", Comparison.angstrom(Comparison.span(salt)))
        // The farthest pair is not next to each other in the list: every pair is measured.
        val line = Molecule("L3", listOf(atom("C", 0f), atom("C", 1f), atom("C", 4f)), emptyList())
        assertEquals("4.0 Å", Comparison.angstrom(Comparison.span(line)))
        // One atom: no distance at all.
        assertEquals(0f, Comparison.span(Molecule("K", listOf(atom("K")), emptyList())), 0f)
    }

    @Test
    fun elementsComeInHillOrderWithZeroWhereAbsent() {
        val rows = Comparison.elements(water, ring)
        assertEquals(listOf("C", "H", "O"), rows.map { it.element })
        assertEquals(Comparison.ElementRow("C", 0, 3), rows[0])
        assertEquals(Comparison.ElementRow("H", 2, 0), rows[1])
        // No carbon anywhere: plain alphabetical order (Cl before Na, H before O).
        assertEquals(listOf("Cl", "H", "Na", "O"), Comparison.elements(water, salt).map { it.element })
    }

    @Test
    fun theCompositionMatchIsSharedOverEither() {
        assertEquals(100, Comparison.compositionMatch(water, water))
        assertEquals(0, Comparison.compositionMatch(water, ring))
        // Water H2O and hydroxide-like "OH": shared O1 H1 = 2, either O1 H2 = 3 -> 67 %.
        val oh = Molecule("OH", listOf(atom("O"), atom("H", 1f)), listOf(Bond(0, 1, BondOrder.SINGLE)))
        assertEquals(67, Comparison.compositionMatch(water, oh))
        assertEquals(Comparison.compositionMatch(oh, water), Comparison.compositionMatch(water, oh))
        assertEquals("Composition match 67 %", Comparison.matchLine(water, oh))
    }

    @Test
    fun theRealLigand001IsOnePieceWithItsDictionaryMass() {
        // 001.cif from the catalogue (document 03): the CCD writes formula_weight 624.715 in the file itself.
        val m = ligand001
        assertEquals(1, Comparison.pieces(m))
        assertTrue(Comparison.rings(m) > 0)
        val mass = AtomicMass.molar(m.atoms)!!
        assertEquals(624.715, mass, 0.06)
        assertEquals("624.7 g/mol", Comparison.mass(mass))
    }

    @Test
    fun knownMassesAndAnUnknownElement() {
        // ATP, C10H16N5O13P3: 507.18 g/mol in every chemistry reference.
        val atpAtoms = MolecularFormula.parse("C10H16N5O13P3")!!.flatMap { p -> List(p.count) { atom(p.element) } }
        assertEquals(507.18, AtomicMass.molar(atpAtoms)!!, 0.01)
        // Caffeine, C8H10N4O2: 194.19 g/mol.
        val caffeine = MolecularFormula.parse("C8H10N4O2")!!.flatMap { p -> List(p.count) { atom(p.element) } }
        assertEquals(194.19, AtomicMass.molar(caffeine)!!, 0.01)
        // UNL has an element "X": no mass rather than a wrong one.
        assertNull(AtomicMass.molar(listOf(atom("C"), atom("X"))))
        assertEquals("?", Comparison.mass(null))
        assertEquals(50, AtomicMass.size)
    }

    @Test
    fun everyLigandWeighsWhatTheDictionarySays() {
        // The independent reference: the CCD's own formula_weight of each ligand, against our mass of the formula in the
        // app's index (the formula our parser counts, document 21). This pins EVERY element of the table: a wrong
        // weight moves the mass of the ligands that contain it. The CCD uses some older IUPAC values (Zn 65.409, Mo
        // 95.94): hence 0.065, and the mass shown with one decimal.
        val formula = java.io.File("src/main/assets/ligand_index.tsv").readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { it.split("\t") }.filter { it.size >= 3 }.associate { it[0] to it[2] }
        var compared = 0
        for (line in javaClass.getResourceAsStream("/ccd_formula_weight.tsv")!!.bufferedReader().readLines()) {
            if (line.startsWith("#") || line.isBlank()) continue
            val (id, weight) = line.split("\t")
            val parts = formula[id]?.let { MolecularFormula.parse(it) } ?: continue
            val atoms = parts.flatMap { p -> List(p.count) { atom(p.element) } }
            val mass = AtomicMass.molar(atoms) ?: continue   // an "X": no mass, tested above
            assertEquals(id, weight.toDouble(), mass, 0.065)
            compared++
        }
        assertTrue("compared $compared", compared >= 1240)
    }
}
