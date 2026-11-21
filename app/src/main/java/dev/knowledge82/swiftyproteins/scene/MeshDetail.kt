package dev.knowledge82.swiftyproteins.scene

import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Level of detail by molecule size (bonus VII.4 "memory management for large molecules", document 20). Pure, tested.
 *
 * Every ball is a mesh cut into `detail` x `detail` bands: about 2 x detail^2 triangles, (detail + 1)^2 vertices, each
 * with its own buffers on the GPU AND its own lists in the Java heap (SceneView keeps the vertices as objects). A small
 * ligand can afford smooth balls; the biggest one in the catalogue (CDL, 256 atoms) must not pay 256 times that.
 *
 * The rule: a fixed TRIANGLE BUDGET for the balls, shared by the atoms of the molecule:
 *
 *     detail = sqrt(BUDGET / (2 x atoms)), between [MIN] and [MAX]
 *
 * The budget is anchored to what already ran at 60 FPS on the A33: 256 atoms at 20 bands (the fixed value used
 * before this rule). So the biggest molecule gets exactly what was measured, and every smaller one gets MORE detail,
 * never less: ATP (47 atoms) goes from 20 to 32 bands.
 *
 * It depends on the number of atoms in the MOLECULE, not on how many are drawn: hiding the hydrogens does not change
 * the detail, so it never rebuilds every ball (an expensive change, done on the main thread).
 */
object MeshDetail {

    /** 2 x 256 atoms x 20^2: the configuration that was seen at 60 FPS. */
    const val TRIANGLE_BUDGET = 204_800

    const val MIN = 12
    const val MAX = 32

    /** Bands of an ordinary ball. */
    fun ball(atoms: Int): Int = sqrt(TRIANGLE_BUDGET / (2.0 * atoms.coerceAtLeast(1))).roundToInt().coerceIn(MIN, MAX)

    /** Space-filling: balls 4 x bigger on screen show their facets, so a fifth more (24 at the size limit, as before). */
    fun bigBall(atoms: Int): Int = (ball(atoms) * 6 / 5).coerceAtMost(MAX)

    /** Tiny balls (a hydrogen, a wire joint): three fifths (12 at the size limit, as before), never below 8. */
    fun smallBall(atoms: Int): Int = (ball(atoms) * 3 / 5).coerceAtLeast(8)

    /** Sides of a bond cylinder: thin sticks need fewer than a ball. At most 24 (SceneView's default), since ball <= 32. */
    fun cylinderSides(atoms: Int): Int = (ball(atoms) * 3 / 4).coerceAtLeast(8)

    /** Triangles of one ball of [detail] bands (the two poles are fans: 2 x detail x (detail - 1)). */
    fun ballTriangles(detail: Int): Int = 2 * detail * (detail - 1)

    /** Vertices of one ball as SceneView builds it: (stacks + 1) x (slices + 1), the seam repeated. */
    fun ballVertices(detail: Int): Int = (detail + 1) * (detail + 1)
}
