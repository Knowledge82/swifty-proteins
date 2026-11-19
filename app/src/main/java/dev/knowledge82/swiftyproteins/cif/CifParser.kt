package dev.knowledge82.swiftyproteins.cif

import dev.knowledge82.swiftyproteins.molecule.Molecule

/** What [CifParser.parse] returns: a molecule, or the reason why there is none. Never an exception. */
sealed interface ParseResult {
    data class Success(val molecule: Molecule) : ParseResult
    data class Failure(val failure: CifFailure) : ParseResult
}

/**
 * The public door of the parser. The rest of the app calls THIS and nothing else of the `cif` package.
 *
 * Pure Kotlin, no `android.*`. It is CPU work (no network), so the caller runs it off the main thread.
 */
object CifParser {

    /** Real ligand files are well under 100 KB. A body above this limit is not a ligand. */
    const val MAX_INPUT_CHARS = 2_000_000

    /** Never throws because of its input: every problem comes back as [ParseResult.Failure]. */
    fun parse(text: String): ParseResult {
        if (text.length > MAX_INPUT_CHARS) {
            return ParseResult.Failure(CifFailure.TooBig("characters", text.length, MAX_INPUT_CHARS))
        }
        return try {
            val tokens = CifTokenizer.tokenize(text)         // layer 1: syntax
            val block = CifTableReader.read(tokens)          // layer 2: structure
            ParseResult.Success(CifExtractor.extract(block)) // layer 3: chemistry
        } catch (e: CifFormatException) {
            ParseResult.Failure(e.failure)                   // the expected way to fail
        } catch (e: Exception) {
            // A bug of ours (or something nobody foresaw). The app must not die in front of the evaluator:
            // it becomes a failure we can log. Errors such as OutOfMemoryError are NOT caught on purpose.
            ParseResult.Failure(CifFailure.Unexpected(e.toString()))
        }
    }
}
