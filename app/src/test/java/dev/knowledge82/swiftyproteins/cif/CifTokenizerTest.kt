package dev.knowledge82.swiftyproteins.cif

import dev.knowledge82.swiftyproteins.cif.CifTokenType.DATA
import dev.knowledge82.swiftyproteins.cif.CifTokenType.LOOP
import dev.knowledge82.swiftyproteins.cif.CifTokenType.TAG
import dev.knowledge82.swiftyproteins.cif.CifTokenType.VALUE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CifTokenizerTest {

    /** Shortcut: tokenizes and keeps only (type, text), which is what most tests compare. */
    private fun tokens(text: String): List<Pair<CifTokenType, String>> =
        CifTokenizer.tokenize(text).map { it.type to it.text }

    private fun values(text: String): List<String> =
        CifTokenizer.tokenize(text).filter { it.type == VALUE }.map { it.text }

    // ---------------------------------------------------------------- the basics

    @Test
    fun emptyTextGivesNoTokens() {
        assertEquals(emptyList<CifToken>(), CifTokenizer.tokenize(""))
        assertEquals(emptyList<CifToken>(), CifTokenizer.tokenize("  \n\t \r\n "))
    }

    @Test
    fun tagAndValue() {
        assertEquals(
            listOf(TAG to "_chem_comp.id", VALUE to "001"),
            tokens("_chem_comp.id   001"),
        )
    }

    @Test
    fun loopWithColumnsAndRows() {
        val text = """
            data_XXA
            loop_
            _a.x
            _a.y
            1 2
            3 4
        """.trimIndent()
        assertEquals(
            listOf(
                DATA to "XXA",
                LOOP to "loop_",
                TAG to "_a.x", TAG to "_a.y",
                VALUE to "1", VALUE to "2", VALUE to "3", VALUE to "4",
            ),
            tokens(text),
        )
    }

    @Test
    fun keywordsIgnoreCaseButValuesKeepIt() {
        assertEquals(
            listOf(DATA to "Abc", LOOP to "LOOP_", TAG to "_A.B", VALUE to "Cl"),
            tokens("DATA_Abc LOOP_ _A.B Cl"),
        )
    }

    // ---------------------------------------------------------------- quotes

    @Test
    fun doubleQuotesKeepTheSingleQuoteInside() {
        // The line from a real nucleotide: the atom is called O5' (with a quote).
        assertEquals(listOf("XXA", "O5'", "O"), values("""XXA "O5'" O"""))
    }

    @Test
    fun quoteOnlyClosesWhenFollowedByWhitespace() {
        assertEquals(listOf("a'b"), values("'a'b'"))
        assertEquals(listOf("a", "b"), values("'a' b"))
        assertEquals(listOf("say \"hi\" now"), values("'say \"hi\" now'"))
    }

    @Test
    fun quotedValueCanContainSpacesAndHashes() {
        assertEquals(listOf("C35 H42 F2 N2 O6"), values("\"C35 H42 F2 N2 O6\""))
        assertEquals(listOf("not # a comment"), values("'not # a comment'"))
    }

    @Test
    fun emptyQuotedString() {
        assertEquals(listOf("", "x"), values("'' x"))
    }

    @Test
    fun quoteInTheMiddleOfAWordIsJustACharacter() {
        assertEquals(listOf("O5'"), values("O5'"))
    }

    @Test
    fun unterminatedQuoteFailsWithItsLine() {
        val e = assertThrows(CifFormatException::class.java) {
            CifTokenizer.tokenize("_a.b 1\n_a.c 'never closed\n_a.d 2")
        }
        assertEquals(CifFailure.UnterminatedQuote(line = 2), e.failure)
    }

    @Test
    fun quoteAtTheVeryEndOfTheTextWithoutClosingFails() {
        assertThrows(CifFormatException::class.java) { CifTokenizer.tokenize("'abc") }
    }

    // ---------------------------------------------------------------- comments

    @Test
    fun commentsAreSkipped() {
        assertEquals(
            listOf(TAG to "_a.b", VALUE to "1"),
            tokens("# first\n_a.b 1 # trailing\n#   #\n"),
        )
    }

    @Test
    fun hashInTheMiddleOfAWordIsPartOfTheWord() {
        assertEquals(listOf("C1#x"), values("C1#x"))
    }

    // ---------------------------------------------------------------- ; text fields

    @Test
    fun multilineTextFieldIsOneValue() {
        // Same shape as the ligand name in the real 001 file.
        val text = "_chem_comp.name\n;LINE ONE\nLINE TWO\n;\n_chem_comp.type NON-POLYMER\n"
        assertEquals(
            listOf(
                TAG to "_chem_comp.name",
                VALUE to "LINE ONE\nLINE TWO",
                TAG to "_chem_comp.type",
                VALUE to "NON-POLYMER",
            ),
            tokens(text),
        )
    }

    @Test
    fun textFieldWithTheOpeningSemicolonAloneOnItsLine() {
        assertEquals(listOf("hello"), values("_a.b\n;\nhello\n;\n"))
    }

    @Test
    fun semicolonNotInColumnOneIsNotATextField() {
        assertEquals(listOf("a;b", ";x"), values("a;b ;x"))
        assertEquals(listOf(";x"), values("   ;x"))
    }

    @Test
    fun linesAreCountedInsideTextFields() {
        val result = CifTokenizer.tokenize("_a.b\n;one\ntwo\n;\n_a.c 5")
        assertEquals(1, result[0].line)
        assertEquals(2, result[1].line)   // the text field starts on line 2
        assertEquals(5, result[2].line)   // _a.c is on line 5
    }

    @Test
    fun unterminatedTextFieldFailsWithItsLine() {
        val e = assertThrows(CifFormatException::class.java) {
            CifTokenizer.tokenize("_a.b 1\n_a.c\n;never closed\nmore text")
        }
        assertEquals(CifFailure.UnterminatedTextField(line = 3), e.failure)
    }

    // ---------------------------------------------------------------- line endings, BOM

    @Test
    fun windowsLineEndingsGiveTheSameTokensAsUnix() {
        val unix = "loop_\n_a.x\n_a.y\n'a b' \"c'd\"\n;multi\nline\n;\n"
        val windows = unix.replace("\n", "\r\n")
        assertEquals(tokens(unix), tokens(windows))
    }

    @Test
    fun byteOrderMarkAtTheStartIsIgnored() {
        assertEquals(listOf(DATA to "X"), tokens("\uFEFFdata_X"))
    }

    // ---------------------------------------------------------------- a REAL file

    /**
     * The real ligand 001 downloaded from RCSB (87 atoms, 90 bonds). The expected numbers come from
     * an independent tokenizer written in Python for the first analysis of the data, not from this
     * Kotlin code, so a bug that repeats in both is unlikely.
     */
    @Test
    fun realLigand001() {
        val text = javaClass.getResourceAsStream("/cif/001.cif")!!.bufferedReader().use { it.readText() }
        val result = CifTokenizer.tokenize(text)
        assertEquals(1, result.count { it.type == DATA })
        assertEquals(EXPECTED_001_LOOPS, result.count { it.type == LOOP })
        assertEquals(EXPECTED_001_TAGS, result.count { it.type == TAG })
        assertEquals(EXPECTED_001_VALUES, result.count { it.type == VALUE })

        // The multiline name, with its newline inside, arrives as ONE value.
        val name = result.first { it.type == VALUE && it.text.startsWith("1-[2,2-DIFLUORO") }
        assertEquals(2, name.text.lines().size)

        // The atom loop declares 18 columns and has 87 rows: the values after its last tag must be
        // exactly 87 * 18. If a quote or a field were mis-tokenized, this number would be off.
        val atomTags = result.count { it.type == TAG && it.text.startsWith("_chem_comp_atom.") }
        assertEquals(18, atomTags)
        val lastAtomTag = result.indexOfFirst { it.text == "_chem_comp_atom.pdbx_ordinal" }
        val atomValues = result.drop(lastAtomTag + 1).takeWhile { it.type == VALUE }
        assertEquals(87 * 18, atomValues.size)
    }

    private companion object {
        // Counted with the Python reference tokenizer (cif_stats.py) on the same file.
        const val EXPECTED_001_LOOPS = 5
        const val EXPECTED_001_TAGS = 68
        const val EXPECTED_001_VALUES = 2282
    }
}
