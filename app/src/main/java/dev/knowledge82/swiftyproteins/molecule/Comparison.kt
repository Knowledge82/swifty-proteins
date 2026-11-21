package dev.knowledge82.swiftyproteins.molecule

import java.util.Locale
import kotlin.math.sqrt

/**
 * Two molecules side by side (bonus VII.5 "comparison view of two molecules", document 24): what each one is, in
 * numbers, and how much their composition has in common. Pure, tested.
 *
 * Everything is COUNTED from the atoms and bonds that are drawn, never read from somewhere else, so the table never
 * says something the 3D view contradicts (the same rule as the formula, document 15).
 */
object Comparison {

    /**
     * One line of the table: what is compared, the value of each molecule as text, and whether the two are the same.
     * [larger] says which one has more (-1 the first, 1 the second, 0 equal or not comparable), for the eye.
     */
    data class Row(val label: String, val first: String, val second: String, val larger: Int) {
        val same: Boolean get() = first == second
    }

    /** How many atoms of [element] each molecule has. */
    data class ElementRow(val element: String, val first: Int, val second: Int)

    /** The table, in this order: formula, mass, atoms, heavy atoms, bonds, rings, aromatic bonds, charge, span. */
    fun rows(a: Molecule, b: Molecule): List<Row> = listOf(
        Row("Formula", MolecularFormula.plain(a.atoms), MolecularFormula.plain(b.atoms), 0),
        massRow(a, b),
        countRow("Atoms", a.atoms.size, b.atoms.size),
        countRow("Heavy atoms", heavyAtoms(a), heavyAtoms(b)),
        countRow("Bonds", a.bonds.size, b.bonds.size),
        countRow("Rings", rings(a), rings(b)),
        countRow("Aromatic bonds", a.bonds.count { it.order == BondOrder.AROMATIC }, b.bonds.count { it.order == BondOrder.AROMATIC }),
        Row("Charge", charge(netCharge(a)), charge(netCharge(b)), compareValues(netCharge(b), netCharge(a)).coerceIn(-1, 1)),
        spanRow(a, b),
    )

    /**
     * Every element of either molecule, in Hill order (C, H, then alphabetical; alphabetical when neither has carbon),
     * with how many atoms of it each one has (0 when it has none).
     */
    fun elements(a: Molecule, b: Molecule): List<ElementRow> {
        val ca = a.atoms.groupingBy { it.element }.eachCount()
        val cb = b.atoms.groupingBy { it.element }.eachCount()
        val withCarbon = "C" in ca || "C" in cb
        val all = (ca.keys + cb.keys).sortedWith(compareBy({ if (withCarbon) hillRank(it) else 0 }, { it }))
        return all.map { ElementRow(it, ca[it] ?: 0, cb[it] ?: 0) }
    }

    /**
     * How much the two COMPOSITIONS have in common, 0..100 %: for each element the smaller count over the larger,
     * summed (the Tanimoto/Jaccard index of two multisets). Same formula = 100 %; nothing in common = 0 %. It says
     * nothing about the SHAPE: two isomers (same atoms, other bonds) are 100 % here (document 24, §3.3).
     */
    fun compositionMatch(a: Molecule, b: Molecule): Int {
        val rows = elements(a, b)
        val shared = rows.sumOf { minOf(it.first, it.second) }
        val either = rows.sumOf { maxOf(it.first, it.second) }
        return if (either == 0) 0 else Math.round(100.0 * shared / either).toInt()
    }

    /** "Composition match 51 %". */
    fun matchLine(a: Molecule, b: Molecule): String = "Composition match ${compositionMatch(a, b)} %"

    /** The atoms that are not hydrogen: the "size" chemists usually quote. */
    fun heavyAtoms(m: Molecule): Int = m.atoms.count { it.element != "H" }

    /**
     * The number of rings: bonds - atoms + pieces (the cyclomatic number of the bond graph). A chain has none; each
     * bond that closes a loop adds one. ATP: 49 - 47 + 1 = 3 (two in adenine, one in the ribose). A double bond is one
     * bond here, as in the file, so it does not count as a ring.
     */
    fun rings(m: Molecule): Int = m.bonds.size - m.atoms.size + pieces(m)

    /** How many separate pieces the molecule is (atoms joined by bonds): 1 for most ligands, more for a salt. */
    fun pieces(m: Molecule): Int {
        val parent = IntArray(m.atoms.size) { it }
        fun root(i: Int): Int {
            var r = i
            while (parent[r] != r) r = parent[r]
            var j = i
            while (parent[j] != r) { val next = parent[j]; parent[j] = r; j = next }   // shorten the path for next time
            return r
        }
        var count = m.atoms.size
        for (bond in m.bonds) {
            val ra = root(bond.from)
            val rb = root(bond.to)
            if (ra != rb) { parent[ra] = rb; count-- }
        }
        return count
    }

    /** The sum of the formal charges. */
    fun netCharge(m: Molecule): Int = m.atoms.sumOf { it.charge }

    /** The longest distance between two atoms, in angstrom: how big the molecule is from end to end. */
    fun span(m: Molecule): Float {
        var best = 0f
        val atoms = m.atoms
        for (i in atoms.indices) {
            val p = atoms[i]
            for (j in i + 1 until atoms.size) {
                val q = atoms[j]
                val dx = p.x - q.x; val dy = p.y - q.y; val dz = p.z - q.z
                val d2 = dx * dx + dy * dy + dz * dz
                if (d2 > best) best = d2
            }
        }
        return sqrt(best)
    }

    private fun countRow(label: String, a: Int, b: Int) = Row(label, a.toString(), b.toString(), compareValues(b, a).coerceIn(-1, 1))

    private fun massRow(a: Molecule, b: Molecule): Row {
        val ma = AtomicMass.molar(a.atoms)
        val mb = AtomicMass.molar(b.atoms)
        val ta = mass(ma)
        val tb = mass(mb)
        // Compared as shown (one decimal): two masses that print the same are "the same".
        val larger = if (ma == null || mb == null || ta == tb) 0 else compareValues(mb, ma).coerceIn(-1, 1)
        return Row("Mass", ta, tb, larger)
    }

    private fun spanRow(a: Molecule, b: Molecule): Row {
        val ta = angstrom(span(a))
        val tb = angstrom(span(b))
        return Row("Span", ta, tb, if (ta == tb) 0 else compareValues(span(b), span(a)).coerceIn(-1, 1))
    }

    /** "507.2 g/mol", or "?" when an element has no known weight (the unknown ligand UNL). */
    fun mass(value: Double?): String = if (value == null) "?" else String.format(Locale.ROOT, "%.1f g/mol", value)

    /** "12.3 Å". */
    fun angstrom(value: Float): String = String.format(Locale.ROOT, "%.1f Å", value)

    /** "0", "+1", "-2": a charge always says its sign. */
    fun charge(value: Int): String = if (value > 0) "+$value" else value.toString()

    private fun hillRank(element: String): Int = when (element) { "C" -> 0; "H" -> 1; else -> 2 }
}
