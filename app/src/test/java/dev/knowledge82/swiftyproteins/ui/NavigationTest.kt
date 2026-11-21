package dev.knowledge82.swiftyproteins.ui

import dev.knowledge82.swiftyproteins.loading.LoadState
import dev.knowledge82.swiftyproteins.molecule.Atom
import dev.knowledge82.swiftyproteins.molecule.Molecule
import dev.knowledge82.swiftyproteins.net.Origin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationTest {

    private val atp = Molecule("ATP", listOf(Atom("C1", "C", 0f, 0f, 0f)), emptyList())

    private fun screen(
        booted: Boolean = true, loaded: Boolean = true, seen: Boolean = true, replay: Boolean = false,
        unlocked: Boolean = true, settings: Boolean = false, load: LoadState = LoadState.Idle,
        compare: LoadState = LoadState.Idle,
    ) = Navigation.screenOf(booted, loaded, seen, replay, unlocked, settings, load, compare)

    @Test
    fun theBootScreenStaysUntilTheSettingsAreRead() {
        assertEquals(Screen.Boot, screen(booted = false))
        assertEquals(Screen.Boot, screen(loaded = false))                 // booted, but "tutorial seen" not known yet
        assertEquals(Screen.Boot, screen(loaded = false, seen = false))   // so the tutorial does not flash
    }

    @Test
    fun theFirstRunTutorialComesBeforeSigningIn() {
        assertEquals(Screen.Tutorial, screen(seen = false, unlocked = false))
        assertEquals(Screen.Access, screen(unlocked = false))
    }

    @Test
    fun signingInComesBeforeAnyData() {
        assertEquals(Screen.Access, screen(unlocked = false, load = LoadState.Loaded(atp)))
        assertEquals(Screen.Access, screen(unlocked = false, settings = true))
        assertEquals(Screen.Access, screen(unlocked = false, replay = true))   // the replay waits for the sign-in too
    }

    @Test
    fun afterSigningInMoleculeThenSettingsThenIndex() {
        assertEquals(Screen.Molecule(atp, Origin.NETWORK), screen(load = LoadState.Loaded(atp), settings = true))
        assertEquals(Screen.Settings, screen(settings = true))
        assertEquals(Screen.Index, screen())
        assertEquals(Screen.Index, screen(load = LoadState.Loading("ATP")))   // the dialog is drawn over the index
        assertEquals(Screen.Tutorial, screen(replay = true, settings = true))
    }

    @Test
    fun theMoleculeScreenCarriesItsMolecule() {
        val s = screen(load = LoadState.Loaded(atp, Origin.ARCHIVE)) as Screen.Molecule
        assertEquals(atp, s.molecule)
        assertEquals(Origin.ARCHIVE, s.origin)
        assertEquals("molecule:ATP", s.key)
    }

    @Test
    fun aSecondMoleculeOpensTheComparisonAndOnlyThen() {
        val cff = Molecule("CFF", listOf(Atom("C1", "C", 0f, 0f, 0f)), emptyList())
        val both = screen(load = LoadState.Loaded(atp, Origin.NETWORK), compare = LoadState.Loaded(cff, Origin.ARCHIVE)) as Screen.Compare
        assertEquals(atp, both.first)
        assertEquals(cff, both.second)
        assertEquals("compare:ATP:CFF", both.key)
        // While the second one loads (or failed), the first molecule stays on screen, under the dialogs.
        assertTrue(screen(load = LoadState.Loaded(atp, Origin.NETWORK), compare = LoadState.Loading("CFF")) is Screen.Molecule)
        // A second molecule alone (the first one was closed) is no comparison: the index.
        assertEquals(Screen.Index, screen(compare = LoadState.Loaded(cff, Origin.ARCHIVE)))
        // Locked: never the comparison either (D43).
        assertEquals(Screen.Access, screen(unlocked = false, load = LoadState.Loaded(atp, Origin.NETWORK), compare = LoadState.Loaded(cff, Origin.ARCHIVE)))
    }

    @Test
    fun keysAreUniqueSoEveryChangeOfScreenSwitchesTheTube() {
        val cff = Molecule("CFF", listOf(Atom("C1", "C", 0f, 0f, 0f)), emptyList())
        val all = listOf(Screen.Boot, Screen.Tutorial, Screen.Access, Screen.Index, Screen.Settings, Screen.Molecule(atp, Origin.NETWORK), Screen.Compare(atp, cff))
        assertEquals(all.size, all.map { it.key }.toSet().size)
    }

    @Test
    fun onlyTheBootScreenIsAlwaysDarkAndNoMotionMeansNoAnimation() {
        assertTrue(Navigation.alwaysDark(Screen.Boot))
        assertFalse(Navigation.alwaysDark(Screen.Index))
        assertEquals(0, Navigation.durationMillis(reduceMotion = true))
        assertTrue(Navigation.durationMillis(reduceMotion = false) in 200..400)
    }
}
