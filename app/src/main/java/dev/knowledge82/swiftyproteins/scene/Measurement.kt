package dev.knowledge82.swiftyproteins.scene

import dev.knowledge82.swiftyproteins.molecule.Atom
import dev.knowledge82.swiftyproteins.molecule.Molecule
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * The ruler (bonus VII.3: "measure distances and angles", document 16). Pure Kotlin, tested.
 *
 * The user taps atoms one after another; what is measured depends on how many:
 *
 * | Atoms | Measure                                                                    |
 * |------:|----------------------------------------------------------------------------|
 * |     2 | distance A–B                                                               |
 * |     3 | the angle at the MIDDLE atom, A–B–C (bond angle when they are bonded)      |
 * |     4 | the torsion (dihedral) angle A–B–C–D: the twist around the B–C axis        |
 */
object Measurement {

    /** At most this many atoms: a fifth tap starts a new measurement. */
    const val MAX_ATOMS = 4

    /**
     * The new list of measured atoms after a tap on atom [index]:
     * an atom already in the list is REMOVED (a second tap undoes it); a full list starts again with this atom;
     * otherwise the atom is added at the end.
     */
    fun tap(current: List<Int>, index: Int): List<Int> = when {
        index in current -> current - index
        current.size >= MAX_ATOMS -> listOf(index)
        else -> current + index
    }

    /** Drops the atoms that are no longer drawn (hydrogens just hidden): a measurement of something invisible would lie. */
    fun visible(current: List<Int>, molecule: Molecule, showHydrogens: Boolean): List<Int> =
        current.filter { Drawn.atom(molecule, it, showHydrogens) }

    /** Distance in angstrom. */
    fun distance(a: Atom, b: Atom): Double = norm(minus(b, a))

    /**
     * The angle at [b] between the directions to [a] and to [c], in degrees (0..180).
     * Null if [a] or [c] is at the same place as [b] (no direction).
     */
    fun angle(a: Atom, b: Atom, c: Atom): Double? {
        val u = minus(a, b)
        val v = minus(c, b)
        val lengths = norm(u) * norm(v)
        if (lengths < EPSILON) return null
        // coerceIn: rounding can give 1.0000000002, and acos of that is NaN.
        return Math.toDegrees(acos((dot(u, v) / lengths).coerceIn(-1.0, 1.0)))
    }

    /**
     * The torsion (dihedral) angle A–B–C–D in degrees, -180..180: looking along B→C, how far the bond B–A must turn
     * to cover the bond C–D. IUPAC sign: POSITIVE when that turn is CLOCKWISE. 0 = "cis" (eclipsed, same side),
     * 180 = "trans" (opposite sides).
     *
     * Formula (Blondel and Karplus, 1996): with b1 = B-A, b2 = C-B, b3 = D-C,
     * angle = atan2(|b2| · b1·(b2×b3), (b1×b2)·(b2×b3)). atan2 gives the sign and is stable near 0 and 180, where an
     * acos of the cosine would lose precision.
     *
     * Null when it is undefined: three of the atoms in a straight line (one of the planes does not exist).
     */
    fun torsion(a: Atom, b: Atom, c: Atom, d: Atom): Double? {
        val b1 = minus(b, a)
        val b2 = minus(c, b)
        val b3 = minus(d, c)
        val n1 = cross(b1, b2)
        val n2 = cross(b2, b3)
        if (norm(n1) < EPSILON || norm(n2) < EPSILON) return null
        val y = norm(b2) * dot(b1, n2)
        val x = dot(n1, n2)
        return Math.toDegrees(atan2(y, x))
    }

    private const val EPSILON = 1e-9

    private fun minus(p: Atom, q: Atom) = doubleArrayOf((p.x - q.x).toDouble(), (p.y - q.y).toDouble(), (p.z - q.z).toDouble())
    private fun dot(u: DoubleArray, v: DoubleArray) = u[0] * v[0] + u[1] * v[1] + u[2] * v[2]
    private fun norm(u: DoubleArray) = sqrt(dot(u, u))
    private fun cross(u: DoubleArray, v: DoubleArray) =
        doubleArrayOf(u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0])
}
