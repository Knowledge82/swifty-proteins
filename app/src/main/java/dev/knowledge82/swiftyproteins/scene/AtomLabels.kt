package dev.knowledge82.swiftyproteins.scene

import kotlin.math.sqrt

/**
 * Where and how big the element labels are (bonus VII.3 "atom labels", document 19). Pure, tested.
 *
 * A label is a flat picture that always faces the camera (a billboard). Put at the CENTRE of its atom it would be
 * hidden inside the ball, so it is pushed out of the ball TOWARD THE CAMERA, just past the surface: from any angle it
 * sits on the visible face of its atom. Atoms in front still hide the labels of atoms behind them, as they should.
 */
object AtomLabels {

    /** A little past the surface, so the label never fights the ball for the same depth (no flicker). */
    const val GAP = 0.03f

    /** Height of the label in angstrom: 1.4 x the ball, but readable on a wire joint and not huge in space-filling. */
    fun height(atomRadius: Float): Float = (atomRadius * 1.4f).coerceIn(MIN_HEIGHT, MAX_HEIGHT)

    /** Width: the picture is drawn 3:2 (room for two letters, "Cl"). */
    fun width(atomRadius: Float): Float = height(atomRadius) * 1.5f

    const val MIN_HEIGHT = 0.35f
    const val MAX_HEIGHT = 1.2f

    /**
     * The label's position: from the atom centre, [atomRadius] + [GAP] toward the camera. If the camera is ON the
     * atom (zoomed inside it), there is no direction: the centre.
     */
    fun position(ax: Float, ay: Float, az: Float, atomRadius: Float, cx: Float, cy: Float, cz: Float): Triple<Float, Float, Float> {
        val dx = cx - ax; val dy = cy - ay; val dz = cz - az
        val d = sqrt(dx * dx + dy * dy + dz * dz)
        if (d < 1e-6f) return Triple(ax, ay, az)
        val k = (atomRadius + GAP) / d
        return Triple(ax + dx * k, ay + dy * k, az + dz * k)
    }

    /**
     * Whether a label must move: only if it is off by more than [EPSILON]. The labels are moved from the scene's
     * frame OBSERVER (after each frame); moving a node asks for a new frame. With the camera still, every label is
     * already in place, nothing moves, and the scene can stop drawing (on-demand rendering, document 12).
     */
    fun needsMove(x: Float, y: Float, z: Float, tx: Float, ty: Float, tz: Float): Boolean =
        kotlin.math.abs(x - tx) > EPSILON || kotlin.math.abs(y - ty) > EPSILON || kotlin.math.abs(z - tz) > EPSILON

    const val EPSILON = 1e-3f
}
