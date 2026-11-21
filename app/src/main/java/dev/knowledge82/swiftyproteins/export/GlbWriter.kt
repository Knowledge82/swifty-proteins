package dev.knowledge82.swiftyproteins.export

import dev.knowledge82.swiftyproteins.molecule.Molecule
import dev.knowledge82.swiftyproteins.scene.BondGeometry
import dev.knowledge82.swiftyproteins.scene.Drawn
import dev.knowledge82.swiftyproteins.scene.AtomColors
import dev.knowledge82.swiftyproteins.scene.ElementStyle
import dev.knowledge82.swiftyproteins.scene.Representation
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.pow

/**
 * The molecule as a **3D model file: glTF 2.0, binary (`.glb`)** — the open standard of the Khronos Group (the
 * "JPEG of 3D"). Blender, Windows' 3D Viewer and Android's Scene Viewer open it. Bonus VII.5 (document 18).
 *
 * It exports what is ON SCREEN: the current model (ball and stick, space-filling, sticks, wireframe) and the
 * hydrogens only if they are shown. 1 unit = 1 ångström.
 *
 * A .glb file is: a 12-byte header, a JSON chunk (the description: scene, nodes, meshes, materials, and where the
 * numbers are) and a BIN chunk (the numbers: positions, normals, triangle indexes, little-endian). Pure Kotlin,
 * tested, and checked with the official Khronos glTF Validator (document 18).
 */
object GlbWriter {

    /** One mesh of one colour: all the atoms of an element, or all the grey sticks. */
    class Group(val name: String, val rgb: Int, val mesh: Mesh)

    // Detail of the exported shapes: enough to look round in a viewer, small enough for a message (256 atoms ~ 2 MB).
    private const val BALL_STACKS = 14
    private const val BALL_SLICES = 18
    private const val SMALL_BALL = 10
    private const val TUBE_SEGMENTS = 12
    private const val STICK_RGB = 0xB3B3B3   // the grey of the scene's sticks (0.7, 0.7, 0.7)

    /** The groups of what is drawn: one per element present, plus the sticks (grey, or in halves of each colour). */
    fun groupsOf(molecule: Molecule, representation: Representation, showHydrogens: Boolean, colors: AtomColors = AtomColors.JMOL): List<Group> {
        val byColour = LinkedHashMap<String, Group>()
        fun groupFor(key: String, rgb: Int) = byColour.getOrPut(key) { Group(key, rgb, Mesh()) }

        val drawnBonds = molecule.bonds.indices.filter { Drawn.bond(molecule, it, showHydrogens, representation) }
        val bonded = BooleanArray(molecule.atoms.size)
        for (k in drawnBonds) { bonded[molecule.bonds[k].from] = true; bonded[molecule.bonds[k].to] = true }

        for ((i, atom) in molecule.atoms.withIndex()) {
            if (!Drawn.atom(molecule, i, showHydrogens)) continue
            val r = representation.atomRadius(atom.element, bonded[i])
            val detail = if (r < 0.3f) SMALL_BALL else BALL_STACKS
            groupFor(atom.element, ElementStyle.colorOf(atom.element, colors))
                .mesh.addSphere(atom.x, atom.y, atom.z, r, detail, if (r < 0.3f) SMALL_BALL else BALL_SLICES)
        }
        val bondRadius = representation.bondRadius
        if (bondRadius != null) {
            for (k in drawnBonds) {
                val a = molecule.atoms[molecule.bonds[k].from]
                val b = molecule.atoms[molecule.bonds[k].to]
                if (representation.bondsInAtomColours) {
                    if (BondGeometry.halvesOf(a, b) == null) continue
                    val mx = (a.x + b.x) / 2; val my = (a.y + b.y) / 2; val mz = (a.z + b.z) / 2
                    groupFor(a.element, ElementStyle.colorOf(a.element, colors)).mesh.addCylinder(a.x, a.y, a.z, mx, my, mz, bondRadius, TUBE_SEGMENTS)
                    groupFor(b.element, ElementStyle.colorOf(b.element, colors)).mesh.addCylinder(mx, my, mz, b.x, b.y, b.z, bondRadius, TUBE_SEGMENTS)
                } else {
                    groupFor("bonds", STICK_RGB).mesh.addCylinder(a.x, a.y, a.z, b.x, b.y, b.z, bondRadius, TUBE_SEGMENTS)
                }
            }
        }
        return byColour.values.filter { it.mesh.triangleCount > 0 }
    }

    /** The whole .glb file, in the colours on screen ([colors], document 21). */
    fun write(molecule: Molecule, representation: Representation, showHydrogens: Boolean, colors: AtomColors = AtomColors.JMOL): ByteArray =
        write(groupsOf(molecule, representation, showHydrogens, colors), "Swifty Proteins · ${molecule.id}")

    fun write(groups: List<Group>, title: String): ByteArray {
        val bin = ByteArrayOutputStream()
        val accessors = StringBuilder()
        val views = StringBuilder()
        val meshes = StringBuilder()
        val materials = StringBuilder()
        val nodes = StringBuilder()
        var accessorCount = 0
        var viewCount = 0

        fun comma(sb: StringBuilder) { if (sb.isNotEmpty()) sb.append(',') }

        /** Appends a block of numbers to BIN (aligned to 4 bytes, as glTF requires) and returns its bufferView index. */
        fun view(bytes: ByteArray, target: Int): Int {
            while (bin.size() % 4 != 0) bin.write(0)
            val offset = bin.size()
            bin.write(bytes)
            comma(views)
            views.append("""{"buffer":0,"byteOffset":$offset,"byteLength":${bytes.size},"target":$target}""")
            return viewCount++
        }

        for ((g, group) in groups.withIndex()) {
            val positions = group.mesh.positions()
            val normals = group.mesh.normals()
            val indices = group.mesh.indices()
            val count = group.mesh.vertexCount

            // POSITION needs min and max (the glTF spec requires them for this attribute).
            val min = floatArrayOf(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
            val max = floatArrayOf(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
            for (v in 0 until count) for (c in 0..2) {
                min[c] = minOf(min[c], positions[3 * v + c]); max[c] = maxOf(max[c], positions[3 * v + c])
            }
            val pv = view(floats(positions), ARRAY_BUFFER)
            val nv = view(floats(normals), ARRAY_BUFFER)
            val iv = view(ints(indices), ELEMENT_ARRAY_BUFFER)
            val pa = accessorCount++; val na = accessorCount++; val ia = accessorCount++
            comma(accessors)
            accessors.append("""{"bufferView":$pv,"componentType":$FLOAT,"count":$count,"type":"VEC3","min":${vec(min)},"max":${vec(max)}},""")
            accessors.append("""{"bufferView":$nv,"componentType":$FLOAT,"count":$count,"type":"VEC3"},""")
            accessors.append("""{"bufferView":$iv,"componentType":$UNSIGNED_INT,"count":${indices.size},"type":"SCALAR"}""")

            val (r, gr, b) = linear(group.rgb)
            comma(materials)
            materials.append(
                """{"name":${json(group.name)},"pbrMetallicRoughness":{"baseColorFactor":[${num(r)},${num(gr)},${num(b)},1.0],""" +
                    """"metallicFactor":0.0,"roughnessFactor":0.5}}"""
            )
            comma(meshes)
            meshes.append("""{"name":${json(group.name)},"primitives":[{"attributes":{"POSITION":$pa,"NORMAL":$na},"indices":$ia,"material":$g}]}""")
            comma(nodes)
            nodes.append("""{"name":${json(group.name)},"mesh":$g}""")
        }
        while (bin.size() % 4 != 0) bin.write(0)
        val binBytes = bin.toByteArray()

        val nodeList = groups.indices.joinToString(",")
        val jsonText = """{"asset":{"version":"2.0","generator":"Swifty Proteins"},""" +
            """"scene":0,"scenes":[{"name":${json(title)},"nodes":[$nodeList]}],""" +
            """"nodes":[$nodes],"meshes":[$meshes],"materials":[$materials],""" +
            """"accessors":[$accessors],"bufferViews":[$views],"buffers":[{"byteLength":${binBytes.size}}]}"""
        // The JSON chunk is padded with SPACES to a multiple of 4 bytes (still valid JSON); the BIN chunk with zeros.
        var jsonBytes = jsonText.toByteArray(Charsets.UTF_8)
        if (jsonBytes.size % 4 != 0) jsonBytes += ByteArray(4 - jsonBytes.size % 4) { ' '.code.toByte() }

        val total = 12 + 8 + jsonBytes.size + (if (binBytes.isEmpty()) 0 else 8 + binBytes.size)
        val out = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN)
        out.putInt(MAGIC).putInt(2).putInt(total)                  // "glTF", version 2, file length
        out.putInt(jsonBytes.size).putInt(CHUNK_JSON).put(jsonBytes)
        if (binBytes.isNotEmpty()) out.putInt(binBytes.size).putInt(CHUNK_BIN).put(binBytes)
        return out.array()
    }

    /**
     * sRGB (how colours are written: 0xFF0D0D) → LINEAR light, which is what glTF's baseColorFactor means. Without
     * it every colour would look washed out in a viewer: 0x90 grey is 0.565 in sRGB but 0.279 in linear light.
     */
    fun linear(rgb: Int): Triple<Float, Float, Float> {
        fun channel(v: Int): Float {
            val c = v / 255.0
            return (if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)).toFloat()
        }
        return Triple(channel((rgb shr 16) and 0xFF), channel((rgb shr 8) and 0xFF), channel(rgb and 0xFF))
    }

    private fun floats(values: FloatArray): ByteArray {
        val b = ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (v in values) b.putFloat(v)
        return b.array()
    }

    private fun ints(values: IntArray): ByteArray {
        val b = ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (v in values) b.putInt(v)
        return b.array()
    }

    private fun num(v: Float) = String.format(Locale.US, "%.6f", v)
    // min/max EXACTLY as the stored floats (validators compare them with the data): Float.toString is the shortest
    // text that reads back as the same float ("1.0E-4" is valid JSON).
    private fun vec(v: FloatArray) = "[${v[0]},${v[1]},${v[2]}]"

    /** A JSON string: quotes and backslashes escaped, control characters as \uXXXX. */
    fun json(text: String): String = buildString {
        append('"')
        for (ch in text) when {
            ch == '"' -> append("\\\"")
            ch == '\\' -> append("\\\\")
            ch.code < 0x20 -> append(String.format(Locale.US, "\\u%04x", ch.code))
            else -> append(ch)
        }
        append('"')
    }

    const val MAGIC = 0x46546C67        // "glTF" read as a little-endian int
    const val CHUNK_JSON = 0x4E4F534A   // "JSON"
    const val CHUNK_BIN = 0x004E4942    // "BIN\0"
    private const val FLOAT = 5126
    private const val UNSIGNED_INT = 5125
    private const val ARRAY_BUFFER = 34962
    private const val ELEMENT_ARRAY_BUFFER = 34963
}
