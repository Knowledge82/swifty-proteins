package dev.knowledge82.swiftyproteins.scene

import dev.knowledge82.swiftyproteins.cif.CifParser
import dev.knowledge82.swiftyproteins.cif.ParseResult
import dev.knowledge82.swiftyproteins.molecule.Atom
import dev.knowledge82.swiftyproteins.molecule.Molecule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneFramingTest {

    private fun molecule(vararg atoms: Atom) = Molecule("T", atoms.toList(), emptyList())

    @Test
    fun theRadiusIsTheFarthestCenterPlusItsBall() {
        val m = molecule(Atom("A", "C", 0f, 0f, 0f), Atom("B", "O", 3f, 4f, 0f))
        assertEquals(5f + ElementStyle.radiusOf("O"), SceneFraming.radiusOf(m), 1e-5f)
    }

    @Test
    fun theFarthestAtomCountsEvenWhenItIsNotTheLastOne() {
        val m = molecule(Atom("A", "C", 9f, 0f, 0f), Atom("B", "C", 1f, 0f, 0f), Atom("C", "C", 0f, 0f, 0f))
        assertEquals(9f + 0.40f, SceneFraming.radiusOf(m), 1e-5f)
    }

    @Test
    fun aSingleAtomAtTheCenterHasTheRadiusOfItsBall() {
        assertEquals(0.40f, SceneFraming.radiusOf(molecule(Atom("A", "C", 0f, 0f, 0f))), 1e-6f)
    }

    @Test
    fun hydrogensCountEvenWhenTheyAreHidden() {
        // The function does not know about hiding: the framing must not jump when the "H" button is pressed.
        val withH = molecule(Atom("A", "C", 0f, 0f, 0f), Atom("H", "H", 10f, 0f, 0f))
        assertEquals(10f + 0.25f, SceneFraming.radiusOf(withH), 1e-5f)
    }

    @Test
    fun aSmallMoleculeStillGetsTheMinimumDistance() {
        assertEquals(SceneFraming.MIN_DISTANCE, SceneFraming.cameraDistance(0.4f), 0f)
    }

    @Test
    fun aBigMoleculeGetsADistanceProportionalToItsRadius() {
        assertEquals(5.5f * 41.4f, SceneFraming.cameraDistance(41.4f), 1e-3f)
    }

    @Test
    fun theDistanceNeverDecreasesWhenTheMoleculeGrows() {
        var previous = 0f
        for (r in listOf(0.4f, 1f, 2f, 5f, 10f, 20f, 40f)) {
            val d = SceneFraming.cameraDistance(r)
            assertTrue("$r -> $d after $previous", d >= previous)
            previous = d
        }
    }

    @Test
    fun theCameraIsAlwaysOutsideTheMolecule() {
        for (r in listOf(0.4f, 1f, 5f, 41.4f)) assertTrue(SceneFraming.cameraDistance(r) > r)
    }

    @Test
    fun theRealLigand001FitsInAFewAngstromAndGetsASensibleDistance() {
        val text = javaClass.getResource("/cif/001.cif")!!.readText()
        val molecule = (CifParser.parse(text) as ParseResult.Success).molecule
        val radius = SceneFraming.radiusOf(molecule)
        assertTrue("radius $radius", radius in 3f..12f)
        val distance = SceneFraming.cameraDistance(radius)
        assertTrue("distance $distance", distance in 15f..70f)
    }
}
