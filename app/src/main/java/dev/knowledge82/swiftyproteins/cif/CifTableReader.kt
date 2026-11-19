package dev.knowledge82.swiftyproteins.cif

import dev.knowledge82.swiftyproteins.cif.CifTokenType.DATA
import dev.knowledge82.swiftyproteins.cif.CifTokenType.LOOP
import dev.knowledge82.swiftyproteins.cif.CifTokenType.TAG
import dev.knowledge82.swiftyproteins.cif.CifTokenType.VALUE

/**
 * Layer 2 of the parser: groups the tokens into tables. It knows the STRUCTURE of CIF (a `loop_` is a
 * table, loose `tag value` pairs are a one-row table) and still nothing about chemistry.
 */
object CifTableReader {

    /**
     * Reads the FIRST data block of the file. A second `data_` is ignored and so is everything after it.
     *
     * @throws CifFormatException if the file has no data block or the structure is broken.
     */
    fun read(tokens: List<CifToken>): CifBlock {
        val header = tokens.firstOrNull()
        if (header == null || header.type != DATA) throw CifFormatException(CifFailure.NoDataBlock)

        val tables = ArrayList<CifTable>()
        // Loose "tag value" pairs, grouped by category (the part before the dot):
        //   "_chem_comp.id", "_chem_comp.name"  -> category "_chem_comp"
        // LinkedHashMap keeps the order in which the categories appeared.
        val pairs = LinkedHashMap<String, MutableList<Pair<String, String>>>()

        var i = 1   // position in the token list; token 0 was the data_ line
        readLoop@ while (i < tokens.size) {
            val token = tokens[i]
            when (token.type) {
                DATA -> break@readLoop   // start of a second block: we do not need it

                LOOP -> {
                    // 1) the column names: the TAG tokens right after `loop_`
                    var j = i + 1
                    val columns = ArrayList<String>()
                    while (j < tokens.size && tokens[j].type == TAG) {
                        columns += tokens[j].text
                        j++
                    }
                    if (columns.isEmpty()) {
                        throw CifFormatException(CifFailure.LoopWithoutColumns(token.line))
                    }
                    // 2) the values: every VALUE token that follows, until something else appears
                    val values = ArrayList<String>()
                    while (j < tokens.size && tokens[j].type == VALUE) {
                        values += tokens[j].text
                        j++
                    }
                    // 3) rows are NOT separated by line breaks: we know where one ends by counting.
                    if (values.size % columns.size != 0) {
                        throw CifFormatException(CifFailure.TruncatedRow(columns[0], token.line))
                    }
                    tables += CifTable(columns, values.chunked(columns.size))
                    i = j
                }

                TAG -> {
                    // outside a loop, a tag must be followed by its value
                    val value = tokens.getOrNull(i + 1)
                    if (value == null || value.type != VALUE) {
                        throw CifFormatException(CifFailure.MissingValue(token.text, token.line))
                    }
                    val category = token.text.substringBefore('.').lowercase()
                    pairs.getOrPut(category) { ArrayList() } += token.text to value.text
                    i += 2
                }

                // a value with no tag before it and no loop around it: the file is malformed
                VALUE -> throw CifFormatException(CifFailure.StrayValue(token.text, token.line))
            }
        }

        // each category of loose pairs becomes a table with ONE row
        for ((_, list) in pairs) {
            tables += CifTable(list.map { it.first }, listOf(list.map { it.second }))
        }
        return CifBlock(header.text, tables)
    }
}
