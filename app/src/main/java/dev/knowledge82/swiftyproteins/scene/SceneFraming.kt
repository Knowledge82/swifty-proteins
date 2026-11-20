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
}
