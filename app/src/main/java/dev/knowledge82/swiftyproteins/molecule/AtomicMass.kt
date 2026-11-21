package dev.knowledge82.swiftyproteins.molecule

/**
 * Standard atomic weights (IUPAC, abridged to 5 significant figures, the conventional values for the elements whose
 * weight is given as an interval: H 1.008, C 12.011, ...), for the 50 elements of the ligand catalogue. Pure, tested.
 *
 * Checked on 2026-11-22 against the Chemical Component Dictionary: the mass of each of the 1241 ligands that have
 * atoms, computed with this table, against the CCD's own `_chem_comp.formula_weight`. All agree within 0.06 g/mol;
 * the largest gaps come from older IUPAC values in the CCD (Zn 65.409 instead of 65.38, Mo 95.94 instead of 95.95).
 * That is why the mass is shown with ONE decimal (document 24).
 */
object AtomicMass {

    private val WEIGHTS = mapOf(
        "H" to 1.008, "Li" to 6.94, "Be" to 9.0122, "B" to 10.81, "C" to 12.011, "N" to 14.007, "O" to 15.999,
        "F" to 18.998, "Na" to 22.990, "Mg" to 24.305, "Al" to 26.982, "P" to 30.974, "S" to 32.06, "Cl" to 35.45,
        "K" to 39.098, "Ca" to 40.078, "V" to 50.942, "Cr" to 51.996, "Mn" to 54.938, "Fe" to 55.845, "Co" to 58.933,
        "Ni" to 58.693, "Cu" to 63.546, "Zn" to 65.38, "Ga" to 69.723, "As" to 74.922, "Se" to 78.971, "Br" to 79.904,
        "Rb" to 85.468, "Sr" to 87.62, "Mo" to 95.95, "Ru" to 101.07, "Rh" to 102.91, "Pd" to 106.42, "Cd" to 112.41,
        "I" to 126.90, "Xe" to 131.29, "Cs" to 132.91, "Ba" to 137.33, "La" to 138.91, "Eu" to 151.96, "Tb" to 158.93,
        "Yb" to 173.05, "W" to 183.84, "Pt" to 195.08, "Au" to 196.97, "Hg" to 200.59, "Tl" to 204.38, "Pb" to 207.2,
        "U" to 238.03,
    )

    /** The weight of one atom of [element] in g/mol, or null for an element the table does not know. */
    fun of(element: String): Double? = WEIGHTS[element]

    /** How many elements the table knows. */
    val size: Int get() = WEIGHTS.size

    /**
     * The molar mass of [atoms] in g/mol: the sum of their weights. Null if ANY element is unknown: a mass that
     * silently left an atom out would be a wrong number shown as a right one.
     */
    fun molar(atoms: List<Atom>): Double? {
        var sum = 0.0
        for (atom in atoms) sum += of(atom.element) ?: return null
        return sum
    }
}
