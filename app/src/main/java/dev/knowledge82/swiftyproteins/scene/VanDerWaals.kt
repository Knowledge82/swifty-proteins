package dev.knowledge82.swiftyproteins.scene

/**
 * Van der Waals radii, in angstrom: how big an atom "is" when nothing is bonded to it, the size the SPACE-FILLING
 * model draws (bonus VII.1, document 15).
 *
 * Source: Mantina, Chamberlin, Valero, Cramer and Truhlar, "Consistent van der Waals Radii for the Whole Main
 * Group", J. Phys. Chem. A 113 (2009), table 12, DOI 10.1021/jp8111556 (transcribed from the copy in MolSSI's
 * QCElemental, `mantina_2009_vanderwaals_radii.py`). They extend Bondi (1964) to the whole main group; hydrogen is
 * 1.10 (Rowland and Taylor) instead of Bondi's 1.20.
 *
 * The table has NO transition metals, lanthanides or actinides (Fe, Zn, Cu, Mo, W, Pt, U...): there is no
 * consistent set for them. Those get [FALLBACK], OUR choice, of the order of their main-group neighbours.
 */
object VanDerWaals {

    const val FALLBACK = 2.0f

    private val radii: Map<String, Float> = mapOf(
        "H" to 1.10f, "He" to 1.40f, "Li" to 1.81f, "Be" to 1.53f, "B" to 1.92f, "C" to 1.70f, "N" to 1.55f,
        "O" to 1.52f, "F" to 1.47f, "Ne" to 1.54f, "Na" to 2.27f, "Mg" to 1.73f, "Al" to 1.84f, "Si" to 2.10f,
        "P" to 1.80f, "S" to 1.80f, "Cl" to 1.75f, "Ar" to 1.88f, "K" to 2.75f, "Ca" to 2.31f, "Ga" to 1.87f,
        "Ge" to 2.11f, "As" to 1.85f, "Se" to 1.90f, "Br" to 1.83f, "Kr" to 2.02f, "Rb" to 3.03f, "Sr" to 2.49f,
        "In" to 1.93f, "Sn" to 2.17f, "Sb" to 2.06f, "Te" to 2.06f, "I" to 1.98f, "Xe" to 2.16f, "Cs" to 3.43f,
        "Ba" to 2.68f, "Tl" to 1.96f, "Pb" to 2.02f, "Bi" to 2.07f, "Po" to 1.97f, "At" to 2.02f, "Rn" to 2.20f,
        "Fr" to 3.48f, "Ra" to 2.83f,
    )

    fun radiusOf(element: String): Float = radii[element] ?: FALLBACK

    /** Is the radius from the table (true) or the fallback (false)? */
    fun isTabulated(element: String): Boolean = element in radii
}
