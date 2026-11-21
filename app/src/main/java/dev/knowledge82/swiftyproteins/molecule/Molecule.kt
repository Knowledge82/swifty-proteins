package dev.knowledge82.swiftyproteins.molecule

/**
 * Domain model of a ligand: what the 3D viewer needs, and nothing else.
 *
 * It knows nothing about CIF, the network, Android or SceneView. The parser PRODUCES a Molecule,
 * the viewer CONSUMES it.
 */

/** Multiplicity of a bond, as written in the `value_order` column of the file. */
enum class BondOrder { SINGLE, DOUBLE, TRIPLE, AROMATIC, OTHER }

/**
 * One atom.
 *
 * @param name    name inside the ligand, e.g. "C01" or "O5'". What we show when the atom is tapped.
 * @param element normalized symbol: first letter upper case, the rest lower case ("C", "Cl", "Fe").
 *                The file writes "CL" and "FE"; the CPK color table is easier to read with "Cl", "Fe".
 * @param x,y,z   position in angstrom (1 Å = 0.1 nm). The parser centers the molecule on the origin.
 * @param charge  formal charge; 175 of the 1242 ligands have at least one charged atom (document 18).
 */
data class Atom(
    val name: String,
    val element: String,
    val x: Float,
    val y: Float,
    val z: Float,
    /** Formal charge (`_chem_comp_atom.charge`): 0 for most atoms; -1 for a carboxylate oxygen, +1 for an ammonium N. */
    val charge: Int = 0,
)

/**
 * A bond between two atoms.
 *
 * [from] and [to] are INDEXES into [Molecule.atoms], not names. Names are only used by the parser to
 * find the two atoms; after that an index gives direct access: `molecule.atoms[bond.from]`.
 */
data class Bond(
    val from: Int,
    val to: Int,
    val order: BondOrder,
)

/**
 * A whole ligand.
 *
 * The `init` block makes an INVALID molecule impossible to build: if the parser has a bug and
 * produces a bond that points to a non-existent atom, the error shows up here, next to its cause,
 * and not later as a crash inside the 3D scene.
 */
data class Molecule(
    val id: String,
    val atoms: List<Atom>,
    val bonds: List<Bond>,
    /** The chemical name from the file ("ADENOSINE-5'-TRIPHOSPHATE"), cleaned; null when the file has none. */
    val name: String? = null,
) {
    init {
        require(atoms.isNotEmpty()) { "A molecule needs at least one atom" }
        for (bond in bonds) {
            require(bond.from in atoms.indices && bond.to in atoms.indices) {
                "Bond $bond points outside the ${atoms.size} atoms"
            }
            require(bond.from != bond.to) { "Bond $bond joins an atom with itself" }
        }
    }
}
