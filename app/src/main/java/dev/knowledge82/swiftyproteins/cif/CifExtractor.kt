package dev.knowledge82.swiftyproteins.cif

import dev.knowledge82.swiftyproteins.molecule.Atom
import dev.knowledge82.swiftyproteins.molecule.Bond
import dev.knowledge82.swiftyproteins.molecule.BondOrder
import dev.knowledge82.swiftyproteins.molecule.Molecule
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Layer 3 of the parser: turns the tables of a [CifBlock] into a [Molecule]. This is the only layer
 * that knows CHEMISTRY: which column is the atom name, which one is the element, what "SING" means.
 *
 * Policy for bad data: STRICT. Anything doubtful is a [CifFailure], never a guess. A molecule that
 * is silently incomplete is worse than an honest "corrupt file" message.
 */
object CifExtractor {

    const val MAX_ATOMS = 2_000
    const val MAX_BONDS = 5_000

    // The real ligands have at most 256 atoms and a radius of 41 Å. A coordinate beyond this is garbage.
    private const val MAX_ABS_COORDINATE = 10_000.0

    // A covalent bond is 1-2.5 Å long. In the 1242 real ligands the longest correct one is below 3 Å, while
    // five ligands (HEM, HEA, HEC, B12, AV2) have an IDEAL set with bonds of 8-14 Å. 5 Å separates both worlds.
    private const val MAX_PLAUSIBLE_BOND_LENGTH = 5.0

    private const val ATOM_ID = "_chem_comp_atom.atom_id"
    private const val ELEMENT = "_chem_comp_atom.type_symbol"
    private const val BOND_FIRST = "_chem_comp_bond.atom_id_1"
    private const val BOND_SECOND = "_chem_comp_bond.atom_id_2"
    private const val BOND_ORDER = "_chem_comp_bond.value_order"

    // Two candidate coordinate sets, x/y/z each. The order of this list IS the policy: first the ideal one.
    private val COORDINATE_SETS = listOf(
        listOf(
            "_chem_comp_atom.pdbx_model_cartn_x_ideal",
            "_chem_comp_atom.pdbx_model_cartn_y_ideal",
            "_chem_comp_atom.pdbx_model_cartn_z_ideal",
        ),
        listOf(
            "_chem_comp_atom.model_cartn_x",
            "_chem_comp_atom.model_cartn_y",
            "_chem_comp_atom.model_cartn_z",
        ),
    )

    private class Point(val x: Double, val y: Double, val z: Double)

    /** @throws CifFormatException if the block does not describe a usable molecule. */
    fun extract(block: CifBlock): Molecule {
        // ---- atoms -------------------------------------------------------------------------
        val atomTable = block.tableWith(ATOM_ID) ?: throw fail(CifFailure.MissingColumn(ATOM_ID))
        val rows = atomTable.rows
        if (rows.isEmpty()) throw fail(CifFailure.NoAtoms)
        if (rows.size > MAX_ATOMS) throw fail(CifFailure.TooBig("atoms", rows.size, MAX_ATOMS))

        val nameColumn = atomTable.column(ATOM_ID)
        val elementColumn = atomTable.column(ELEMENT)

        // Names and elements first: the bonds refer to atoms BY NAME and need this map.
        val indexByName = HashMap<String, Int>()
        val names = ArrayList<String>(rows.size)
        val elements = ArrayList<String>(rows.size)
        for ((index, row) in rows.withIndex()) {
            val name = row[nameColumn]
            val element = normalizeElement(row[elementColumn])
                ?: throw fail(CifFailure.BadElement(name, row[elementColumn]))
            // put() returns the previous value of that key: not null means the name was already taken.
            if (indexByName.put(name, index) != null) throw fail(CifFailure.DuplicateAtomName(name))
            names += name
            elements += element
        }

        // A ligand with a single atom (an ion) has no bonds and may not have the table at all.
        val bondTable = block.tableWith(BOND_FIRST)
        val bonds = if (bondTable == null) emptyList() else readBonds(bondTable, indexByName)

        // The bonds come BEFORE the coordinates because they are needed to judge which set is believable.
        val points = chooseCoordinates(atomTable, bonds) ?: throw fail(CifFailure.NoCompleteCoordinates)

        // The centroid is the average position. Moving it to (0, 0, 0) puts the molecule in front of
        // the camera whatever the reference frame of the file was (it can be 226 Å away from the origin).
        val centerX = points.sumOf { it.x } / points.size
        val centerY = points.sumOf { it.y } / points.size
        val centerZ = points.sumOf { it.z } / points.size

        val atoms = rows.indices.map { index ->
            val p = points[index]
            Atom(
                name = names[index],
                element = elements[index],
                x = (p.x - centerX).toFloat(),
                y = (p.y - centerY).toFloat(),
                z = (p.z - centerZ).toFloat(),
            )
        }

        return Molecule(block.name, atoms, bonds)
    }

    private fun readBonds(table: CifTable, indexByName: Map<String, Int>): List<Bond> {
        if (table.rows.size > MAX_BONDS) throw fail(CifFailure.TooBig("bonds", table.rows.size, MAX_BONDS))
        val firstColumn = table.column(BOND_FIRST)
        val secondColumn = table.column(BOND_SECOND)
        val orderColumn = table.indexOf(BOND_ORDER)   // optional: without it every bond is OTHER

        return table.rows.map { row ->
            val firstName = row[firstColumn]
            val secondName = row[secondColumn]
            val from = indexByName[firstName] ?: throw fail(CifFailure.UnknownAtomInBond(firstName))
            val to = indexByName[secondName] ?: throw fail(CifFailure.UnknownAtomInBond(secondName))
            if (from == to) throw fail(CifFailure.BondToItself(firstName))
            Bond(from, to, bondOrderOf(orderColumn?.let { row[it] }))
        }
    }

    /**
     * Picks ONE coordinate set for the whole ligand (decisions D10 and D13). The candidates are tried in order
     * (ideal, then model) and the first one that is COMPLETE (every atom has the three numbers) and
     * BELIEVABLE (no bond longer than [MAX_PLAUSIBLE_BOND_LENGTH]) wins. If there is a complete set but none
     * is believable, the first complete one is used anyway: a stray hydrogen is better than no molecule.
     * Null if no set is complete.
     *
     * The two sets live in different reference frames, so mixing them atom by atom would scatter the molecule.
     */
    private fun chooseCoordinates(table: CifTable, bonds: List<Bond>): List<Point>? {
        var firstComplete: List<Point>? = null
        for (tags in COORDINATE_SETS) {
            val points = readCompleteSet(table, tags) ?: continue
            if (longestBond(points, bonds) <= MAX_PLAUSIBLE_BOND_LENGTH) return points
            if (firstComplete == null) firstComplete = points
        }
        return firstComplete
    }

    /** The x/y/z of every atom for one set, or null if a column is absent or ANY atom lacks a usable number. */
    private fun readCompleteSet(table: CifTable, tags: List<String>): List<Point>? {
        val xColumn = table.indexOf(tags[0]) ?: return null
        val yColumn = table.indexOf(tags[1]) ?: return null
        val zColumn = table.indexOf(tags[2]) ?: return null
        return table.rows.map { row ->
            // One unusable number in ONE atom and the whole set is discarded.
            Point(
                parseCoordinate(row[xColumn]) ?: return null,
                parseCoordinate(row[yColumn]) ?: return null,
                parseCoordinate(row[zColumn]) ?: return null,
            )
        }
    }

    private fun longestBond(points: List<Point>, bonds: List<Bond>): Double =
        bonds.maxOfOrNull { bond ->
            val a = points[bond.from]
            val b = points[bond.to]
            sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y) + (a.z - b.z) * (a.z - b.z))
        } ?: 0.0   // no bonds (an ion): nothing to doubt

    /** A usable number, or null for `?`, `.`, text, NaN, infinities and absurd magnitudes. */
    private fun parseCoordinate(text: String): Double? {
        // toDoubleOrNull alone also accepts "NaN", "Infinity", "0x1p3" and "1.5f". Only these characters
        // can be part of a plain decimal number such as -3.053 or 1e-3.
        if (text.isEmpty() || !text.all { it in "0123456789+-.eE" }) return null
        val value = text.toDoubleOrNull() ?: return null
        return if (value.isFinite() && abs(value) <= MAX_ABS_COORDINATE) value else null
    }

    /** "CL" -> "Cl", "fe" -> "Fe", "C" -> "C". Null if it cannot be an element symbol. */
    private fun normalizeElement(raw: String): String? {
        if (raw.length !in 1..3) return null
        if (!raw.all { it in 'A'..'Z' || it in 'a'..'z' }) return null
        return raw.substring(0, 1).uppercase() + raw.substring(1).lowercase()
    }

    private fun bondOrderOf(raw: String?): BondOrder = when (raw?.uppercase()) {
        "SING" -> BondOrder.SINGLE
        "DOUB" -> BondOrder.DOUBLE
        "TRIP" -> BondOrder.TRIPLE
        "AROM" -> BondOrder.AROMATIC
        else -> BondOrder.OTHER   // "QUAD", "DELO", missing column... a bond is drawn anyway
    }

    /** The position of a REQUIRED column, or a failure that names it. */
    private fun CifTable.column(tag: String): Int =
        indexOf(tag) ?: throw fail(CifFailure.MissingColumn(tag))

    private fun fail(failure: CifFailure) = CifFormatException(failure)
}
