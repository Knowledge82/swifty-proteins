package dev.knowledge82.swiftyproteins.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RepresentationTest {

    @Test
    fun ballAndStickKeepsTheBallsOfBefore() {
        for (e in listOf("H", "C", "N", "O", "S", "Fe", "X")) {
            assertEquals(e, ElementStyle.radiusOf(e), Representation.BALL_AND_STICK.atomRadius(e, bonded = true), 0f)
        }
        assertEquals(0.12f, Representation.BALL_AND_STICK.bondRadius)
    }

    @Test
    fun spaceFillingUsesVanDerWaalsAndDrawsNoBonds() {
        assertEquals(1.70f, Representation.SPACE_FILLING.atomRadius("C", bonded = true), 0f)
        assertEquals(1.10f, Representation.SPACE_FILLING.atomRadius("H", bonded = true), 0f)
        assertNull(Representation.SPACE_FILLING.bondRadius)
    }

    @Test
    fun inSpaceFillingNeighboursOverlapThatIsTheBond() {
        // A C-C single bond is ~1.54 A; two carbon spheres of 1.70 overlap by more than a whole radius.
        val r = Representation.SPACE_FILLING.atomRadius("C", true)
        assertTrue(2 * r > 1.54f + r)
    }

    @Test
    fun sticksAndWireframeHaveJointsAsThickAsTheirBonds() {
        for (rep in listOf(Representation.STICKS, Representation.WIREFRAME)) {
            assertEquals("$rep", rep.bondRadius!!, rep.atomRadius("C", bonded = true), 0f)
            assertTrue("$rep", rep.bondsInAtomColours)
        }
        assertTrue(Representation.WIREFRAME.bondRadius!! < Representation.STICKS.bondRadius!!)
        assertFalse(Representation.BALL_AND_STICK.bondsInAtomColours)
    }

    @Test
    fun aLoneAtomNeverVanishesInThinModels() {
        // An ion (or an atom whose only neighbours are hidden hydrogens) has no stick to show it.
        for (rep in listOf(Representation.STICKS, Representation.WIREFRAME)) {
            assertEquals("$rep", Representation.LONE_ATOM_RADIUS, rep.atomRadius("Zn", bonded = false), 0f)
        }
    }

    @Test
    fun aHaloIsAlwaysVisibleEvenAroundAWireJoint() {
        assertEquals(Representation.MIN_HALO_RADIUS, Representation.WIREFRAME.haloRadius("C", bonded = true), 0f)
        assertEquals(1.70f * 1.5f, Representation.SPACE_FILLING.haloRadius("C", bonded = true), 1e-6f)
        for (rep in Representation.entries) for (bonded in listOf(true, false)) {
            assertTrue("$rep $bonded", rep.haloRadius("H", bonded) > rep.atomRadius("H", bonded))
        }
    }

    @Test
    fun theKeyCyclesThroughAllFourAndBack() {
        var r = Representation.BALL_AND_STICK
        val seen = mutableListOf<Representation>()
        repeat(4) { seen += r; r = r.next() }
        assertEquals(Representation.entries, seen)
        assertEquals(Representation.BALL_AND_STICK, r)
    }
}
