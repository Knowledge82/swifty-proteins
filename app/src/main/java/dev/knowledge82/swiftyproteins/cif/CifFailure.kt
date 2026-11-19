package dev.knowledge82.swiftyproteins.cif

/** Why a file could not be understood. New reasons are added here as the parser grows. */
sealed interface CifFailure {
    // ---- layer 1: tokenizer ------------------------------------------------------------------

    /** A quote was opened and the line ended before it was closed. */
    data class UnterminatedQuote(val line: Int) : CifFailure

    /** A `;` text field was opened and the file ended before the closing `;`. */
    data class UnterminatedTextField(val line: Int) : CifFailure

    // ---- layer 2: table reader ---------------------------------------------------------------

    /** The file does not start with `data_NAME`. An HTML error page or an empty body ends up here. */
    data object NoDataBlock : CifFailure

    /** A `loop_` with no column names after it. */
    data class LoopWithoutColumns(val line: Int) : CifFailure

    /**
     * The values of a `loop_` do not fill whole rows (e.g. 29 values for 10 columns).
     * @param firstTag the first column of the loop, to tell which table is broken.
     */
    data class TruncatedRow(val firstTag: String, val line: Int) : CifFailure

    /** A data name that should be followed by a value is followed by something else, or by the end. */
    data class MissingValue(val tag: String, val line: Int) : CifFailure

    /** A value that belongs to no data name and to no loop. */
    data class StrayValue(val value: String, val line: Int) : CifFailure

    // ---- layer 3: extractor ------------------------------------------------------------------

    /** A column we cannot live without is not in the file (or there is no atom table at all). */
    data class MissingColumn(val tag: String) : CifFailure

    /** The atom table exists but has no rows. */
    data object NoAtoms : CifFailure

    /** Neither the ideal nor the model coordinates are complete for every atom (see decision D10). */
    data object NoCompleteCoordinates : CifFailure

    /** The element of an atom is empty, `?`, `.` or not made of letters. */
    data class BadElement(val atom: String, val element: String) : CifFailure

    /** Two atoms of the same ligand have the same name, so a bond could not tell them apart. */
    data class DuplicateAtomName(val name: String) : CifFailure

    /** A bond names an atom that is not in the atom table. */
    data class UnknownAtomInBond(val name: String) : CifFailure

    /** A bond joins an atom with itself. */
    data class BondToItself(val name: String) : CifFailure

    // ---- entry point: CifParser ----------------------------------------------------------------

    /** Something is bigger than the limit we accept: the text, the atoms or the bonds. */
    data class TooBig(val what: String, val count: Int, val max: Int) : CifFailure

    /** Safety net: an exception nobody expected. It means a bug in the parser, not a bad file. */
    data class Unexpected(val description: String) : CifFailure
}

/**
 * The only exception the parser layers throw. It carries a typed [failure], not just a message.
 *
 * It is thrown INSIDE the parser's layers and caught at its public entry point, which turns it into a
 * result value. The rest of the app never sees this exception.
 */
class CifFormatException(val failure: CifFailure) : Exception(failure.toString())
