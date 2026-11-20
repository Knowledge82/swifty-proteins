package dev.knowledge82.swiftyproteins.ui

import java.util.Locale

/** The status texts of the Data-Vault Index (design v2, screens 06-09). Pure Kotlin, tested. */
object ListTexts {

    /** The tab on the archive panel: "ARCHIVE ACCESS / 1243" (no thousands separator, as in the design). */
    fun archiveLabel(available: Boolean, total: Int): String =
        if (available) "ARCHIVE ACCESS / $total" else "ARCHIVE ACCESS / ERROR"

    /**
     * The green line above the rows: the whole catalog when nothing is typed, the matches otherwise.
     *
     * @param available `false` when the list file could not be read (catalog is null)
     */
    fun patternsFound(available: Boolean, total: Int, query: String, shown: Int): String = when {
        !available -> "ARCHIVE UNREADABLE"
        query.isBlank() -> "PATTERNS FOUND: ${count(total)}"
        else -> "PATTERNS FOUND: ${count(shown)}"
    }

    /** "No pattern matches 'xyz'." (the query trimmed, so spaces typed around it do not show). */
    fun noMatch(query: String): String = "No pattern matches '${query.trim()}'."

    /** 1243 -> "1,243" always (Locale.US), whatever the phone's language. */
    private fun count(n: Int): String = String.format(Locale.US, "%,d", n)
}
