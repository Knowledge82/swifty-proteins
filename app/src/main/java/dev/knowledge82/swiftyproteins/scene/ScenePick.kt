package dev.knowledge82.swiftyproteins.scene

import dev.knowledge82.swiftyproteins.molecule.Molecule

/**
 * What a tap on the 3D scene hit (document 15). The scene names its nodes "atom 12" and "bond 3"; SceneView gives
 * back the NODE that was touched, and this turns its name into something the screen can use. Pure, tested.
 */
sealed interface ScenePick {
    data class Atom(val index: Int) : ScenePick
    data class Bond(val index: Int) : ScenePick

    companion object {
        const val ATOM_PREFIX = "atom "
        const val BOND_PREFIX = "bond "

        fun atomName(index: Int) = "$ATOM_PREFIX$index"
        fun bondName(index: Int) = "$BOND_PREFIX$index"

        /** The pick of a node name; null for empty space, a node without a name or anything else. */
        fun of(nodeName: String?): ScenePick? {
            if (nodeName == null) return null
            nodeName.removePrefixOrNull(ATOM_PREFIX)?.toIntOrNull()?.takeIf { it >= 0 }?.let { return Atom(it) }
            nodeName.removePrefixOrNull(BOND_PREFIX)?.toIntOrNull()?.takeIf { it >= 0 }?.let { return Bond(it) }
            return null
        }

        private fun String.removePrefixOrNull(prefix: String) = if (startsWith(prefix)) substring(prefix.length) else null
    }
}

/** What is DRAWN right now, so the screen never highlights or describes something that is not on screen. Pure, tested. */
object Drawn {

    /** An atom is drawn unless it is a hydrogen and hydrogens are hidden. */
    fun atom(molecule: Molecule, index: Int, showHydrogens: Boolean): Boolean =
        index in molecule.atoms.indices && (showHydrogens || molecule.atoms[index].element != "H")

    /** A bond is drawn when the model draws bonds (not space-filling) and BOTH its atoms are drawn. */
    fun bond(molecule: Molecule, index: Int, showHydrogens: Boolean, representation: Representation): Boolean =
        index in molecule.bonds.indices && representation.bondRadius != null &&
            atom(molecule, molecule.bonds[index].from, showHydrogens) && atom(molecule, molecule.bonds[index].to, showHydrogens)

    /**
     * The selection to SHOW, from what the screen remembers ([atom] or [bond], at most one is set): null when it
     * points at something that is not drawn now (a hidden hydrogen, a bond in space-filling). The remembered index is
     * kept: showing hydrogens again, or going back to a model with bonds, brings the selection back.
     */
    fun selection(molecule: Molecule, atom: Int?, bond: Int?, showHydrogens: Boolean, representation: Representation): ScenePick? = when {
        atom != null -> ScenePick.Atom(atom).takeIf { atom(molecule, atom, showHydrogens) }
        bond != null -> ScenePick.Bond(bond).takeIf { bond(molecule, bond, showHydrogens, representation) }
        else -> null
    }
}
