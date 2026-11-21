package dev.knowledge82.swiftyproteins.ui

import dev.knowledge82.swiftyproteins.molecule.MolecularFormula
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
    fun patternsFound(available: Boolean, total: Int, query: String, shown: Int, onlyFavorites: Boolean = false): String = when {
        !available -> "ARCHIVE UNREADABLE"
        onlyFavorites -> "FAVOURITES FOUND: ${count(shown)}"
        query.isBlank() -> "PATTERNS FOUND: ${count(total)}"
        else -> "PATTERNS FOUND: ${count(shown)}"
    }

    /** "No pattern matches 'xyz'." (the query trimmed, so spaces typed around it do not show). */
    fun noMatch(query: String): String = "No pattern matches '${query.trim()}'."

    /** The empty list with "favourites only" on (document 16): none at all yet, or none matching the search. */
    fun noFavorites(query: String): String =
        if (query.isBlank()) "No favourites yet. Tap the star of a ligand to keep it here." else "No favourite matches '${query.trim()}'."

    /** "47 ATOMS", "1 ATOM" (a cell of the list, document 21). */
    fun atoms(n: Int): String = if (n == 1) "1 ATOM" else "${count(n)} ATOMS"

    /**
     * What TalkBack reads for a cell (document 21): everything the eye gets from it, in one sentence. The formula is
     * spaced ("C10 H16 N5") so it is read as parts, not as one strange word.
     */
    fun cellDescription(id: String, name: String?, formula: String?, atoms: Int?, archived: Boolean, action: String = "Inspect"): String = buildString {
        append("Ligand ").append(id)
        if (!name.isNullOrBlank()) append(", ").append(name)
        val parts = formula?.let { MolecularFormula.parse(it) }
        if (parts != null) append(", formula ").append(parts.joinToString(" ") { if (it.count == 1) it.element else "${it.element} ${it.count}" })
        if (atoms != null) append(", ").append(atoms(atoms).lowercase())
        if (archived) append(", saved on the phone")
        append(". ").append(action)
    }

    /**
     * The composition bar of a cell (document 21): each element's SHARE of the atoms, in formula order, summing to 1.
     * A small picture of the molecule's make-up before it is even downloaded: mostly carbon and hydrogen, or a metal ion.
     */
    fun composition(parts: List<MolecularFormula.Part>): List<Pair<String, Float>> {
        val total = parts.sumOf { it.count }
        if (total <= 0) return emptyList()
        return parts.map { it.element to it.count.toFloat() / total }
    }

    /** 1243 -> "1,243" always (Locale.US), whatever the phone's language. */
    private fun count(n: Int): String = String.format(Locale.US, "%,d", n)
}
