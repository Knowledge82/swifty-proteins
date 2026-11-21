package dev.knowledge82.swiftyproteins.ui

import dev.knowledge82.swiftyproteins.molecule.BondOrder
import dev.knowledge82.swiftyproteins.molecule.Molecule
import dev.knowledge82.swiftyproteins.scene.BondGeometry
import dev.knowledge82.swiftyproteins.scene.ElementStyle

/** The texts of the bond card (bonus VII.3: "bond information: type and length", document 15). Pure, tested. */
object BondText {

    /** "C1 – O2 · Double bond". */
    fun title(molecule: Molecule, index: Int): String {
        val bond = molecule.bonds[index]
        return "${molecule.atoms[bond.from].name} – ${molecule.atoms[bond.to].name} · ${orderName(bond.order)}"
    }

    /** "Carbon – Oxygen". */
    fun elements(molecule: Molecule, index: Int): String {
        val bond = molecule.bonds[index]
        return "${ElementStyle.nameOf(molecule.atoms[bond.from].element)} – ${ElementStyle.nameOf(molecule.atoms[bond.to].element)}"
    }

    /** "1.234 Å": measured between the two atoms (the coordinates are the CCD's ideal or model ones, document 03). */
    fun length(molecule: Molecule, index: Int): String {
        val bond = molecule.bonds[index]
        return AtomText.value(BondGeometry.lengthOf(molecule.atoms[bond.from], molecule.atoms[bond.to]))
    }

    /**
     * What `value_order` said, in words. CCD: SING, DOUB, TRIP, AROM; anything else (QUAD, DELO, PI, POLY, or no
     * column) was read as OTHER (document 03), and is shown as such: not guessed.
     */
    fun orderName(order: BondOrder): String = when (order) {
        BondOrder.SINGLE -> "Single bond"
        BondOrder.DOUBLE -> "Double bond"
        BondOrder.TRIPLE -> "Triple bond"
        BondOrder.AROMATIC -> "Aromatic bond"
        BondOrder.OTHER -> "Other bond"
    }
}
