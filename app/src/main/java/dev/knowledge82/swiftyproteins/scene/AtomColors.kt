package dev.knowledge82.swiftyproteins.scene

/**
 * The colour schemes for the atoms (bonus VII.2 "settings", document 21). Both are "CPK" conventions (Corey, Pauling,
 * Koltun: oxygen red, nitrogen blue, carbon grey...), as the two classic viewers draw them:
 *
 * - [JMOL]: Jmol's table, the default. Light colours for every element up to uranium.
 * - [RASMOL]: RasMol's older table. Fewer colours: groups of elements share one (Ni, Cu, Zn and Br are all brown),
 *   and anything it does not list is DEEP PINK, as in RasMol itself.
 *
 * Both tables were checked on 2026-11-21: Jmol against 3Dmol.js `elementColors.Jmol`, RasMol against the UMass
 * "RasMol/Chime Color RGB Values" page (document 21, sources).
 */
enum class AtomColors(val label: String) {
    JMOL("CPK · Jmol"),
    RASMOL("CPK · RasMol");

    fun next(): AtomColors = entries[(ordinal + 1) % entries.size]
}
