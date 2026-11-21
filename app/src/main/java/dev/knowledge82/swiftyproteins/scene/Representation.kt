package dev.knowledge82.swiftyproteins.scene

/**
 * The four ways to draw a molecule (bonus VII.1, document 15). Pure Kotlin: the scene only asks these questions,
 * so every size is tested on the JVM, and switching is just recomposing with another value (no reload).
 *
 * | Model          | Atoms                           | Bonds                                        |
 * |----------------|---------------------------------|----------------------------------------------|
 * | Ball and stick | small balls ([ElementStyle])    | thin grey sticks                             |
 * | Space-filling  | van der Waals spheres           | none (the spheres overlap: that IS the bond)  |
 * | Sticks         | as thick as the bond (joints)   | thick, each half in its atom's colour        |
 * | Wireframe      | as thin as the bond             | very thin, each half in its atom's colour    |
 */
enum class Representation(val label: String) {
    BALL_AND_STICK("Ball & stick"),
    SPACE_FILLING("Space-filling"),
    STICKS("Sticks"),
    WIREFRAME("Wireframe");

    /**
     * Radius of the ball of an atom. [bonded] = the atom has at least one bond DRAWN: in sticks and wireframe a
     * lone atom (an ion, or every atom when hydrogens are hidden and it only had hydrogens) would otherwise be a
     * dot too small to see or to tap, so it gets [LONE_ATOM_RADIUS].
     */
    fun atomRadius(element: String, bonded: Boolean): Float = when (this) {
        BALL_AND_STICK -> ElementStyle.radiusOf(element)
        SPACE_FILLING -> VanDerWaals.radiusOf(element)
        STICKS -> if (bonded) STICK_RADIUS else LONE_ATOM_RADIUS
        WIREFRAME -> if (bonded) WIRE_RADIUS else LONE_ATOM_RADIUS
    }

    /** Radius of a bond's cylinder, or null when this model draws no bonds. */
    val bondRadius: Float?
        get() = when (this) {
            BALL_AND_STICK -> BALL_AND_STICK_BOND_RADIUS
            SPACE_FILLING -> null
            STICKS -> STICK_RADIUS
            WIREFRAME -> WIRE_RADIUS
        }

    /** True: each bond is two halves, each in the colour of its atom. False: one grey stick. */
    val bondsInAtomColours: Boolean get() = this == STICKS || this == WIREFRAME

    /** The halo around a selected atom: 1.5 times its ball, but never smaller than [MIN_HALO_RADIUS] (a wire joint is 0.05). */
    fun haloRadius(element: String, bonded: Boolean): Float = maxOf(atomRadius(element, bonded) * HALO_SCALE, MIN_HALO_RADIUS)

    /** The next model, for the key that cycles through them. */
    fun next(): Representation = entries[(ordinal + 1) % entries.size]

    companion object {
        const val BALL_AND_STICK_BOND_RADIUS = 0.12f
        const val STICK_RADIUS = 0.2f
        const val WIRE_RADIUS = 0.05f
        const val LONE_ATOM_RADIUS = 0.35f
        const val HALO_SCALE = 1.5f
        const val MIN_HALO_RADIUS = 0.45f
    }
}
