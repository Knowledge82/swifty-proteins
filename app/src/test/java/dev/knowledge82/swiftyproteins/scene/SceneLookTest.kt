package dev.knowledge82.swiftyproteins.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneLookTest {

    private fun linearOf(rgb: Int) = doubleArrayOf(
        SceneLook.linear((rgb shr 16) and 0xFF), SceneLook.linear((rgb shr 8) and 0xFF), SceneLook.linear(rgb and 0xFF),
    )

    @Test
    fun theDarkSceneKeepsSceneViewsBlack() {
        assertNull(SceneLook.DARK.backdrop)
        assertEquals(SceneLook.DARK, SceneLook.of(true))
    }

    @Test
    fun theSkyboxColourComesOutOfTheToneMapperAsTheWantedParchment() {
        val wanted = SceneLook.LIGHT.backdrop!!
        val sky = SceneLook.skyboxColor(wanted)
        val shown = SceneLook.pbrNeutral(doubleArrayOf(sky[0].toDouble(), sky[1].toDouble(), sky[2].toDouble()))
        val target = linearOf(wanted)
        for (i in 0..2) assertEquals(target[i], shown[i], 1e-6)
        assertEquals(1f, sky[3])
    }

    @Test
    fun theParchmentSitsBelowTheKneeWhereTheCompensationIsExact() {
        val lin = linearOf(SceneLook.LIGHT.backdrop!!)
        assertTrue(lin.max() < SceneLook.START_COMPRESSION)
        assertTrue(lin.min() + 0.04 >= 0.08)   // the plain offset branch of the tone mapper
    }

    @Test
    fun theToneMapperFormulaMatchesItsReference() {
        // PBR Neutral: dark colours get a soft offset, mid colours lose 0.04, highlights are compressed (never above 1).
        val mid = SceneLook.pbrNeutral(doubleArrayOf(0.5, 0.4, 0.3))
        assertEquals(0.46, mid[0], 1e-9); assertEquals(0.26, mid[2], 1e-9)
        val dark = SceneLook.pbrNeutral(doubleArrayOf(0.04, 0.04, 0.04))
        assertEquals(0.04 - (0.04 - 6.25 * 0.04 * 0.04), dark[0], 1e-12)
        val hot = SceneLook.pbrNeutral(doubleArrayOf(5.0, 5.0, 5.0))
        assertTrue(hot.all { it < 1.0 && it > 0.9 })
    }

    @Test
    fun theLightHalosAreDeeperAndTheRimSofter() {
        assertTrue(SceneLook.LIGHT.elementAlpha > SceneLook.DARK.elementAlpha)
        assertTrue(SceneLook.LIGHT.rimLux < SceneLook.DARK.rimLux)
        assertEquals(SceneLook.DARK.keyLux, SceneLook.LIGHT.keyLux)   // the balls are shaded the same way
    }
}
