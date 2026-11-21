package dev.knowledge82.swiftyproteins.scene

/**
 * How each chemical element is drawn: its COLOR, the RADIUS of its ball and its English NAME.
 *
 * Pure Kotlin, no Android: the 3D scene asks this object and does not decide anything itself, so the rules can be
 * tested on the JVM.
 *
 * The keys are the symbols AS THE PARSER NORMALIZES THEM: first letter upper case, the rest lower case ("Cl", "Fe",
 * never "CL"). The lookup is case sensitive on purpose.
 *
 * **Colors** come in two schemes ([AtomColors], document 21). The default is the table that Jmol (a well-known
 * molecule viewer) calls "CPK": hydrogen white, carbon grey, nitrogen blue, oxygen red, sulfur yellow, phosphorus
 * orange, halogens green, and so on. First written from memory; on 2026-11-21 every value below was compared with
 * 3Dmol.js's copy of the Jmol table and all 54 matched. The second scheme is RasMol's ([rasmol]).
 */
object ElementStyle {

    /** Used for any symbol that is not in the table (and for "X", the placeholder of unknown atoms): bright pink, so it stands out. */
    const val UNKNOWN_COLOR = 0xFF1493

    private val colors: Map<String, Int> = mapOf(
        "H" to 0xFFFFFF, "He" to 0xD9FFFF, "Li" to 0xCC80FF, "Be" to 0xC2FF00, "B" to 0xFFB5B5,
        "C" to 0x909090, "N" to 0x3050F8, "O" to 0xFF0D0D, "F" to 0x90E050, "Ne" to 0xB3E3F5,
        "Na" to 0xAB5CF2, "Mg" to 0x8AFF00, "Al" to 0xBFA6A6, "Si" to 0xF0C8A0, "P" to 0xFF8000,
        "S" to 0xFFFF30, "Cl" to 0x1FF01F, "Ar" to 0x80D1E3, "K" to 0x8F40D4, "Ca" to 0x3DFF00,
        "V" to 0xA6A6AB, "Cr" to 0x8A99C7, "Mn" to 0x9C7AC7, "Fe" to 0xE06633, "Co" to 0xF090A0,
        "Ni" to 0x50D050, "Cu" to 0xC88033, "Zn" to 0x7D80B0, "Ga" to 0xC28F8F, "As" to 0xBD80E3,
        "Se" to 0xFFA100, "Br" to 0xA62929, "Rb" to 0x702EB0, "Sr" to 0x00FF00, "Mo" to 0x54B5B5,
        "Ru" to 0x248F8F, "Rh" to 0x0A7D8C, "Pd" to 0x006985, "Cd" to 0xFFD98F, "I" to 0x940094,
        "Xe" to 0x429EB0, "Cs" to 0x57178F, "Ba" to 0x00C900, "La" to 0x70D4FF, "Eu" to 0x61FFC7,
        "Tb" to 0x30FFC7, "Yb" to 0x00BF38, "W" to 0x2194D6, "Pt" to 0xD0D0E0, "Au" to 0xFFD123,
        "Hg" to 0xB8B8D0, "Tl" to 0xA6544D, "Pb" to 0x575961, "U" to 0x008FFF,
    )

    /**
     * RasMol's "cpk" scheme (UMass "RasMol/Chime Color RGB Values"): sixteen named colours shared by groups of
     * elements. Elements it does not list are deep pink in RasMol, which is our [UNKNOWN_COLOR] too.
     */
    private val rasmol: Map<String, Int> = buildMap {
        fun all(color: Int, vararg elements: String) = elements.forEach { put(it, color) }
        all(0xC8C8C8, "C")                                       // light grey
        all(0xF00000, "O")                                       // red
        all(0xFFFFFF, "H")                                       // white
        all(0x8F8FFF, "N")                                       // light blue
        all(0xFFC832, "S")                                       // sulphur yellow
        all(0x00FF00, "Cl", "B")                                 // green
        all(0xFFA500, "P", "Fe", "Ba")                           // orange
        all(0x0000FF, "Na")                                      // blue
        all(0x228B22, "Mg")                                      // forest green
        all(0xA52A2A, "Zn", "Cu", "Ni", "Br")                    // brown
        all(0x808090, "Ca", "Mn", "Al", "Ti", "Cr", "Ag")        // dark grey
        all(0xDAA520, "F", "Si", "Au")                           // goldenrod
        all(0xA020F0, "I")                                       // purple
        all(0xB22222, "Li")                                      // firebrick
        all(0xFFC0CB, "He")                                      // pink
    }

    private val names: Map<String, String> = mapOf(
        "H" to "Hydrogen", "He" to "Helium", "Li" to "Lithium", "Be" to "Beryllium", "B" to "Boron",
        "C" to "Carbon", "N" to "Nitrogen", "O" to "Oxygen", "F" to "Fluorine", "Ne" to "Neon",
        "Na" to "Sodium", "Mg" to "Magnesium", "Al" to "Aluminium", "Si" to "Silicon", "P" to "Phosphorus",
        "S" to "Sulfur", "Cl" to "Chlorine", "Ar" to "Argon", "K" to "Potassium", "Ca" to "Calcium",
        "V" to "Vanadium", "Cr" to "Chromium", "Mn" to "Manganese", "Fe" to "Iron", "Co" to "Cobalt",
        "Ni" to "Nickel", "Cu" to "Copper", "Zn" to "Zinc", "Ga" to "Gallium", "As" to "Arsenic",
        "Se" to "Selenium", "Br" to "Bromine", "Rb" to "Rubidium", "Sr" to "Strontium", "Mo" to "Molybdenum",
        "Ru" to "Ruthenium", "Rh" to "Rhodium", "Pd" to "Palladium", "Cd" to "Cadmium", "I" to "Iodine",
        "Xe" to "Xenon", "Cs" to "Caesium", "Ba" to "Barium", "La" to "Lanthanum", "Eu" to "Europium",
        "Tb" to "Terbium", "Yb" to "Ytterbium", "W" to "Tungsten", "Pt" to "Platinum", "Au" to "Gold",
        "Hg" to "Mercury", "Tl" to "Thallium", "Pb" to "Lead", "U" to "Uranium", "X" to "Unknown element",
    )

    /** `0xRRGGBB` of the element in [scheme] (Jmol by default), or [UNKNOWN_COLOR]. */
    fun colorOf(element: String, scheme: AtomColors = AtomColors.JMOL): Int = when (scheme) {
        AtomColors.JMOL -> colors[element]
        AtomColors.RASMOL -> rasmol[element]
    } ?: UNKNOWN_COLOR

    /** Is the element in the color table? ("X" and anything unlisted are not.) */
    fun isKnown(element: String): Boolean = element in colors

    /**
     * Radius of the ball, in angstrom (the same unit as the coordinates). It is NOT the real size of the atom: it is
     * chosen so that the picture looks like "ball and stick" (balls clearly smaller than the distance between bonded
     * atoms, about 1 to 1.5 A), with hydrogen smaller and heavy atoms bigger.
     */
    fun radiusOf(element: String): Float = when (element) {
        "H" -> 0.25f
        "C", "N", "O", "F" -> 0.40f
        "P", "S", "Cl", "Br", "I", "Se", "B", "Si" -> 0.50f
        else -> 0.55f
    }

    /** English name for the screen; the symbol itself when the element is not in the table. */
    fun nameOf(element: String): String = names[element] ?: element
}
