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

    // --- zoom limits (the "15P cannot be zoomed in" bug) ---

    /** What SceneView computes: closest = start distance x factor. */
    private fun closest(start: Float) = start * SceneFraming.minZoomFactor(start)

    @Test
    fun theClosestZoomIsTheSameInAngstromForALigandOfAnySize() {
        for (radius in listOf(2f, 6.5f, 12f, 30.9f, 41.4f)) {
            val start = SceneFraming.cameraDistance(radius)
            assertEquals("radius $radius", SceneFraming.MIN_CLOSE_DISTANCE, closest(start), 1e-3f)
        }
    }

    @Test
    fun theBiggestLigandCanNowGetMuchCloserThanWithSceneViewsDefault() {
        val start = SceneFraming.cameraDistance(41.4f)
        val withDefault = start * 0.15f // SceneView's DEFAULT_MIN_ZOOM_DISTANCE_FACTOR
        assertTrue("default $withDefault", withDefault > 30f)
        assertTrue(closest(start) < withDefault / 5)
    }

    @Test
    fun aTinyMoleculeCanAlwaysZoomIn() {
        // start = 6 (the minimum): 4/6 would be 0.67, so the factor is capped at 0.5 and zooming in is possible.
        assertEquals(0.5f, SceneFraming.minZoomFactor(6f), 0f)
        for (start in listOf(6f, 7f, 8f, 20f, 228f)) assertTrue(SceneFraming.minZoomFactor(start) <= 0.5f)
    }

    @Test
    fun theZoomOutLimitKeepsEvenTheBiggestLigandInsideTheFarPlane() {
        val radius = 41.4f
        val farthestPoint = SceneFraming.MAX_ZOOM_OUT_FACTOR * SceneFraming.cameraDistance(radius) + radius
        assertTrue("$farthestPoint", farthestPoint < 1000f) // SceneView's default far plane
    }

    // ---------------------------------------------------------------- orbit (double tap: document 15)

    @Test
    fun withoutFocusTheCameraOrbitsTheCentreAsBefore() {
        val o = SceneFraming.orbit(null, 40f)
        assertEquals(0f, o.x, 0f); assertEquals(0f, o.y, 0f); assertEquals(0f, o.z, 0f)
        assertEquals(40f, o.distance, 0f)
        assertEquals(SceneFraming.minZoomFactor(40f), o.minZoomFactor, 0f)
        assertEquals(SceneFraming.MAX_ZOOM_OUT_FACTOR, o.maxZoomFactor, 0f)
    }

    @Test
    fun aFocusedAtomIsTheNewCentreSeenFromCloser() {
        val o = SceneFraming.orbit(Atom("C7", "C", 3f, -2f, 5f), 120f)
        assertEquals(3f, o.x, 0f); assertEquals(-2f, o.y, 0f); assertEquals(5f, o.z, 0f)
        assertEquals(SceneFraming.FOCUS_DISTANCE, o.distance, 0f)
    }

    @Test
    fun fromAFocusedAtomOnePinchCanGoBackToTheWholeMolecule() {
        // Zoom limits in angstrom: as close as MIN_CLOSE_DISTANCE, as far as 3 x the WHOLE view (not 3 x 12).
        val o = SceneFraming.orbit(Atom("C7", "C", 0f, 0f, 0f), 120f)
        assertEquals(SceneFraming.MIN_CLOSE_DISTANCE, o.distance * o.minZoomFactor, 1e-4f)
        assertEquals(SceneFraming.MAX_ZOOM_OUT_FACTOR * 120f, o.distance * o.maxZoomFactor, 1e-3f)
    }

    @Test
    fun aTinyMoleculeIsNotPushedFartherAwayByFocusing() {
        val o = SceneFraming.orbit(Atom("ZN", "Zn", 0f, 0f, 0f), SceneFraming.MIN_DISTANCE)
        assertEquals(SceneFraming.MIN_DISTANCE, o.distance, 0f)
    }
}
