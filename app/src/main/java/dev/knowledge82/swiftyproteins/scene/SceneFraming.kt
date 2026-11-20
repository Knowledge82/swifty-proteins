package dev.knowledge82.swiftyproteins.scene

import dev.knowledge82.swiftyproteins.molecule.Molecule
import kotlin.math.max
import kotlin.math.sqrt

/**
 * How far to put the camera so that the WHOLE molecule is in view when the scene opens.
 *
 * The parser centers every molecule on the origin, so the camera orbits around (0, 0, 0) and it is enough to know
 * how far the farthest atom is.
 */
object SceneFraming {

    /** Never closer than this (angstrom), even for a single atom. */
    const val MIN_DISTANCE = 6f

    /**
     * Distance = [DISTANCE_PER_RADIUS] times the radius of the molecule. ESTIMATED, not measured: a phone is TALL, so
     * the narrow side (the width) limits what fits; with the usual lens that asks for about five radii. To be adjusted
     * on the device (document 07, section 10).
     */
    const val DISTANCE_PER_RADIUS = 5.5f

    /** Distance from the origin to the outer edge of the farthest ball. It counts ALL atoms, hidden hydrogens included, so the view does not jump when they are shown or hidden. */
    fun radiusOf(molecule: Molecule): Float {
        var farthest = 0f
        for (atom in molecule.atoms) {
            val reach = sqrt(atom.x * atom.x + atom.y * atom.y + atom.z * atom.z) + ElementStyle.radiusOf(atom.element)
            farthest = max(farthest, reach)
        }
        return farthest
    }

    /** Where the camera starts, as a distance from the center. */
    fun cameraDistance(radius: Float): Float = max(MIN_DISTANCE, DISTANCE_PER_RADIUS * radius)

    /**
     * The closest the camera may come to the center when zooming in (angstrom), THE SAME for every ligand.
     *
     * Why absolute: SceneView limits the zoom as a FRACTION of the starting distance (default 0.15). The starting
     * distance grows with the molecule (5.5 x radius), so a big ligand such as 15P (radius 41 -> start 228) could not
     * get nearer than 34 angstrom, while the small 001 (start ~35) could reach 5. The limit must be about what we want
     * to SEE (a ball is ~1 angstrom wide; at 4 it fills a good part of the screen), not about how big the molecule is.
     * ESTIMATED value: to be tuned on the device (document 07).
     */
    const val MIN_CLOSE_DISTANCE = 4f

    /** How far the camera may go back, as a multiple of the starting distance. Keeps everything inside the far plane (1000 in SceneView) even for 15P: 3 x 228 + 41 = 725. */
    const val MAX_ZOOM_OUT_FACTOR = 3f

    /** The fraction of the starting distance that equals [MIN_CLOSE_DISTANCE]; never above 0.5, so zooming in is always possible, even for a tiny molecule whose start is already close. */
    fun minZoomFactor(startDistance: Float): Float =
        (MIN_CLOSE_DISTANCE / startDistance).coerceAtMost(MAX_MIN_ZOOM_FACTOR)

    private const val MAX_MIN_ZOOM_FACTOR = 0.5f
}
