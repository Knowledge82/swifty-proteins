package dev.knowledge82.swiftyproteins.share

import dev.knowledge82.swiftyproteins.molecule.Atom
import dev.knowledge82.swiftyproteins.molecule.Bond
import dev.knowledge82.swiftyproteins.molecule.BondOrder
import dev.knowledge82.swiftyproteins.molecule.Molecule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareTextTest {

    private fun molecule(id: String, atoms: Int, bonds: Int) = Molecule(
        id,
        List(atoms) { Atom("C$it", "C", it.toFloat(), 0f, 0f) },
        List(bonds) { Bond(0, 1, BondOrder.SINGLE) },
    )

    @Test
    fun theCaptionNamesTheAppTheLigandItsFormulaAndItsSize() {
        assertEquals("Swifty Proteins · 001 · C87 · 87 atoms · 90 bonds", ShareText.caption(molecule("001", 87, 90)))
    }

    @Test
    fun aSingleAtomIonIsWrittenInSingular() {
        assertEquals("Swifty Proteins · ZN · C · 1 atom · 0 bonds", ShareText.caption(molecule("ZN", 1, 0)))
    }

    @Test
    fun theMessageHasNameFormulaAndSize() {
        // Bonus VII.5 asks for "name, number of atoms, formula" in the message.
        val atp = molecule("ATP", 2, 1).copy(name = "ADENOSINE-5'-TRIPHOSPHATE")
        assertEquals(
            "Ligand ATP · ADENOSINE-5'-TRIPHOSPHATE\nC2 · 2 atoms · 1 bond\nViewed in Swifty Proteins",
            ShareText.message(atp),
        )
    }

    @Test
    fun withoutANameTheMessageStillSaysWhichLigand() {
        assertEquals("Ligand ZN\nC · 1 atom · 0 bonds\nViewed in Swifty Proteins", ShareText.message(molecule("ZN", 1, 0)))
    }

    @Test
    fun theCaptionUsesPlainDigitsNeverSubscripts() {
        // The band is drawn with a font that may lack U+2080..U+2089.
        assertTrue(ShareText.caption(molecule("001", 12, 0)).none { it in '₀'..'₉' })
    }

    @Test
    fun theShareSheetTitleNamesTheLigand() {
        assertEquals("Share ligand ATP", ShareText.chooserTitle(molecule("ATP", 2, 1)))
    }

    @Test
    fun everyFailureHasAReadableMessage() {
        for (f in CaptureFailure.values()) assertTrue("$f", f.userMessage().length in 10..80)
        assertEquals(CaptureFailure.values().size, CaptureFailure.values().map { it.userMessage() }.toSet().size)
    }

    @Test
    fun aSecondTapWhileWorkingIsIgnored() {
        assertFalse(ShareStatus.Working.canStart)
        assertTrue(ShareStatus.Idle.canStart)
        assertTrue(ShareStatus.Done.canStart)
        assertTrue(ShareStatus.Failed(CaptureFailure.TIMEOUT).canStart)
    }

    @Test
    fun theStatusLineFollowsTheDesign() {
        assertNull(ShareStatus.Idle.label)
        assertEquals("Preparing pict-capture…", ShareStatus.Working.label)
        assertEquals("Pict-capture ready", ShareStatus.Done.label)
        assertEquals(CaptureFailure.NOT_READY.userMessage(), ShareStatus.Failed(CaptureFailure.NOT_READY).label)
    }
}
