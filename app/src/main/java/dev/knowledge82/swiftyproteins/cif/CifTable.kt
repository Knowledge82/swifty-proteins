package dev.knowledge82.swiftyproteins.cif

/**
 * A table of the file: named columns and rows of text.
 *
 * It comes from a `loop_` (many rows) or from data names written one by one (a single row). From the
 * outside both look the same, which is the point of this class.
 *
 * Every value is still TEXT here. Turning "26.108" into a Float is the job of the next layer, which
 * knows what each column means.
 */
class CifTable(rawColumns: List<String>, val rows: List<List<String>>) {

    /** Full column names in lower case, e.g. `_chem_comp_atom.atom_id`. CIF names ignore case. */
    val columns: List<String> = rawColumns.map { it.lowercase() }

    // column name -> position. If a name were repeated, the first one wins.
    private val positions: Map<String, Int> = buildMap {
        columns.forEachIndexed { position, name -> putIfAbsent(name, position) }
    }

    /** Position of a column, or null if this table does not have it. Ignores upper/lower case. */
    fun indexOf(tag: String): Int? = positions[tag.lowercase()]
}

/** What the file says: its name (`001`) and its tables. */
class CifBlock(val name: String, val tables: List<CifTable>) {

    /** The table that has this column, or null. Columns are found by NAME, never by position. */
    fun tableWith(tag: String): CifTable? = tables.firstOrNull { it.indexOf(tag) != null }
}
