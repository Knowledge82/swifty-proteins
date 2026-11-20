package dev.knowledge82.swiftyproteins.ui

import java.util.Locale

/**
 * The small status texts of the ligand list (the green line under the title and the footer). Pure Kotlin, tested.
 *
 * @param available `false` when the list file could not be read (catalog is null)
 * @param total     how many ligands the catalog has
 * @param query     what the user typed
 * @param shown     how many rows the search returned
 */
object ListTexts {

    fun status(available: Boolean, total: Int, query: String, shown: Int): String = when {
        !available -> "CATALOG UNAVAILABLE"
        query.isBlank() -> plural(total, "ENTRY", "ENTRIES")
        else -> plural(shown, "RESULT", "RESULTS")
    }

    fun footer(available: Boolean, total: Int, query: String, shown: Int): String = when {
        !available -> "CATALOG ERROR // LIST NOT READABLE"
        total == 0 -> "CATALOG EMPTY"
        query.isBlank() -> "CATALOG READY // ${count(total)} INDEXED LIGANDS"
        shown == 0 -> "SEARCH COMPLETE // NO MATCH"
        else -> "FILTER LIVE // CASE INSENSITIVE"
    }

    /** 1243 -> "1,243" always (Locale.US): the design shows a comma, whatever the phone's language. */
    private fun count(n: Int): String = String.format(Locale.US, "%,d", n)

    private fun plural(n: Int, one: String, many: String) = "${count(n)} ${if (n == 1) one else many}"
}
