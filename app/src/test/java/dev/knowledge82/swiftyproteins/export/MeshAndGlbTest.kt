package dev.knowledge82.swiftyproteins.export

import dev.knowledge82.swiftyproteins.molecule.Atom
import dev.knowledge82.swiftyproteins.molecule.Bond
import dev.knowledge82.swiftyproteins.molecule.BondOrder
import dev.knowledge82.swiftyproteins.molecule.Molecule
import dev.knowledge82.swiftyproteins.scene.AtomColors
import dev.knowledge82.swiftyproteins.scene.Representation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

class MeshAndGlbTest {

    // ---------------------------------------------------------------- mesh

    /** Every triangle must face OUTWARD (counter-clockwise from outside) and have an area. */
    private fun assertOutwardFrom(mesh: Mesh, centre: (FloatArray) -> FloatArray) {
        val p = mesh.positions(); val idx = mesh.indices()
        for (t in 0 until mesh.triangleCount) {
            val (a, b, c) = Triple(idx[3 * t], idx[3 * t + 1], idx[3 * t + 2])
            val e1 = FloatArray(3) { p[3 * b + it] - p[3 * a + it] }
            val e2 = FloatArray(3) { p[3 * c + it] - p[3 * a + it] }
            val n = floatArrayOf(e1[1] * e2[2] - e1[2] * e2[1], e1[2] * e2[0] - e1[0] * e2[2], e1[0] * e2[1] - e1[1] * e2[0])
            val area = sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2])
            assertTrue("triangle $t has no area", area > 1e-9f)
            val mid = FloatArray(3) { (p[3 * a + it] + p[3 * b + it] + p[3 * c + it]) / 3 }
            val out = centre(mid).let { cc -> FloatArray(3) { mid[it] - cc[it] } }
            assertTrue("triangle $t faces inward", n[0] * out[0] + n[1] * out[1] + n[2] * out[2] > 0)
        }
    }

    @Test
    fun aSphereIsClosedOutwardAndOnItsRadius() {
        val m = Mesh().apply { addSphere(1f, 2f, 3f, 1.5f, 8, 12) }
        assertEquals(9 * 13, m.vertexCount)
        assertEquals(12 * (2 * 8 - 2), m.triangleCount)       // two per quad, minus the pole slivers
        val p = m.positions(); val n = m.normals()
        for (v in 0 until m.vertexCount) {
            val d = sqrt((p[3 * v] - 1f).let { it * it } + (p[3 * v + 1] - 2f).let { it * it } + (p[3 * v + 2] - 3f).let { it * it })
            assertEquals(1.5f, d, 1e-5f)
            assertEquals(1f, sqrt(n[3 * v] * n[3 * v] + n[3 * v + 1] * n[3 * v + 1] + n[3 * v + 2] * n[3 * v + 2]), 1e-5f)
        }
        assertOutwardFrom(m) { floatArrayOf(1f, 2f, 3f) }
    }

    @Test
    fun aCylinderJoinsItsEndsWithOutwardSidesAtTheRadius() {
        val ends = listOf(floatArrayOf(3f, 0f, 0f), floatArrayOf(0f, 0f, 4f), floatArrayOf(1f, -2f, 2f))   // incl. along z
        for (b in ends) {
            val m = Mesh().apply { addCylinder(0f, 0f, 0f, b[0], b[1], b[2], 0.2f, 10) }
            assertEquals(22, m.vertexCount)
            assertEquals(20, m.triangleCount)
            val len = sqrt(b[0] * b[0] + b[1] * b[1] + b[2] * b[2])
            val axis = FloatArray(3) { b[it] / len }
            val p = m.positions()
            for (v in 0 until m.vertexCount) {
                val t = p[3 * v] * axis[0] + p[3 * v + 1] * axis[1] + p[3 * v + 2] * axis[2]
                assertTrue(abs(t) < 1e-4f || abs(t - len) < 1e-4f)            // on one of the two end rings
                val r = FloatArray(3) { p[3 * v + it] - t * axis[it] }
                assertEquals(0.2f, sqrt(r[0] * r[0] + r[1] * r[1] + r[2] * r[2]), 1e-4f)
            }
            assertOutwardFrom(m) { q -> val t = q[0] * axis[0] + q[1] * axis[1] + q[2] * axis[2]; FloatArray(3) { t * axis[it] } }
        }
    }

    @Test
    fun aZeroLengthCylinderAddsNothing() {
        assertEquals(0, Mesh().apply { addCylinder(1f, 1f, 1f, 1f, 1f, 1f, 0.1f, 8) }.vertexCount)
    }

    // ---------------------------------------------------------------- glb

    // Water-like: O with two H; one bond each.
    private val water = Molecule(
        "HOH",
        listOf(Atom("O", "O", 0f, 0f, 0f), Atom("H1", "H", 0.96f, 0f, 0f), Atom("H2", "H", -0.24f, 0.93f, 0f)),
        listOf(Bond(0, 1, BondOrder.SINGLE), Bond(0, 2, BondOrder.SINGLE)),
    )

    private fun chunks(glb: ByteArray): Pair<String, ByteArray> {
        val b = ByteBuffer.wrap(glb).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(GlbWriter.MAGIC, b.int)
        assertEquals(2, b.int)
        assertEquals(glb.size, b.int)
        val jsonLength = b.int
        assertEquals(GlbWriter.CHUNK_JSON, b.int)
        assertEquals(0, jsonLength % 4)
        val json = ByteArray(jsonLength).also { b.get(it) }.toString(Charsets.UTF_8)
        val binLength = b.int
        assertEquals(GlbWriter.CHUNK_BIN, b.int)
        assertEquals(0, binLength % 4)
        val bin = ByteArray(binLength).also { b.get(it) }
        assertEquals(0, b.remaining())
        return json to bin
    }

    @Test
    fun aGlbHasTheHeaderAndTwoAlignedChunks() {
        val (json, bin) = chunks(GlbWriter.write(water, Representation.BALL_AND_STICK, showHydrogens = true))
        assertTrue(json.trimEnd().startsWith("{\"asset\":{\"version\":\"2.0\""))
        assertTrue(json.contains("\"buffers\":[{\"byteLength\":${bin.size}}]"))
    }

    @Test
    fun oneMeshPerColourAndTheSticksApart() {
        assertEquals(listOf("O", "H", "bonds"), GlbWriter.groupsOf(water, Representation.BALL_AND_STICK, true).map { it.name })
        // Sticks: halves in the colour of each atom, no grey group.
        assertEquals(listOf("O", "H"), GlbWriter.groupsOf(water, Representation.STICKS, true).map { it.name })
        // Space-filling: no bonds at all.
        assertEquals(listOf("O", "H"), GlbWriter.groupsOf(water, Representation.SPACE_FILLING, true).map { it.name })
    }

    @Test
    fun hiddenHydrogensAreNotExported() {
        val groups = GlbWriter.groupsOf(water, Representation.BALL_AND_STICK, showHydrogens = false)
        assertEquals(listOf("O"), groups.map { it.name })   // the O-H sticks go with the hydrogens
    }

    @Test
    fun theIndexesNeverPointOutsideTheirMesh() {
        for (g in GlbWriter.groupsOf(water, Representation.WIREFRAME, true)) {
            assertTrue(g.mesh.indices().all { it in 0 until g.mesh.vertexCount })
        }
    }

    @Test
    fun coloursAreConvertedToLinearLight() {
        val (r, g, b) = GlbWriter.linear(0x909090)                // CPK carbon
        assertEquals(0.279f, r, 0.001f); assertEquals(r, g, 0f); assertEquals(r, b, 0f)
        assertEquals(Triple(1f, 0f, 0f), GlbWriter.linear(0xFF0000))
        assertEquals(0.0f, GlbWriter.linear(0x000000).first, 0f)
        assertEquals(0.000304f, GlbWriter.linear(0x010101).first, 1e-6f)   // the linear part of the sRGB curve: 1/255/12.92
    }

    @Test
    fun namesAreEscapedForJson() {
        assertEquals("\"A \\\"B\\\" \\\\ \\u000a\"", GlbWriter.json("A \"B\" \\ \n"))
    }

    @Test
    fun theExportTakesTheColoursOnScreen() {
        // Document 21: the .glb is painted in the scheme the user chose, like the 3D view.
        val jmol = GlbWriter.groupsOf(water, Representation.BALL_AND_STICK, true).associate { it.name to it.rgb }
        val rasmol = GlbWriter.groupsOf(water, Representation.BALL_AND_STICK, true, AtomColors.RASMOL).associate { it.name to it.rgb }
        assertEquals(0xFF0D0D, jmol["O"])
        assertEquals(0xF00000, rasmol["O"])
        assertEquals(jmol["bonds"], rasmol["bonds"])   // the grey sticks are not an element
    }
}
