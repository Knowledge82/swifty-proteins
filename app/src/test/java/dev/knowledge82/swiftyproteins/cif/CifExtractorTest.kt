package dev.knowledge82.swiftyproteins.cif

import dev.knowledge82.swiftyproteins.molecule.Bond
import dev.knowledge82.swiftyproteins.molecule.BondOrder
import dev.knowledge82.swiftyproteins.molecule.Molecule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class CifExtractorTest {

    // ---------------------------------------------------------------- helpers

    private val atomTags = listOf(
        "atom_id", "type_symbol",
        "model_Cartn_x", "model_Cartn_y", "model_Cartn_z",
        "pdbx_model_Cartn_x_ideal", "pdbx_model_Cartn_y_ideal", "pdbx_model_Cartn_z_ideal",
    )

    /**
     * Builds the text of a small ligand. Each atom row has: name, element, model xyz, ideal xyz
     * (that order, unless [columns] says otherwise). [bonds] rows are: atom1 atom2 order.
     */
    private fun ligand(
        atoms: List<String>,
        bonds: List<String>? = null,
        columns: List<String> = atomTags,
        name: String = "XXA",
    ): String = buildString {
        appendLine("data_$name")
        appendLine("loop_")
        columns.forEach { appendLine("_chem_comp_atom.$it") }
        atoms.forEach { appendLine(it) }
        if (bonds != null) {
            appendLine("loop_")
            appendLine("_chem_comp_bond.atom_id_1")
            appendLine("_chem_comp_bond.atom_id_2")
            appendLine("_chem_comp_bond.value_order")
            bonds.forEach { appendLine(it) }
        }
    }

    private fun extract(text: String): Molecule =
        CifExtractor.extract(CifTableReader.read(CifTokenizer.tokenize(text)))

    private fun failureOf(text: String): CifFailure =
        assertThrows(CifFormatException::class.java) { extract(text) }.failure

    // Two atoms, ideal coordinates (0,0,0) and (2,0,0); model ones far away on purpose.
    private val twoAtoms = listOf(
        "C1 C  100 100 100   0 0 0",
        "O1 O  103 100 100   2 0 0",
    )

    // ---------------------------------------------------------------- the happy path

    @Test
    fun smallMoleculeWithItsBond() {
        val m = extract(ligand(twoAtoms, bonds = listOf("C1 O1 DOUB")))
        assertEquals("XXA", m.id)
        assertEquals(listOf("C1", "O1"), m.atoms.map { it.name })
        assertEquals(listOf("C", "O"), m.atoms.map { it.element })
        assertEquals(listOf(Bond(0, 1, BondOrder.DOUBLE)), m.bonds)
    }

    @Test
    fun theMoleculeIdIsTheNameOfTheDataBlock() {
        assertEquals("ATP", extract(ligand(twoAtoms, name = "ATP")).id)
    }

    @Test
    fun columnOrderInTheFileDoesNotMatter() {
        // Same data with the columns in another order: the result must be identical.
        val reordered = listOf(
            "type_symbol", "pdbx_model_Cartn_z_ideal", "atom_id",
            "pdbx_model_Cartn_x_ideal", "model_Cartn_x", "pdbx_model_Cartn_y_ideal",
            "model_Cartn_y", "model_Cartn_z",
        )
        val rows = listOf(
            "C 0 C1 0 100 0 100 100",
            "O 0 O1 2 103 0 100 100",
        )
        val bonds = listOf("C1 O1 SING")
        assertEquals(
            extract(ligand(twoAtoms, bonds)),
            extract(ligand(rows, bonds, columns = reordered)),
        )
    }

    @Test
    fun anAtomNameWithAQuoteLinksItsBond() {
        // The nucleotides have names like O5'. The bond must find them by the name WITHOUT quotes.
        val atoms = listOf(
            "\"O5'\" O  0 0 0  0 0 0",
            "\"C5'\" C  0 0 0  1.4 0 0",
        )
        val m = extract(ligand(atoms, bonds = listOf("\"O5'\" \"C5'\" SING")))
        assertEquals(listOf("O5'", "C5'"), m.atoms.map { it.name })
        assertEquals(listOf(Bond(0, 1, BondOrder.SINGLE)), m.bonds)
    }

    // ---------------------------------------------------------------- elements

    @Test
    fun elementsAreNormalizedToFirstCapitalThenLowerCase() {
        val atoms = listOf(
            "A1 CL 0 0 0  0 0 0",
            "A2 FE 0 0 0  1 0 0",
            "A3 c  0 0 0  2 0 0",
            "A4 mG 0 0 0  3 0 0",
            "A5 C  0 0 0  4 0 0",
        )
        assertEquals(listOf("Cl", "Fe", "C", "Mg", "C"), extract(ligand(atoms)).atoms.map { it.element })
    }

    @Test
    fun anElementThatCannotBeASymbolFailsAndNamesTheAtom() {
        for (bad in listOf("?", ".", "C1", "''", "Carbono")) {
            val failure = failureOf(ligand(listOf("X9 $bad 0 0 0  0 0 0")))
            assertEquals("element '$bad'", CifFailure.BadElement("X9", if (bad == "''") "" else bad), failure)
        }
    }

    // ---------------------------------------------------------------- the choice of coordinates (D10)

    @Test
    fun idealCoordinatesAreUsedWhenAllAtomsHaveThem() {
        val m = extract(ligand(twoAtoms))
        // ideal x = 0 and 2, centroid 1 -> -1 and +1. (Model coordinates would give -1.5 and +1.5.)
        assertEquals(-1f, m.atoms[0].x, 1e-6f)
        assertEquals(1f, m.atoms[1].x, 1e-6f)
    }

    @Test
    fun modelCoordinatesAreUsedWhenAnIdealOneIsMissing() {
        val atoms = listOf(
            "C1 C  100 100 100   0 0 0",
            "O1 O  103 100 100   ? ? ?",     // one atom without ideal coordinates
        )
        val m = extract(ligand(atoms))
        assertEquals(-1.5f, m.atoms[0].x, 1e-6f)   // model: 100 and 103, centroid 101.5
        assertEquals(1.5f, m.atoms[1].x, 1e-6f)
    }

    @Test
    fun theTwoSetsAreNeverMixed() {
        // Atom 1 has a valid ideal x, atom 2 does not. A per-atom mix would take ideal x=0 for atom 1
        // and model x=103 for atom 2, giving a distance of 103 Å. The whole model set must be used.
        val atoms = listOf(
            "C1 C  100 100 100   0 0 0",
            "O1 O  103 100 100   2 ? 0",
        )
        val m = extract(ligand(atoms))
        assertEquals(3f, m.atoms[1].x - m.atoms[0].x, 1e-5f)
    }

    @Test
    fun anIdealSetWithAnAbsurdBondFallsBackToModel() {
        // Like HEM in the real data: the ideal set puts a hydrogen 20 Å away from the atom it is bonded to,
        // while the model set is fine. The believable set must win even though the ideal one is complete.
        val atoms = listOf(
            "C1 C  0 0 0     0 0 0",
            "H1 H  1.0 0 0   20 0 0",
        )
        val m = extract(ligand(atoms, bonds = listOf("C1 H1 SING")))
        assertEquals(1f, m.atoms[1].x - m.atoms[0].x, 1e-5f)   // model: 1 Å, not the 20 Å of the ideal set
    }

    @Test
    fun ifNoCompleteSetIsBelievableTheFirstCompleteOneIsStillUsed() {
        val atoms = listOf(
            "C1 C  0 0 0    0 0 0",
            "H1 H  30 0 0   20 0 0",
        )
        val m = extract(ligand(atoms, bonds = listOf("C1 H1 SING")))
        assertEquals(20f, m.atoms[1].x - m.atoms[0].x, 1e-4f)   // ideal: the first complete one, no failure
    }

    @Test
    fun aLongButPossibleBondDoesNotDiscreditTheIdealSet() {
        // 2.9 Å (a metal bond, say) is below the 5 Å limit: the ideal set stays.
        val atoms = listOf(
            "C1 C   0 0 0     0 0 0",
            "FE Fe  1.5 0 0   2.9 0 0",
        )
        val m = extract(ligand(atoms, bonds = listOf("C1 FE SING")))
        assertEquals(2.9f, m.atoms[1].x - m.atoms[0].x, 1e-5f)
    }

    @Test
    fun withoutBondsNothingCanBeDoubted() {
        // No bonds, so there is no bond length to judge: the ideal set is kept even if the atoms are far apart.
        val atoms = listOf(
            "C1 C  0 0 0    0 0 0",
            "C2 C  1.5 0 0  20 0 0",
        )
        assertEquals(20f, extract(ligand(atoms)).let { it.atoms[1].x - it.atoms[0].x }, 1e-4f)
    }

    @Test
    fun anAbsurdButCompleteIdealSetBeatsAnIncompleteModelSet() {
        val atoms = listOf(
            "C1 C  0 0 0   0 0 0",
            "H1 H  ? 0 0   20 0 0",
        )
        val m = extract(ligand(atoms, bonds = listOf("C1 H1 SING")))
        assertEquals(20f, m.atoms[1].x - m.atoms[0].x, 1e-4f)
    }

    @Test
    fun idealColumnsAbsentFromTheFileFallBackToModel() {
        val columns = listOf("atom_id", "type_symbol", "model_Cartn_x", "model_Cartn_y", "model_Cartn_z")
        val m = extract(ligand(listOf("C1 C 0 0 0", "O1 O 2 0 0"), columns = columns))
        assertEquals(2f, m.atoms[1].x - m.atoms[0].x, 1e-6f)
    }

    @Test
    fun noCompleteSetIsAFailure() {
        val atoms = listOf(
            "C1 C  100 100 100   0 0 0",
            "O1 O  ? 100 100     ? ? ?",
        )
        assertEquals(CifFailure.NoCompleteCoordinates, failureOf(ligand(atoms)))
    }

    @Test
    fun noCoordinateColumnsAtAllIsAFailure() {
        val failure = failureOf(ligand(listOf("C1 C", "O1 O"), columns = listOf("atom_id", "type_symbol")))
        assertEquals(CifFailure.NoCompleteCoordinates, failure)
    }

    @Test
    fun valuesThatAreNotPlainNumbersCountAsMissing() {
        val garbage = listOf(
            "?", ".", "abc", "NaN", "Infinity", "-Infinity", "1.5f", "1.5d", "0x10",
            "1,5", "1e999", "1e5", "--1", "''",
        )
        val columns = listOf("atom_id", "type_symbol", "model_Cartn_x", "model_Cartn_y", "model_Cartn_z")
        for (bad in garbage) {
            val failure = failureOf(ligand(listOf("C1 C 0 0 0", "O1 O $bad 0 0"), columns = columns))
            assertEquals("coordinate '$bad'", CifFailure.NoCompleteCoordinates, failure)
        }
    }

    @Test
    fun ordinaryNumberNotationsAreAccepted() {
        val columns = listOf("atom_id", "type_symbol", "model_Cartn_x", "model_Cartn_y", "model_Cartn_z")
        val m = extract(ligand(listOf("C1 C -3.053 +1.0 1e-3", "O1 O 0 0 0"), columns = columns))
        assertEquals(2, m.atoms.size)
    }

    // ---------------------------------------------------------------- centering

    @Test
    fun theMoleculeIsCenteredOnItsCentroid() {
        // Far from the origin, like the real ideal coordinates of some ligands (up to 226 Å away).
        val atoms = listOf(
            "A C  0 0 0  200 -50 10",
            "B C  0 0 0  202 -50 10",
            "C C  0 0 0  201 -47 13",
        )
        val m = extract(ligand(atoms))
        assertEquals(0.0, m.atoms.sumOf { it.x.toDouble() } / 3, 1e-5)
        assertEquals(0.0, m.atoms.sumOf { it.y.toDouble() } / 3, 1e-5)
        assertEquals(0.0, m.atoms.sumOf { it.z.toDouble() } / 3, 1e-5)
        // and distances do not change: A-B was 2 Å
        assertEquals(2f, m.atoms[1].x - m.atoms[0].x, 1e-5f)
    }

    // ---------------------------------------------------------------- single atoms and bond tables

    @Test
    fun aSingleAtomWrittenAsLooseTagValuePairs() {
        // How an ion may be written: no loop_, each tag followed by its value.
        val text = """
            data_NA
            _chem_comp_atom.atom_id                  NA
            _chem_comp_atom.type_symbol              NA
            _chem_comp_atom.model_Cartn_x            5.5
            _chem_comp_atom.model_Cartn_y            6.5
            _chem_comp_atom.model_Cartn_z            7.5
            _chem_comp_atom.pdbx_model_Cartn_x_ideal 0.0
            _chem_comp_atom.pdbx_model_Cartn_y_ideal 0.0
            _chem_comp_atom.pdbx_model_Cartn_z_ideal 0.0
        """.trimIndent()
        val m = extract(text)
        assertEquals(1, m.atoms.size)
        assertEquals("Na", m.atoms[0].element)
        assertEquals(0f, m.atoms[0].x, 1e-6f)    // a lone atom ends up exactly at the origin
        assertEquals(emptyList<Bond>(), m.bonds)
    }

    @Test
    fun noBondTableMeansNoBonds() {
        assertEquals(emptyList<Bond>(), extract(ligand(twoAtoms)).bonds)
    }

    @Test
    fun aBondTableWithColumnsButNoRowsMeansNoBonds() {
        assertEquals(emptyList<Bond>(), extract(ligand(twoAtoms, bonds = emptyList())).bonds)
    }

    // ---------------------------------------------------------------- bond orders

    @Test
    fun bondOrdersAreTranslated() {
        val atoms = (1..6).map { "A$it C 0 0 0  $it 0 0" }
        val bonds = listOf(
            "A1 A2 SING", "A2 A3 DOUB", "A3 A4 TRIP", "A4 A5 AROM", "A5 A6 QUAD", "A1 A3 doub",
        )
        val orders = extract(ligand(atoms, bonds)).bonds.map { it.order }
        assertEquals(
            listOf(
                BondOrder.SINGLE, BondOrder.DOUBLE, BondOrder.TRIPLE,
                BondOrder.AROMATIC, BondOrder.OTHER, BondOrder.DOUBLE,
            ),
            orders,
        )
    }

    @Test
    fun withoutAValueOrderColumnEveryBondIsOther() {
        val text = ligand(twoAtoms) + """
            loop_
            _chem_comp_bond.atom_id_1
            _chem_comp_bond.atom_id_2
            C1 O1
        """.trimIndent()
        assertEquals(listOf(BondOrder.OTHER), extract(text).bonds.map { it.order })
    }

    // ---------------------------------------------------------------- broken input

    @Test
    fun noAtomTableFails() {
        val failure = failureOf("data_X\n_chem_comp.id X\n")
        assertEquals(CifFailure.MissingColumn("_chem_comp_atom.atom_id"), failure)
    }

    @Test
    fun missingElementColumnFailsAndNamesIt() {
        val failure = failureOf(ligand(listOf("C1 0 0 0", "O1 2 0 0"),
            columns = listOf("atom_id", "model_Cartn_x", "model_Cartn_y", "model_Cartn_z")))
        assertEquals(CifFailure.MissingColumn("_chem_comp_atom.type_symbol"), failure)
    }

    @Test
    fun atomTableWithoutRowsFails() {
        assertEquals(CifFailure.NoAtoms, failureOf(ligand(emptyList())))
    }

    @Test
    fun duplicateAtomNamesFail() {
        val failure = failureOf(ligand(listOf("C1 C 0 0 0  0 0 0", "C1 C 0 0 0  1 0 0")))
        assertEquals(CifFailure.DuplicateAtomName("C1"), failure)
    }

    @Test
    fun aBondToAnUnknownAtomFails() {
        assertEquals(
            CifFailure.UnknownAtomInBond("ZZ"),
            failureOf(ligand(twoAtoms, bonds = listOf("C1 ZZ SING"))),
        )
        assertEquals(
            CifFailure.UnknownAtomInBond("ZZ"),
            failureOf(ligand(twoAtoms, bonds = listOf("ZZ C1 SING"))),
        )
    }

    @Test
    fun atomNamesAreCaseSensitive() {
        assertEquals(
            CifFailure.UnknownAtomInBond("c1"),
            failureOf(ligand(twoAtoms, bonds = listOf("c1 O1 SING"))),
        )
    }

    @Test
    fun aBondFromAnAtomToItselfFails() {
        assertEquals(
            CifFailure.BondToItself("C1"),
            failureOf(ligand(twoAtoms, bonds = listOf("C1 C1 SING"))),
        )
    }

    @Test
    fun aBondTableMissingItsSecondColumnFails() {
        val text = ligand(twoAtoms) + "loop_\n_chem_comp_bond.atom_id_1\nC1\n"
        assertEquals(CifFailure.MissingColumn("_chem_comp_bond.atom_id_2"), failureOf(text))
    }

    // ---------------------------------------------------------------- limits

    @Test
    fun tooManyAtomsFails() {
        val atoms = (0..CifExtractor.MAX_ATOMS).map { "A$it C 0 0 0  $it 0 0" }
        assertEquals(
            CifFailure.TooBig("atoms", CifExtractor.MAX_ATOMS + 1, CifExtractor.MAX_ATOMS),
            failureOf(ligand(atoms)),
        )
    }

    @Test
    fun exactlyTheMaximumNumberOfAtomsIsAccepted() {
        val atoms = (1..CifExtractor.MAX_ATOMS).map { "A$it C 0 0 0  $it 0 0" }
        assertEquals(CifExtractor.MAX_ATOMS, extract(ligand(atoms)).atoms.size)
    }

    // ---------------------------------------------------------------- a REAL file

    @Test
    fun realLigand001() {
        val text = javaClass.getResourceAsStream("/cif/001.cif")!!.bufferedReader().use { it.readText() }
        val m = extract(text)

        assertEquals("001", m.id)
        assertEquals(87, m.atoms.size)
        assertEquals(90, m.bonds.size)
        assertEquals("C01", m.atoms.first().name)
        assertEquals("C", m.atoms.first().element)
        assertEquals("H351", m.atoms.last().name)
        assertEquals("H", m.atoms.last().element)

        // IDEAL coordinates were chosen (the file has both sets and both are complete). In the file the first
        // two atoms are 0.484 / 0.579 apart in ideal x, and 26.108 / 25.498 in model x: -0.095 vs +0.610.
        assertEquals(0.484f - 0.579f, m.atoms[0].x - m.atoms[1].x, 2e-3f)
        assertNotEquals(26.108f - 25.498f, m.atoms[0].x - m.atoms[1].x, 0.1f)

        // Centered.
        assertEquals(0.0, m.atoms.sumOf { it.x.toDouble() } / m.atoms.size, 1e-4)
        assertEquals(0.0, m.atoms.sumOf { it.y.toDouble() } / m.atoms.size, 1e-4)
        assertEquals(0.0, m.atoms.sumOf { it.z.toDouble() } / m.atoms.size, 1e-4)

        // Every bond must join atoms that are really next to each other. If the name->index translation
        // were wrong, some bond would join two distant atoms. 0.8-2.3 Å covers C-H up to C-S/C-Cl.
        for (bond in m.bonds) {
            val a = m.atoms[bond.from]
            val b = m.atoms[bond.to]
            val d = sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y) + (a.z - b.z) * (a.z - b.z))
            assertTrue("bond ${a.name}-${b.name} has length $d", d in 0.8f..2.3f)
        }
    }
}
