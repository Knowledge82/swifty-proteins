package dev.knowledge82.swiftyproteins.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VanDerWaalsTest {

    @Test
    fun theCommonElementsHaveTheirMantinaValues() {
        // Table 12 of Mantina et al. 2009 (H by Rowland and Taylor; C, N, O, F, P, S, Cl as Bondi).
        val expected = mapOf("H" to 1.10f, "C" to 1.70f, "N" to 1.55f, "O" to 1.52f, "F" to 1.47f, "P" to 1.80f,
            "S" to 1.80f, "Cl" to 1.75f, "Br" to 1.83f, "I" to 1.98f, "Se" to 1.90f, "B" to 1.92f, "Si" to 2.10f)
        for ((e, r) in expected) assertEquals(e, r, VanDerWaals.radiusOf(e), 0f)
    }

    @Test
    fun transitionMetalsAndUnknownsGetTheFallback() {
        for (e in listOf("Fe", "Zn", "Cu", "Mo", "W", "Pt", "U", "X")) {
            assertEquals(e, VanDerWaals.FALLBACK, VanDerWaals.radiusOf(e), 0f)
            assertFalse(e, VanDerWaals.isTabulated(e))
        }
        assertTrue(VanDerWaals.isTabulated("C"))
    }

    @Test
    fun everyTabulatedRadiusIsPhysicallyPlausible() {
        // Between helium-sized and francium-sized: catches a typo such as 17.0 or 0.17.
        for (e in listOf("H", "He", "Li", "C", "Na", "K", "Rb", "Cs", "Fr", "Xe", "Rn")) {
            assertTrue(e, VanDerWaals.radiusOf(e) in 1.0f..3.6f)
        }
    }
}
