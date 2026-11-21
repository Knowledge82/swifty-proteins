package dev.knowledge82.swiftyproteins.export

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A triangle mesh built in plain Kotlin: positions, normals (one per vertex) and triangles (3 indexes each).
 * Spheres and cylinders are added into the SAME mesh: all the carbons of a molecule are one mesh with one colour
 * (document 18). Triangles are counter-clockwise seen from OUTSIDE, as glTF requires for front faces.
 */
class Mesh {
    private val positions = FloatList()
    private val normals = FloatList()
    private val indices = IntList()

    val vertexCount: Int get() = positions.size / 3
    val triangleCount: Int get() = indices.size / 3

    fun positions(): FloatArray = positions.toArray()
    fun normals(): FloatArray = normals.toArray()
    fun indices(): IntArray = indices.toArray()

    /**
     * A UV sphere: [stacks] bands from pole to pole, [slices] around. Vertex (i, j) sits at polar angle
     * φ = π·i/stacks and azimuth θ = 2π·j/slices; its normal is the unit vector from the centre. The seam column
     * (j = slices) repeats j = 0: simpler indexes, and harmless.
     */
    fun addSphere(cx: Float, cy: Float, cz: Float, radius: Float, stacks: Int, slices: Int) {
        require(stacks >= 2 && slices >= 3 && radius > 0f)
        val base = vertexCount
        for (i in 0..stacks) {
            val phi = PI * i / stacks
            for (j in 0..slices) {
                val theta = 2 * PI * j / slices
                val nx = (sin(phi) * cos(theta)).toFloat()
                val ny = cos(phi).toFloat()
                val nz = (sin(phi) * sin(theta)).toFloat()
                vertex(cx + radius * nx, cy + radius * ny, cz + radius * nz, nx, ny, nz)
            }
        }
        val row = slices + 1
        for (i in 0 until stacks) {
            for (j in 0 until slices) {
                val a = base + i * row + j
                val b = a + row
                // The first band touches the north pole (all its top vertices are one point) and the last band the
                // south pole: one of their two triangles would have no area, so it is not written.
                if (i != 0) triangle(a, a + 1, b)
                if (i != stacks - 1) triangle(a + 1, b + 1, b)
            }
        }
    }

    /**
     * An open tube from (ax, ay, az) to (bx, by, bz): [segments] sides, no caps (each end is inside an atom).
     * The ring around the axis is built from two unit vectors u, v perpendicular to it and to each other.
     */
    fun addCylinder(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, radius: Float, segments: Int) {
        require(segments >= 3 && radius > 0f)
        var dx = bx - ax; var dy = by - ay; var dz = bz - az
        val length = sqrt(dx * dx + dy * dy + dz * dz)
        if (length < 1e-6f) return   // two atoms at the same place: nothing to draw
        dx /= length; dy /= length; dz /= length
        // u = axis × reference, with a reference that is surely not parallel to the axis.
        val (rx, ry, rz) = if (abs(dz) < 0.9f) Triple(0f, 0f, 1f) else Triple(1f, 0f, 0f)
        var ux = dy * rz - dz * ry; var uy = dz * rx - dx * rz; var uz = dx * ry - dy * rx
        val ul = sqrt(ux * ux + uy * uy + uz * uz)
        ux /= ul; uy /= ul; uz /= ul
        val vx = dy * uz - dz * uy; val vy = dz * ux - dx * uz; val vz = dx * uy - dy * ux   // v = axis × u
        val base = vertexCount
        for (k in 0..segments) {
            val angle = 2 * PI * k / segments
            val c = cos(angle).toFloat(); val s = sin(angle).toFloat()
            val nx = c * ux + s * vx; val ny = c * uy + s * vy; val nz = c * uz + s * vz
            vertex(ax + radius * nx, ay + radius * ny, az + radius * nz, nx, ny, nz)
            vertex(bx + radius * nx, by + radius * ny, bz + radius * nz, nx, ny, nz)
        }
        for (k in 0 until segments) {
            val a0 = base + 2 * k; val b0 = a0 + 1; val a1 = a0 + 2; val b1 = a0 + 3
            triangle(a0, a1, b1)
            triangle(a0, b1, b0)
        }
    }

    private fun vertex(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float) {
        positions.add(x); positions.add(y); positions.add(z)
        normals.add(nx); normals.add(ny); normals.add(nz)
    }

    private fun triangle(a: Int, b: Int, c: Int) {
        indices.add(a); indices.add(b); indices.add(c)
    }
}

/** A growable FloatArray: a List<Float> would box every number (millions of objects for a big molecule). */
internal class FloatList {
    private var data = FloatArray(1024)
    var size = 0
        private set
    fun add(v: Float) { if (size == data.size) data = data.copyOf(size * 2); data[size++] = v }
    fun toArray(): FloatArray = data.copyOf(size)
}

internal class IntList {
    private var data = IntArray(1024)
    var size = 0
        private set
    fun add(v: Int) { if (size == data.size) data = data.copyOf(size * 2); data[size++] = v }
    fun toArray(): IntArray = data.copyOf(size)
}
