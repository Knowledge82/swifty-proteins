package dev.knowledge82.swiftyproteins.scene

import org.junit.Assert.assertEquals
import org.junit.Test

class AtomColorsTest {

    @Test
    fun jmolIsTheDefaultAndKeepsItsValues() {
        assertEquals(0x909090, ElementStyle.colorOf("C"))
        assertEquals(ElementStyle.colorOf("O"), ElementStyle.colorOf("O", AtomColors.JMOL))
        assertEquals(0xFF0D0D, ElementStyle.colorOf("O"))
    }

    @Test
    fun rasmolHasItsOwnClassicValues() {
        // UMass "RasMol/Chime Color RGB Values".
        val r = AtomColors.RASMOL
        assertEquals(0xC8C8C8, ElementStyle.colorOf("C", r))
        assertEquals(0xF00000, ElementStyle.colorOf("O", r))
        assertEquals(0x8F8FFF, ElementStyle.colorOf("N", r))
        assertEquals(0xFFC832, ElementStyle.colorOf("S", r))
        assertEquals(0xFFFFFF, ElementStyle.colorOf("H", r))
    }

    @Test
    fun rasmolSharesOneColourInsideEachGroup() {
        val r = AtomColors.RASMOL
        assertEquals(setOf(0xA52A2A), listOf("Zn", "Cu", "Ni", "Br").map { ElementStyle.colorOf(it, r) }.toSet())
        assertEquals(setOf(0xFFA500), listOf("P", "Fe", "Ba").map { ElementStyle.colorOf(it, r) }.toSet())
        assertEquals(setOf(0x00FF00), listOf("Cl", "B").map { ElementStyle.colorOf(it, r) }.toSet())
    }

    @Test
    fun whatRasmolDoesNotListIsDeepPinkAsInRasmol() {
        assertEquals(ElementStyle.UNKNOWN_COLOR, ElementStyle.colorOf("Co", AtomColors.RASMOL))
        assertEquals(ElementStyle.UNKNOWN_COLOR, ElementStyle.colorOf("X", AtomColors.RASMOL))
        assertEquals(0xFF1493, ElementStyle.UNKNOWN_COLOR)
    }
}
