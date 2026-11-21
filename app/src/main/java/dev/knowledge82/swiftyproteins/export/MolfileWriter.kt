package dev.knowledge82.swiftyproteins.export

import dev.knowledge82.swiftyproteins.molecule.BondOrder
import dev.knowledge82.swiftyproteins.molecule.Molecule
import java.util.Locale

/**
 * The molecule as an **MDL molfile, version V2000** (`.mol`): THE exchange format of chemistry software (RDKit, Open
 * Babel, ChemDraw, PyMOL, Avogadro...). Bonus VII.5 "export as a 3D model file", the chemist's way (document 18).
 *
 * A V2000 molfile is plain text with FIXED-WIDTH columns (it comes from punched-card days: the position of each
 * character matters, spaces included):
 *
 * ```
 * ATP                                    <- 1: name
 *   SwiftyPr          3D                 <- 2: program; "3D" in columns 21-22 = the coordinates are 3D
 * ADENOSINE-5'-TRIPHOSPHATE              <- 3: comment
 *  47 49  0  0  0  0  0  0  0  0999 V2000  <- counts: atoms, bonds, ... "999 V2000"
 *     1.2340   -0.5000    2.0000 P   0  0  0  0  0  0  0  0  0  0  0  0   <- one line per atom
 *   1  2  2  0  0  0  0                  <- one line per bond: atom, atom (1-based), type
 * M  CHG  2   5  -1  12   1              <- formal charges
 * M  END
 * ```
 *
 * Pure Kotlin, tested; checked with RDKit on the 1242 ligands (document 18).
 */
object MolfileWriter {

    /** V2000 counts atoms and bonds in 3 columns: at most 999. Real ligands have up to 256 atoms. */
    const val MAX_ITEMS = 999

    /** @throws IllegalArgumentException for more than [MAX_ITEMS] atoms or bonds (that needs V3000) */
    fun write(molecule: Molecule): String {
        require(molecule.atoms.size <= MAX_ITEMS && molecule.bonds.size <= MAX_ITEMS) {
            "V2000 holds at most $MAX_ITEMS atoms and bonds; ${molecule.id} has ${molecule.atoms.size} and ${molecule.bonds.size}"
        }
        val out = StringBuilder()
        out.append(line(molecule.id)).append('\n')
        // IIPPPPPPPPMMDDYYHHmmddSSssssssssssEEEEEEEEEEEERRRRRR: user initials (2), program (8), date (10), "3D" (2).
        // The date is left blank on purpose: the same molecule always gives the same file.
        out.append(String.format(Locale.US, "%-2s%-8s%-10s%-2s", "", "SwiftyPr", "", "3D")).append('\n')
        out.append(line(molecule.name.orEmpty())).append('\n')
        out.append(
            String.format(Locale.US, "%3d%3d%3d%3d%3d%3d%3d%3d%3d%3d%3d%6s", molecule.atoms.size, molecule.bonds.size, 0, 0, 0, 0, 0, 0, 0, 0, 999, "V2000")
        ).append('\n')
        for (atom in molecule.atoms) {
            out.append(
                String.format(
                    Locale.US, "%10.4f%10.4f%10.4f %-3s%2d%3d%3d%3d%3d%3d%3d%3d%3d%3d%3d%3d",
                    atom.x, atom.y, atom.z, symbolOf(atom.element), 0, chargeCode(atom.charge), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                )
            ).append('\n')
        }
        for (bond in molecule.bonds) {
            // 111222tttsssxxxrrrccc: the atoms are 1-BASED in the file (our indexes start at 0).
            out.append(String.format(Locale.US, "%3d%3d%3d%3d%3d%3d%3d", bond.from + 1, bond.to + 1, typeOf(bond.order), 0, 0, 0, 0)).append('\n')
        }
        // Charges again in the properties block ("M  CHG"): when it is present, readers use it and IGNORE the old
        // atom-block codes, and it allows -15..+15. At most 8 atoms per line.
        val charged = molecule.atoms.withIndex().filter { it.value.charge != 0 }
        for (chunk in charged.chunked(8)) {
            out.append(String.format(Locale.US, "M  CHG%3d", chunk.size))
            for ((index, atom) in chunk) out.append(String.format(Locale.US, " %3d %3d", index + 1, atom.charge))
            out.append('\n')
        }
        out.append("M  END\n")
        return out.toString()
    }

    /** V2000 bond types: 1 single, 2 double, 3 triple, 4 aromatic, 8 "any" (what we read as OTHER is not guessed). */
    fun typeOf(order: BondOrder): Int = when (order) {
        BondOrder.SINGLE -> 1
        BondOrder.DOUBLE -> 2
        BondOrder.TRIPLE -> 3
        BondOrder.AROMATIC -> 4
        BondOrder.OTHER -> 8
    }

    /**
     * The OLD charge code of the atom block (kept for very old readers): 1 = +3, 2 = +2, 3 = +1, 0 = none, 5 = -1,
     * 6 = -2, 7 = -3. Anything else is written as 0 there and only in "M  CHG".
     */
    fun chargeCode(charge: Int): Int = when (charge) {
        3 -> 1
        2 -> 2
        1 -> 3
        -1 -> 5
        -2 -> 6
        -3 -> 7
        else -> 0
    }

    /** Our unknown element "X" is written "*" ("any atom"); real symbols as they are ("C", "Cl"). */
    private fun symbolOf(element: String) = if (element == "X") "*" else element

    /** A header line: at most 80 characters, and never a line break inside (it would shift every line after it). */
    private fun line(text: String) = text.replace('\n', ' ').replace('\r', ' ').take(80)
}
