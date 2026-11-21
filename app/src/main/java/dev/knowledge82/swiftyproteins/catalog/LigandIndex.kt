package dev.knowledge82.swiftyproteins.catalog

/**
 * What the list knows about each ligand WITHOUT downloading it (bonus VII.2 "custom cells", document 21): its name,
 * its Hill formula and its number of atoms, from `assets/ligand_index.tsv`. Pure, tested.
 *
 * The file is made by `tools/index/BuildLigandIndex.kt` with the app's OWN parser and formula, so a cell never says
 * something different from what the molecule screen shows after loading.
 *
 * Reading is FORGIVING, line by line: a damaged line is skipped, the rest are kept. The index is a nicety; the list
 * works without it (cells then show only the id, as before).
 */
class LigandIndex private constructor(private val entries: Map<String, Entry>) {

    data class Entry(val id: String, val name: String, val formula: String, val atoms: Int)

    operator fun get(id: String): Entry? = entries[id]

    val size: Int get() = entries.size

    /** The name of [id], or null (no entry, or an empty name). */
    fun nameOf(id: String): String? = entries[id]?.name?.takeIf { it.isNotEmpty() }

    companion object {
        val EMPTY = LigandIndex(emptyMap())

        fun fromText(text: String): LigandIndex = LigandIndex(
            text.lineSequence()
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .mapNotNull { line ->
                    val f = line.split('\t')
                    if (f.size != 4) return@mapNotNull null
                    val id = f[0].trim()
                    val atoms = f[3].trim().toIntOrNull() ?: return@mapNotNull null
                    if (id.isEmpty() || atoms < 0) null else Entry(id, f[1].trim(), f[2].trim(), atoms)
                }
                .associateBy { it.id },
        )
    }
}
