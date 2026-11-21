package dev.knowledge82.swiftyproteins.scene

import dev.knowledge82.swiftyproteins.molecule.Atom
import dev.knowledge82.swiftyproteins.molecule.Bond
import dev.knowledge82.swiftyproteins.molecule.BondOrder
import dev.knowledge82.swiftyproteins.molecule.Molecule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScenePickTest {

    @Test
    fun nodeNamesBecomePicks() {
        assertEquals(ScenePick.Atom(12), ScenePick.of("atom 12"))
        assertEquals(ScenePick.Bond(3), ScenePick.of("bond 3"))
        assertEquals(ScenePick.Atom(0), ScenePick.of(ScenePick.atomName(0)))
        assertEquals(ScenePick.Bond(7), ScenePick.of(ScenePick.bondName(7)))
    }

    @Test
    fun anythingElseIsEmptySpace() {
        for (name in listOf(null, "", "atom", "atom ", "atom x", "atom -1", "bond -2", "halo", "Atom 3", " atom 3", "light")) {
            assertNull("'$name'", ScenePick.of(name))
        }
    }

    // ---------------------------------------------------------------- Drawn

    // C0 - H1 and C0 - O2; bond 0 touches a hydrogen, bond 1 does not.
    private val m = Molecule(
        "TST",
        listOf(Atom("C0", "C", 0f, 0f, 0f), Atom("H1", "H", 1f, 0f, 0f), Atom("O2", "O", 0f, 1.4f, 0f)),
        listOf(Bond(0, 1, BondOrder.SINGLE), Bond(0, 2, BondOrder.DOUBLE)),
    )

    @Test
    fun aHiddenHydrogenAndItsBondAreNotDrawn() {
        assertTrue(Drawn.atom(m, 1, showHydrogens = true))
        assertFalse(Drawn.atom(m, 1, showHydrogens = false))
        assertFalse(Drawn.bond(m, 0, showHydrogens = false, Representation.BALL_AND_STICK))
        assertTrue(Drawn.bond(m, 1, showHydrogens = false, Representation.BALL_AND_STICK))
    }

    @Test
    fun spaceFillingDrawsNoBondAtAll() {
        assertFalse(Drawn.bond(m, 1, showHydrogens = true, Representation.SPACE_FILLING))
        for (rep in listOf(Representation.BALL_AND_STICK, Representation.STICKS, Representation.WIREFRAME)) {
            assertTrue("$rep", Drawn.bond(m, 1, showHydrogens = true, rep))
        }
    }

    @Test
    fun indexesOutsideTheMoleculeAreNeverDrawn() {
        assertFalse(Drawn.atom(m, 3, true))
        assertFalse(Drawn.atom(m, -1, true))
        assertFalse(Drawn.bond(m, 2, true, Representation.BALL_AND_STICK))
    }

    @Test
    fun theSelectionShownIsOnlyWhatIsOnScreen() {
        assertEquals(ScenePick.Atom(2), Drawn.selection(m, atom = 2, bond = null, showHydrogens = false, Representation.STICKS))
        assertNull(Drawn.selection(m, atom = 1, bond = null, showHydrogens = false, Representation.STICKS))
        assertEquals(ScenePick.Bond(1), Drawn.selection(m, atom = null, bond = 1, showHydrogens = true, Representation.WIREFRAME))
        assertNull(Drawn.selection(m, atom = null, bond = 1, showHydrogens = true, Representation.SPACE_FILLING))
        assertNull(Drawn.selection(m, atom = null, bond = 0, showHydrogens = false, Representation.BALL_AND_STICK))
        assertNull(Drawn.selection(m, atom = null, bond = null, showHydrogens = true, Representation.BALL_AND_STICK))
    }

    @Test
    fun anAtomSelectionWinsIfBothWereEverSet() {
        // The screen keeps at most one, but the rule does not depend on it.
        assertEquals(ScenePick.Atom(0), Drawn.selection(m, atom = 0, bond = 1, showHydrogens = true, Representation.BALL_AND_STICK))
    }

    @Test
    fun selectingAnAtomHighlightsItsElementUnlessTheKeyChoseOne() {
        assertEquals("O", Drawn.highlight(m, chosenInKey = null, selection = ScenePick.Atom(2)))
        assertEquals("H", Drawn.highlight(m, chosenInKey = "H", selection = ScenePick.Atom(2)))   // the key wins
        assertNull(Drawn.highlight(m, chosenInKey = null, selection = ScenePick.Bond(1)))
        assertNull(Drawn.highlight(m, chosenInKey = null, selection = null))
        assertNull(Drawn.highlight(m, chosenInKey = null, selection = ScenePick.Atom(99)))       // never crashes
    }
}
