package dev.knowledge82.swiftyproteins.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ViewportTest {

    @Test
    fun portraitPanelsAboveAndBelowCoverTheTopAndTheBottom() {
        // A 1080 x 2400 screen; the window between the panels from y = 330 to y = 1400.
        val c = Viewport.cover(0f, 0f, 1080f, 2400f, 0f, 330f, 1080f, 1400f)!!
        assertEquals(Viewport.Cover(0f, 330f, 0f, 1000f, SceneFraming.aspectKey(1080f, 1070f)), c)
    }

    @Test
    fun landscapeThePanelsCoverTheRight() {
        val c = Viewport.cover(0f, 0f, 2400f, 1080f, 30f, 20f, 1350f, 1060f)!!
        assertEquals(30f, c.left); assertEquals(20f, c.top); assertEquals(1050f, c.right); assertEquals(20f, c.bottom)
    }

    @Test
    fun theScreenMayStartBelowTheStatusBar() {
        // Both rectangles in window coordinates: the screen of the app starts at y = 80 (edge to edge off).
        val c = Viewport.cover(0f, 80f, 1080f, 2400f, 0f, 400f, 1080f, 1400f)!!
        assertEquals(320f, c.top); assertEquals(1000f, c.bottom)
    }

    @Test
    fun nothingKnownOrNothingVisibleMeansWait() {
        assertNull(Viewport.cover(0f, 0f, 1080f, 2400f, 0f, 500f, 1080f, 500f))
        assertNull(Viewport.cover(0f, 0f, 0f, 0f, 0f, 0f, 10f, 10f))
    }

    @Test
    fun aWindowSpillingPastTheScreenNeverGivesANegativeCover() {
        val c = Viewport.cover(0f, 0f, 1080f, 2400f, -5f, 300f, 1090f, 2410f)!!
        assertEquals(0f, c.left); assertEquals(0f, c.right); assertEquals(0f, c.bottom)
    }
}
