package dev.knowledge82.swiftyproteins.scene

import dev.knowledge82.swiftyproteins.molecule.Molecule

/**
 * Which elements the colour key above the atom card shows: the ones DRAWN right now, most frequent first.
 * Pure Kotlin, tested.
 */
object ElementLegend {

    /** At most this many keys fit in one row on a phone held upright. */
    const val MAX_KEYS = 6

    /**
     * @param showHydrogens when `false`, H is not drawn, so it is not in the key either
     * @return element symbols, by number of atoms (descending) and then alphabetically, at most [MAX_KEYS]
     */
    fun of(molecule: Molecule, showHydrogens: Boolean): List<String> =
        molecule.atoms
            .asSequence()
            .map { it.element }
            .filter { showHydrogens || it != "H" }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(MAX_KEYS)
            .map { it.key }
}
