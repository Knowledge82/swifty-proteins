package dev.knowledge82.swiftyproteins.molecule

/**
 * The molecular formula of a ligand, counted from ITS atoms (bonus VII.5, document 15). Pure Kotlin, tested, and
 * checked against the official formula of RCSB for the 1242 ligands of the corpus.
 *
 * Order: the HILL SYSTEM, the one chemical databases and catalogues use. With carbon: C first, then H, then every
 * other element alphabetically. Without carbon: ALL elements alphabetically, H included (HCl is written "ClH").
 * A count of 1 is not written: "CH4O", not "C1H4O1".
 *
 * Counted, not read from the file's `_chem_comp.formula`: what the formula says is then exactly what is drawn.
 */
object MolecularFormula {

    /** One element and how many atoms of it. */
    data class Part(val element: String, val count: Int)

    /** The parts in Hill order. */
    fun of(atoms: List<Atom>): List<Part> {
        val counts = atoms.groupingBy { it.element }.eachCount()
        val withCarbon = "C" in counts
        return counts.entries
            .sortedWith(compareBy<Map.Entry<String, Int>>({ if (withCarbon) hillRank(it.key) else 0 }, { it.key }))
            .map { Part(it.key, it.value) }
    }

    /**
     * "C10H16N5O13P3": for messages and plain text. The SCREEN does not use Unicode subscripts (₁₀): neither of our
     * fonts has them, and Android would borrow them from another font in the middle of the formula. It lowers and
     * shrinks our own digits instead, with [of] (document 15).
     */
    fun plain(atoms: List<Atom>): String = of(atoms).joinToString("") { it.element + countText(it.count) }

    /** "C10 H16 N5 O13 P3": the style of the CCD's `_chem_comp.formula` (used to compare against it). */
    fun spaced(atoms: List<Atom>): String = of(atoms).joinToString(" ") { it.element + countText(it.count) }

    /**
     * "C10H16N5O13P3" back into parts (the formulas of the list's index, document 21), or null if it is not a formula
     * written by [plain]: every part is an element symbol (an upper-case letter, maybe a lower-case one) and an optional
     * count of 2 or more. Nothing is skipped silently: one wrong character and the answer is null.
     */
    fun parse(plain: String): List<Part>? {
        if (plain.isEmpty()) return null
        val parts = mutableListOf<Part>()
        var i = 0
        while (i < plain.length) {
            if (plain[i] !in 'A'..'Z') return null
            var end = i + 1
            if (end < plain.length && plain[end] in 'a'..'z') end++
            val element = plain.substring(i, end)
            var digits = end
            while (digits < plain.length && plain[digits] in '0'..'9') digits++
            val count = if (digits == end) 1 else plain.substring(end, digits).toIntOrNull()?.takeIf { it >= 2 } ?: return null
            parts += Part(element, count)
            i = digits
        }
        return parts
    }

    private fun hillRank(element: String) = when (element) {
        "C" -> 0
        "H" -> 1
        else -> 2
    }

    private fun countText(count: Int) = if (count == 1) "" else count.toString()
}
