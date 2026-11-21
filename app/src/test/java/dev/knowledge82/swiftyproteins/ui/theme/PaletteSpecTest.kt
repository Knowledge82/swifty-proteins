package dev.knowledge82.swiftyproteins.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaletteSpecTest {

    @Test
    fun theContrastFormulaGivesTheKnownValues() {
        assertEquals(21.0, Contrast.ratio(0x000000, 0xFFFFFF), 1e-9)
        assertEquals(1.0, Contrast.ratio(0x777777, 0x777777), 1e-9)
        assertEquals(Contrast.ratio(0x123456, 0xABCDEF), Contrast.ratio(0xABCDEF, 0x123456), 1e-12)   // order does not matter
        assertEquals(4.54, Contrast.ratio(0x767676, 0xFFFFFF), 0.01)   // the classic "lightest grey that passes on white"
    }

    @Test
    fun everyTextRolePassesAaOnPanelsAndBackgroundInBothThemes() {
        for (p in listOf(PaletteSpec.DARK, PaletteSpec.LIGHT)) {
            for ((role, ink) in p.textRoles) {
                for ((ground, bg) in listOf("console" to p.console, "abyss" to p.abyss)) {
                    val r = Contrast.ratio(ink, bg)
                    assertTrue("${if (p.isDark) "dark" else "light"} $role on $ground: %.2f".format(r), r >= 4.5)
                }
            }
        }
    }

    @Test
    fun textOnThePrimaryButtonPassesInBothThemes() {
        for (p in listOf(PaletteSpec.DARK, PaletteSpec.LIGHT)) assertTrue(Contrast.ratio(p.onMarsRed, p.marsRed) >= 4.5)
    }

    @Test
    fun headingsOnTheirPlatesPass() {
        // The tab labels: gold on the plate; the title plates: gold on iron.
        for (p in listOf(PaletteSpec.DARK, PaletteSpec.LIGHT)) {
            assertTrue(Contrast.ratio(p.gold, p.plate) >= 4.5)
            assertTrue(Contrast.ratio(p.gold, p.iron) >= 4.5)
        }
    }

    @Test
    fun theLightThemeIsReallyLightAndTheDarkOneDark() {
        assertTrue(Contrast.luminance(PaletteSpec.LIGHT.abyss) > 0.5)
        assertTrue(Contrast.luminance(PaletteSpec.DARK.abyss) < 0.01)
        assertTrue(PaletteSpec.of(true).isDark)
        assertFalse(PaletteSpec.of(false).isDark)
    }

    @Test
    fun parchmentNeitherGlowsNorTakesTheDarkCrtTexture() {
        assertEquals(0f, PaletteSpec.LIGHT.glow)
        assertEquals(0f, PaletteSpec.LIGHT.crtTexture)
        assertTrue(PaletteSpec.LIGHT.ironTexture < 0.1f)
        assertEquals(1f, PaletteSpec.DARK.glow)
    }

    @Test
    fun theDarkPaletteIsTheOneTheAppAlwaysHad() {
        // The design v2 values (document 08c): the dark theme must not have moved while the light one was added.
        val d = PaletteSpec.DARK
        assertEquals(listOf(0x050806, 0x08120B, 0x43E07A, 0xE6DCC3, 0xE0B65C, 0xB8893A, 0x8E1B14), listOf(d.abyss, d.console, d.phosphor, d.bone, d.gold, d.brass, d.marsRed))
    }
}
