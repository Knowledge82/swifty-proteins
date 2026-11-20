package dev.knowledge82.swiftyproteins.scene

import dev.knowledge82.swiftyproteins.molecule.Atom
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Where and how to place a CYLINDER so that it joins two atoms.
 *
 * A cylinder is born standing up (its axis is the Y axis), centered on the origin. Two nested nodes bring it to
 * place (this is what the spike validated on the device, document 02b):
 *
 *  1. the OUTER node sits at the midpoint ([x], [y], [z]) and turns [spinDegrees] around the vertical axis Y;
 *  2. the INNER node holds the cylinder, of height [length], and leans it [tiltDegrees] around Z.
 *
 * The inner rotation is applied first (it is closer to the cylinder), then the outer one. [BondGeometryTest]
 * checks with matrices that the result points exactly from one atom to the other.
 */
data class BondPose(
    val x: Float,
    val y: Float,
    val z: Float,
    val length: Float,
    val tiltDegrees: Float,
    val spinDegrees: Float,
)

object BondGeometry {

    /** Two atoms closer than this (angstrom) have no direction to draw: the bond is skipped. */
    private const val MIN_LENGTH = 1e-4

    private const val RAD_TO_DEG = 180.0 / Math.PI

    /** The pose of the cylinder between [a] and [b], or `null` if they are at the same place. */
    fun poseOf(a: Atom, b: Atom): BondPose? {
        val dx = (b.x - a.x).toDouble()
        val dy = (b.y - a.y).toDouble()
        val dz = (b.z - a.z).toDouble()
        val length = sqrt(dx * dx + dy * dy + dz * dz)
        if (length < MIN_LENGTH) return null

        // How far the axis must lean from the vertical. coerceIn is a SAFETY NET: acos of a number above 1 is NaN and the cylinder
        // would vanish. With Float arithmetic (the spike) the quotient could round to 1.0000001; in Double it cannot exceed 1
        // (the root of a sum of squares is never below one of the terms), so removing the guard changes nothing today.
        val tilt = acos((dy / length).coerceIn(-1.0, 1.0)) * RAD_TO_DEG
        // Which way it leans (around the vertical axis).
        val spin = atan2(dz, -dx) * RAD_TO_DEG

        return BondPose(
            x = (a.x + b.x) / 2f,
            y = (a.y + b.y) / 2f,
            z = (a.z + b.z) / 2f,
            length = length.toFloat(),
            tiltDegrees = tilt.toFloat(),
            spinDegrees = spin.toFloat(),
        )
    }
}
