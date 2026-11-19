package dev.knowledge82.swiftyproteins.cif

/** The four kinds of piece a CIF file is cut into. */
enum class CifTokenType {
    /** The word `loop_`: a table is about to start. */
    LOOP,

    /** `data_NAME`: start of a data block. [CifToken.text] holds NAME, without the `data_` prefix. */
    DATA,

    /** A data name, e.g. `_chem_comp_atom.atom_id`. It always starts with an underscore. */
    TAG,

    /** Any value: a bare word, a quoted string (quotes removed) or a `;` text field. */
    VALUE,
}

/**
 * One piece of the file.
 *
 * @param line 1-based line where the token starts. It exists only to make error messages useful.
 */
data class CifToken(
    val type: CifTokenType,
    val text: String,
    val line: Int,
)
