package dev.knowledge82.swiftyproteins.ui

import dev.knowledge82.swiftyproteins.molecule.Atom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.Locale

class AtomTextTest {

    @Test
    fun theTitleNamesTheAtomAndItsElement() {
        assertEquals("C01 · Carbon (C)", AtomText.title(Atom("C01", "C", 0f, 0f, 0f)))
    }

    @Test
    fun aCoordinateHasThreeDecimalsAndTheUnit() {
        assertEquals("X 12.184 Å", AtomText.coordinate("X", 12.1841f))
        assertEquals("Z 0.000 Å", AtomText.coordinate("Z", 0f))
    }

    @Test
    fun aNegativeCoordinateUsesThePlainMinus() {
        val text = AtomText.coordinate("Y", -5.006f)
        assertEquals("Y -5.006 Å", text)
        assertFalse(text.contains('−'))   // the typographic minus the design previews could not draw
    }

    @Test
    fun theDecimalPointDoesNotDependOnThePhoneLanguage() {
        val before = Locale.getDefault()
        try {
            Locale.setDefault(Locale.FRANCE)   // French would write "12,184"
            assertEquals("X 12.184 Å", AtomText.coordinate("X", 12.184f))
        } finally {
            Locale.setDefault(before)
        }
    }
}
