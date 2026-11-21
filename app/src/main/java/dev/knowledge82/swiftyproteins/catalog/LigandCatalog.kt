package dev.knowledge82.swiftyproteins.catalog

/**
 * The list of ligands the app offers (the content of `ligands.txt`) and the search over it.
 *
 * Pure Kotlin: it receives the TEXT of the file, so it knows nothing about Android, assets or screens.
 * Reading the file from the assets is somebody else's job; this class only turns text into a list and filters it.
 */
class LigandCatalog private constructor(
    /** Every ligand id, in the order of the file, without blanks or repetitions. */
    val ids: List<String>,
) {

    /**
     * The ids that match what the user typed, for showing the list while he types.
     *
     * - Case does not matter: "atp", "ATP" and "aTp" are the same search.
     * - Spaces at the ends are ignored.
     * - An id matches if it CONTAINS the text; the ones that START with it come first, then the rest.
     * - Only the ID is searched (document 23). The search by chemical name (document 21) was removed together with
     *   the name in the cells: a row that matched by a name nobody could see looked like a wrong result.
     * - Inside each group the order of the file is kept.
     * - Empty text (or only spaces) matches everything.
     */
    fun search(query: String): List<String> {
        val wanted = query.trim()
        if (wanted.isEmpty()) return ids

        // ignoreCase compares letter by letter without depending on the language of the phone
        // (with "i".uppercase() in Turkish you would get "İ", and the search would break).
        val (startingWith, containing) = ids
            .filter { it.contains(wanted, ignoreCase = true) }
            .partition { it.startsWith(wanted, ignoreCase = true) }
        return startingWith + containing
    }

    companion object {

        /** Builds the catalog from the text of `ligands.txt`: one id per line. */
        fun fromText(text: String): LigandCatalog {
            val ids = text.lineSequence()      // splits on \n and also on \r\n
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()                    // keeps the first appearance of each id
                .toList()
            return LigandCatalog(ids)
        }
    }
}
