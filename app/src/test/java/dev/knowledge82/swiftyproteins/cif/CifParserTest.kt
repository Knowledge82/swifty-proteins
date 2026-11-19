package dev.knowledge82.swiftyproteins.cif

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class CifParserTest {

    private val real001: String by lazy {
        javaClass.getResourceAsStream("/cif/001.cif")!!.bufferedReader().use { it.readText() }
    }

    private fun failureOf(text: String): CifFailure {
        val result = CifParser.parse(text)
        assertTrue("expected a Failure but got $result", result is ParseResult.Failure)
        return (result as ParseResult.Failure).failure
    }

    // ---------------------------------------------------------------- success

    @Test
    fun realLigand001ParsesFromTextToMolecule() {
        val result = CifParser.parse(real001)
        assertTrue("got $result", result is ParseResult.Success)
        val molecule = (result as ParseResult.Success).molecule
        assertEquals("001", molecule.id)
        assertEquals(87, molecule.atoms.size)
        assertEquals(90, molecule.bonds.size)
    }

    @Test
    fun windowsLineEndingsAndBomGiveTheSameMolecule() {
        val plain = CifParser.parse(real001)
        val windows = CifParser.parse("﻿" + real001.replace("\n", "\r\n"))
        assertTrue(plain is ParseResult.Success)
        assertEquals(plain, windows)   // ParseResult and Molecule are data classes: compared by content
    }

    // ---------------------------------------------------------------- failures come back as values

    @Test
    fun emptyTextIsNoDataBlock() {
        assertEquals(CifFailure.NoDataBlock, failureOf(""))
    }

    @Test
    fun anHtmlErrorPageIsNotAMolecule() {
        val html = "<!DOCTYPE html><html><body><h1>Service unavailable</h1></body></html>"
        assertEquals(CifFailure.NoDataBlock, failureOf(html))
    }

    @Test
    fun aBlockWithoutAtomsIsAFailureNotACrash() {
        val failure = failureOf("data_X\n_chem_comp.id X\n_chem_comp.name 'something'\n")
        assertEquals(CifFailure.MissingColumn("_chem_comp_atom.atom_id"), failure)
    }

    @Test
    fun anOversizedBodyIsRejectedBeforeAnyWork() {
        val huge = "a".repeat(CifParser.MAX_INPUT_CHARS + 1)
        assertEquals(
            CifFailure.TooBig("characters", CifParser.MAX_INPUT_CHARS + 1, CifParser.MAX_INPUT_CHARS),
            failureOf(huge),
        )
    }

    @Test
    fun aBodyOfExactlyTheLimitIsProcessedNormally() {
        // Not a CIF, so it fails, but for a reason of the parser and not for its size.
        val atLimit = "a".repeat(CifParser.MAX_INPUT_CHARS)
        assertEquals(CifFailure.NoDataBlock, failureOf(atLimit))
    }

    @Test
    fun theLayersFailuresArriveThroughTheDoor() {
        assertEquals(CifFailure.UnterminatedQuote(2), failureOf("data_X\n_a.b 'oops\n"))
        assertEquals(CifFailure.UnterminatedTextField(2), failureOf("data_X\n;never closed\n"))
        assertEquals(CifFailure.StrayValue("lost", 2), failureOf("data_X\nlost\n"))
        assertEquals(CifFailure.MissingValue("_a.b", 2), failureOf("data_X\n_a.b\n"))
    }

    // ---------------------------------------------------------------- it must NEVER throw

    @Test
    fun truncatingTheRealFileAtEveryPositionNeverThrows() {
        // A download cut anywhere. Each cut must give Success or Failure; any exception fails the test.
        var successes = 0
        var failures = 0
        for (cut in real001.indices step 7) {
            when (CifParser.parse(real001.substring(0, cut))) {
                is ParseResult.Success -> successes++
                is ParseResult.Failure -> failures++
            }
        }
        assertTrue("some cut should fail", failures > 0)
        assertTrue("expected at least one failure per truncation kind, got $failures failures", failures > 100)
        println("truncations: $successes success, $failures failure")
    }

    @Test
    fun randomDamageToTheRealFileNeverThrows() {
        val random = Random(42)   // fixed seed: the same "random" damage on every run
        val alphabet = "'\"; #_\n\r\t.?-0aZ"
        repeat(3000) {
            val chars = StringBuilder(real001)
            repeat(1 + random.nextInt(5)) {
                val at = random.nextInt(chars.length)
                when (random.nextInt(3)) {
                    0 -> chars.deleteCharAt(at)
                    1 -> chars.insert(at, alphabet[random.nextInt(alphabet.length)])
                    else -> chars.setCharAt(at, alphabet[random.nextInt(alphabet.length)])
                }
            }
            val result = CifParser.parse(chars.toString())
            // The safety net must NOT be what saves us: Unexpected would mean a bug in a layer.
            if (result is ParseResult.Failure) {
                assertTrue("a layer threw something unexpected: $result", result.failure !is CifFailure.Unexpected)
            }
        }
    }

    @Test
    fun randomGarbageNeverThrows() {
        val random = Random(7)
        val alphabet = "data_loop_;'\"#_. \n\r\t0123456789abcXYZ?-+eE"
        repeat(3000) {
            val text = buildString { repeat(random.nextInt(300)) { append(alphabet[random.nextInt(alphabet.length)]) } }
            val result = CifParser.parse(text)
            if (result is ParseResult.Failure) {
                assertTrue("unexpected failure on '$text': $result", result.failure !is CifFailure.Unexpected)
            }
        }
    }
}
