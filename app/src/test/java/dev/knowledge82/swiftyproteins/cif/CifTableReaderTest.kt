package dev.knowledge82.swiftyproteins.cif

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class CifTableReaderTest {

    /** Text -> tokens -> block: the two layers together, as the real parser will use them. */
    private fun read(text: String): CifBlock = CifTableReader.read(CifTokenizer.tokenize(text))

    private fun failureOf(text: String): CifFailure =
        assertThrows(CifFormatException::class.java) { read(text) }.failure

    // ---------------------------------------------------------------- loop_

    @Test
    fun loopBecomesATableWithRows() {
        val block = read(
            """
            data_XXA
            loop_
            _a.x
            _a.y
            1 2
            3 4
            """.trimIndent(),
        )
        assertEquals("XXA", block.name)
        assertEquals(1, block.tables.size)
        val table = block.tables[0]
        assertEquals(listOf("_a.x", "_a.y"), table.columns)
        assertEquals(listOf(listOf("1", "2"), listOf("3", "4")), table.rows)
    }

    @Test
    fun rowsAreCutByCountingColumnsNotByLineBreaks() {
        // The same 2x2 table written on ONE line, and split in odd places: same result.
        val oneLine = read("data_X loop_ _a.x _a.y 1 2 3 4")
        val oddLines = read("data_X\nloop_\n_a.x _a.y 1\n2 3\n\n4")
        assertEquals(oneLine.tables[0].rows, oddLines.tables[0].rows)
    }

    @Test
    fun quotedValuesWithQuotesAndSpacesStayInsideTheirCell() {
        val block = read(
            """
            data_X
            loop_
            _a.name
            _a.type
            "O5'" O
            'C1 A' C
            """.trimIndent(),
        )
        assertEquals(listOf(listOf("O5'", "O"), listOf("C1 A", "C")), block.tables[0].rows)
    }

    @Test
    fun loopWithColumnsButNoValuesIsAnEmptyTable() {
        val table = read("data_X loop_ _a.x _a.y").tables[0]
        assertEquals(emptyList<List<String>>(), table.rows)
    }

    @Test
    fun twoLoopsInARowGiveTwoTables() {
        val block = read("data_X loop_ _a.x 1 2 loop_ _b.y 3")
        assertEquals(listOf(listOf("1"), listOf("2")), block.tables[0].rows)
        assertEquals(listOf(listOf("3")), block.tables[1].rows)
    }

    // ---------------------------------------------------------------- loose pairs

    @Test
    fun loosePairsOfOneCategoryBecomeOneRowTable() {
        val block = read("data_X _chem_comp.id XXA _chem_comp.type NON-POLYMER")
        assertEquals(1, block.tables.size)
        assertEquals(listOf("_chem_comp.id", "_chem_comp.type"), block.tables[0].columns)
        assertEquals(listOf(listOf("XXA", "NON-POLYMER")), block.tables[0].rows)
    }

    @Test
    fun pairsOfDifferentCategoriesGiveDifferentTables() {
        val block = read("data_X _a.p 1 _b.q 2 _a.r 3")
        assertEquals(2, block.tables.size)
        assertEquals(listOf(listOf("1", "3")), block.tableWith("_a.p")!!.rows)   // _a.p and _a.r together
        assertEquals(listOf(listOf("2")), block.tableWith("_b.q")!!.rows)
    }

    @Test
    fun aSingleAtomWrittenAsPairsLooksLikeATableToo() {
        // How an ion MIGHT be written (we have not seen a real text file of an ion yet).
        val block = read("data_CL _chem_comp_atom.comp_id CL _chem_comp_atom.atom_id CL _chem_comp_atom.type_symbol CL")
        val table = block.tableWith("_chem_comp_atom.atom_id")!!
        assertEquals(1, table.rows.size)
        assertEquals("CL", table.rows[0][table.indexOf("_chem_comp_atom.type_symbol")!!])
    }

    @Test
    fun multilineTextFieldIsAValueOfAPair() {
        val block = read("data_X\n_chem_comp.name\n;LINE ONE\nLINE TWO\n;\n_chem_comp.id 001")
        assertEquals(listOf(listOf("LINE ONE\nLINE TWO", "001")), block.tables[0].rows)
    }

    // ---------------------------------------------------------------- finding columns by name

    @Test
    fun columnsAreFoundByNameIgnoringCase() {
        val table = read("data_X loop_ _Chem_Comp_Atom.Atom_ID _chem_comp_atom.TYPE_SYMBOL C1 C").tables[0]
        assertEquals(0, table.indexOf("_chem_comp_atom.atom_id"))
        assertEquals(1, table.indexOf("_CHEM_COMP_ATOM.type_symbol"))
        assertNull(table.indexOf("_chem_comp_atom.charge"))
    }

    @Test
    fun columnOrderDoesNotMatter() {
        val a = read("data_X loop_ _a.x _a.y 1 2").tables[0]
        val b = read("data_X loop_ _a.y _a.x 2 1").tables[0]
        assertEquals(a.rows[0][a.indexOf("_a.x")!!], b.rows[0][b.indexOf("_a.x")!!])
    }

    @Test
    fun tableWithFindsTheRightTableAmongSeveral() {
        val block = read("data_X loop_ _a.x 1 loop_ _b.y 2 3")
        assertEquals(2, block.tableWith("_b.y")!!.rows.size)
        assertNull(block.tableWith("_c.z"))
    }

    // ---------------------------------------------------------------- blocks

    @Test
    fun onlyTheFirstDataBlockIsRead() {
        val block = read("data_ONE _a.x 1 data_TWO _a.x 2")
        assertEquals("ONE", block.name)
        assertEquals(listOf(listOf("1")), block.tables[0].rows)
    }

    // ---------------------------------------------------------------- malformed input

    @Test
    fun emptyTextHasNoDataBlock() {
        assertEquals(CifFailure.NoDataBlock, failureOf(""))
    }

    @Test
    fun anHtmlErrorPageHasNoDataBlock() {
        assertEquals(
            CifFailure.NoDataBlock,
            failureOf("<!DOCTYPE html><html><body>Not Found</body></html>"),
        )
    }

    @Test
    fun truncatedRowIsReportedWithTheFirstColumnAndLine() {
        // 3 columns, 5 values: the second row is incomplete.
        val failure = failureOf("data_X\nloop_\n_a.x\n_a.y\n_a.z\n1 2 3\n4 5")
        assertEquals(CifFailure.TruncatedRow(firstTag = "_a.x", line = 2), failure)
    }

    @Test
    fun loopWithoutColumnsFails() {
        assertEquals(CifFailure.LoopWithoutColumns(line = 2), failureOf("data_X\nloop_\n1 2 3"))
    }

    @Test
    fun tagWithoutValueFails() {
        assertEquals(CifFailure.MissingValue("_a.x", 1), failureOf("data_X _a.x _a.y 2"))
        assertEquals(CifFailure.MissingValue("_a.x", 1), failureOf("data_X _a.x"))
    }

    @Test
    fun valueWithoutTagFails() {
        assertEquals(CifFailure.StrayValue("oops", 2), failureOf("data_X\noops"))
    }

    // ---------------------------------------------------------------- a REAL file

    /** The structure of the real ligand 001; the numbers match the independent Python reference parser. */
    @Test
    fun realLigand001() {
        val text = javaClass.getResourceAsStream("/cif/001.cif")!!.bufferedReader().use { it.readText() }
        val block = read(text)
        assertEquals("001", block.name)
        assertEquals(7, block.tables.size)

        val atoms = block.tableWith("_chem_comp_atom.atom_id")!!
        assertEquals(18, atoms.columns.size)
        assertEquals(87, atoms.rows.size)
        val bonds = block.tableWith("_chem_comp_bond.atom_id_1")!!
        assertEquals(7, bonds.columns.size)
        assertEquals(90, bonds.rows.size)

        // first and last atom, read BY COLUMN NAME
        val name = atoms.indexOf("_chem_comp_atom.atom_id")!!
        val element = atoms.indexOf("_chem_comp_atom.type_symbol")!!
        val x = atoms.indexOf("_chem_comp_atom.model_Cartn_x")!!
        assertEquals("C01", atoms.rows.first()[name])
        assertEquals("C", atoms.rows.first()[element])
        assertEquals("26.108", atoms.rows.first()[x])
        assertEquals("H351", atoms.rows.last()[name])
        assertEquals("H", atoms.rows.last()[element])

        // one-row tables written as pairs
        val comp = block.tableWith("_chem_comp.id")!!
        assertEquals(24, comp.columns.size)
        assertEquals(1, comp.rows.size)
        assertEquals(1, block.tableWith("_pdbx_chem_comp_synonyms.name")!!.rows.size)

        // the multi-line ligand name is ONE cell, and the other loops did not break
        val ligandName = comp.rows[0][comp.indexOf("_chem_comp.name")!!]
        assertEquals(2, ligandName.lines().size)
        assertEquals(7, block.tableWith("_pdbx_chem_comp_descriptor.type")!!.rows.size)
        assertNotNull(block.tableWith("_pdbx_chem_comp_audit.action_type"))
    }
}
