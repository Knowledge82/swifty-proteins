package dev.knowledge82.swiftyproteins.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelGeometryTest {

    private val cuts = listOf(6f, 9f, 11f, 12f)
    private val paddings = listOf(4f, 6f, 8f, 10f, 12f, 14f, 16f, 20f)

    @Test
    fun theContentNeverCoversARivet() {
        // The search box (padding 6) hid the bottom-left rivet: its content started inside the rivet band.
        for (cut in cuts) for (pad in paddings) for (label in listOf(true, false)) {
            val v = PanelGeometry.verticalPadding(pad, cut, label)
            val rivetEdge = PanelGeometry.rivetInset(cut) + PanelGeometry.RIVET_RADIUS
            assertTrue("cut $cut pad $pad: $v <= $rivetEdge", v > rivetEdge)
        }
    }

    @Test
    fun theContentNeverRunsUnderTheTabLabel() {
        for (cut in cuts) for (pad in paddings) assertTrue(PanelGeometry.verticalPadding(pad, cut, hasLabel = true) > PanelGeometry.LABEL_REACH)
    }

    @Test
    fun aGenerousPaddingIsKeptAsAsked() {
        assertEquals(16f, PanelGeometry.verticalPadding(16f, 9f, hasLabel = true))
        assertEquals(20f, PanelGeometry.verticalPadding(20f, 11f, hasLabel = false))
    }

    @Test
    fun theSearchBoxGetsJustEnoughRoom() {
        // Padding 6, cut 9: rivet centre 10.7, edge 13.2, plus the gap: 14.7.
        assertEquals(14.7f, PanelGeometry.verticalPadding(6f, 9f, hasLabel = true), 1e-4f)
    }

    @Test
    fun theLabelRuleHoldsOnItsOwnWhenRivetsSitHigh() {
        // With today's corner cuts the rivets already ask for more room than the label; with a square corner (cut 0)
        // the label is what decides. The rule must not depend on that coincidence.
        assertEquals(PanelGeometry.LABEL_REACH + PanelGeometry.GAP, PanelGeometry.verticalPadding(4f, 0f, hasLabel = true), 1e-4f)
        assertEquals(12f, PanelGeometry.verticalPadding(4f, 0f, hasLabel = false), 1e-4f)
    }
}
