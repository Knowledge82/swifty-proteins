package dev.knowledge82.swiftyproteins.scene

import dev.knowledge82.swiftyproteins.molecule.Atom
import dev.knowledge82.swiftyproteins.molecule.Molecule
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
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
     * Half the vertical field of view of SceneView's default lens, as a tangent: a 28 mm lens on a sensor 24 mm high
     * (Filament's `setLensProjection`), so tan(fov / 2) = 12 / 28. The lens spans the HEIGHT of the visible area.
     */
    const val TAN_HALF_FOV = 12f / 28f

    /** A little air around the molecule (6 %). */
    const val MARGIN = 1.06f

    /**
     * The visible area the first rule was tuned on: the whole screen of a tall phone (1080 x 2400). There the old
     * "5.5 radii" and the formula below agree (5.50).
     */
    const val TALL_PHONE_ASPECT = 0.45f

    /** Distance from the origin to the outer edge of the farthest ball. It counts ALL atoms, hidden hydrogens included, so the view does not jump when they are shown or hidden. */
    fun radiusOf(molecule: Molecule): Float {
        var farthest = 0f
        for (atom in molecule.atoms) {
            val reach = sqrt(atom.x * atom.x + atom.y * atom.y + atom.z * atom.z) + ElementStyle.radiusOf(atom.element)
            farthest = max(farthest, reach)
        }
        return farthest
    }

    /**
     * Where the camera starts, as a distance from the center, so that a ball of [radius] fits the VISIBLE area of
     * [aspect] = width / height (document 21, §18). The lens spans the height; a narrow area (aspect < 1) is limited
     * by its width instead:
     *
     *     distance = MARGIN x radius / (TAN_HALF_FOV x min(aspect, 1))
     *
     * At first the 3D view was the whole screen and the rule was "5.5 radii", tuned for that. Now the camera aims at
     * the window between the panels (SceneView's `contentPadding`), whose shape depends on the phone and on its
     * orientation, so the distance follows the shape.
     */
    fun cameraDistance(radius: Float, aspect: Float = TALL_PHONE_ASPECT): Float =
        max(MIN_DISTANCE, MARGIN * radius / (TAN_HALF_FOV * min(aspect.coerceAtLeast(0.1f), 1f)))

    /**
     * The aspect, rounded to 0.05: the camera is rebuilt only when the window really changes shape (a rotation, a
     * bigger font), never for a pixel of layout, which would send the camera back home under the user's finger.
     */
    fun aspectKey(width: Float, height: Float): Float =
        if (width <= 0f || height <= 0f) TALL_PHONE_ASPECT else (width / height * 20f).roundToInt() / 20f

    /**
     * The closest the camera may come to the center when zooming in (angstrom), THE SAME for every ligand.
     *
     * Why absolute: SceneView limits the zoom as a FRACTION of the starting distance (default 0.15). The starting
     * distance grows with the molecule (a few radii), so a big ligand such as 15P (radius 41 -> start 228) could not
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

    /**
     * How close the camera comes when the user double-taps an atom to orbit around it (bonus VII.3, document 15).
     * ESTIMATED: from 12 angstrom the atom and its neighbours fill the width of a phone. Never farther than the
     * normal start (a tiny molecule is already that close).
     */
    const val FOCUS_DISTANCE = 12f

    /** Where the camera orbits and how it may zoom: around the whole molecule, or around one atom. */
    data class Orbit(val x: Float, val y: Float, val z: Float, val distance: Float, val minZoomFactor: Float, val maxZoomFactor: Float)

    /**
     * The orbit for [focus] (an atom, or null for the whole molecule) when the whole molecule starts at
     * [wholeDistance]. Zoom limits are in angstrom for both, as everywhere: never closer than [MIN_CLOSE_DISTANCE];
     * never farther than [MAX_ZOOM_OUT_FACTOR] times the WHOLE view (so from a focused atom the user can pinch out
     * until the whole molecule is back, without having to double-tap).
     */
    fun orbit(focus: Atom?, wholeDistance: Float): Orbit {
        if (focus == null) return Orbit(0f, 0f, 0f, wholeDistance, minZoomFactor(wholeDistance), MAX_ZOOM_OUT_FACTOR)
        val distance = minOf(FOCUS_DISTANCE, wholeDistance)
        return Orbit(focus.x, focus.y, focus.z, distance, minZoomFactor(distance), MAX_ZOOM_OUT_FACTOR * wholeDistance / distance)
    }
}
