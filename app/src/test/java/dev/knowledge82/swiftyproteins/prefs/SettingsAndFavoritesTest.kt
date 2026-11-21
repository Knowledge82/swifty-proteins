package dev.knowledge82.swiftyproteins.prefs

import dev.knowledge82.swiftyproteins.scene.AtomColors
import dev.knowledge82.swiftyproteins.scene.Representation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsAndFavoritesTest {

    // ---------------------------------------------------------------- codec

    @Test
    fun settingsSurviveARoundTrip() {
        val s = Settings(Representation.WIREFRAME, showHydrogens = false, sounds = false)
        assertEquals(s, SettingsCodec.decode(SettingsCodec.encode(s)))
    }

    @Test
    fun nothingStoredGivesTheDefaults() {
        assertEquals(Settings(), SettingsCodec.decode(emptyMap()))
    }

    @Test
    fun anOddValueOnlyResetsItsOwnSetting() {
        val decoded = SettingsCodec.decode(mapOf(
            SettingsCodec.MODEL to "HOLOGRAM",          // a model this version does not know
            SettingsCodec.HYDROGENS to "false",
            SettingsCodec.SOUNDS to "maybe",            // not a boolean
        ))
        assertEquals(Settings(defaultModel = Representation.BALL_AND_STICK, showHydrogens = false, sounds = true), decoded)
    }

    @Test
    fun theModelIsStoredByNameNotByPosition() {
        // An enum's ordinal changes if a model is inserted; its name does not.
        assertEquals("SPACE_FILLING", SettingsCodec.encode(Settings(defaultModel = Representation.SPACE_FILLING))[SettingsCodec.MODEL])
    }

    // ---------------------------------------------------------------- pure favourites

    @Test
    fun theStarTogglesAndTheFilterKeepsTheCatalogueOrder() {
        assertEquals(setOf("ATP"), Favorites.toggle(emptySet(), "ATP"))
        assertEquals(emptySet<String>(), Favorites.toggle(setOf("ATP"), "ATP"))
        val ids = listOf("001", "ATP", "HEM", "ZN")
        assertEquals(listOf("ATP", "ZN"), Favorites.filter(ids, setOf("ZN", "ATP"), onlyFavorites = true))
        assertEquals(ids, Favorites.filter(ids, setOf("ZN"), onlyFavorites = false))
    }

    // ---------------------------------------------------------------- controller

    private class FakeFavorites : FavoritesStore {
        val files = HashMap<String, Set<String>>()
        val saves = ArrayList<Pair<String, Set<String>>>()
        override fun load(username: String) = files[username].orEmpty()
        override fun save(username: String, ids: Set<String>): Boolean { saves += username to ids; files[username] = ids; return true }
    }

    private val settingsStore = MemorySettingsStore(Settings(defaultModel = Representation.STICKS))
    private val favs = FakeFavorites()
    private fun controller() = PreferencesController(settingsStore, favs, CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined)

    @Test
    fun theSettingsAreLoadedAndEveryChangeIsSaved() {
        val c = controller()
        assertEquals(Representation.STICKS, c.settings.value.defaultModel)
        c.updateSettings { it.copy(sounds = false) }
        assertFalse(c.settings.value.sounds)
        assertEquals(c.settings.value, settingsStore.saved)
    }

    @Test
    fun twoChangesBuildOnEachOther() {
        // A change is a FUNCTION of the current value: the second sees the first.
        val c = controller()
        c.updateSettings { it.copy(defaultModel = it.defaultModel.next()) }
        c.updateSettings { it.copy(defaultModel = it.defaultModel.next()) }
        assertEquals(Representation.BALL_AND_STICK, c.settings.value.defaultModel)   // STICKS -> WIREFRAME -> BALL_AND_STICK
    }

    @Test
    fun favouritesFollowWhoIsSignedIn() {
        favs.files["adept42"] = setOf("ATP")
        favs.files["magos7"] = setOf("HEM")
        val c = controller()
        assertEquals(emptySet<String>(), c.favorites.value)
        c.onUserChanged("adept42")
        assertEquals(setOf("ATP"), c.favorites.value)
        c.onUserChanged(null)                                    // locked
        assertEquals(emptySet<String>(), c.favorites.value)      // nobody sees the previous stars
        c.onUserChanged("magos7")
        assertEquals(setOf("HEM"), c.favorites.value)
    }

    @Test
    fun aStarIsSavedForTheSignedInUserOnly() {
        val c = controller()
        c.onUserChanged("adept42")
        c.toggleFavorite("ATP")
        c.toggleFavorite("HEM")
        c.toggleFavorite("ATP")
        assertEquals(setOf("HEM"), c.favorites.value)
        assertEquals(listOf("adept42" to setOf("ATP"), "adept42" to setOf("ATP", "HEM"), "adept42" to setOf("HEM")), favs.saves)
    }

    @Test
    fun withNobodySignedInAStarDoesNothing() {
        val c = controller()
        c.toggleFavorite("ATP")
        assertEquals(emptySet<String>(), c.favorites.value)
        assertTrue(favs.saves.isEmpty())
    }

    @Test
    fun aStarTappedBeforeTheFavouritesArriveIsIgnored() {
        // A store whose load we release by hand: the tap comes while it is still reading.
        var release: (() -> Unit)? = null
        val slow = object : FavoritesStore {
            override fun load(username: String): Set<String> = setOf("HEM")
            override fun save(username: String, ids: Set<String>) = true.also { favs.saves += username to ids }
        }
        val pending = ArrayList<Runnable>()
        val manual = object : kotlinx.coroutines.CoroutineDispatcher() {
            override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) { pending += block }
        }
        val c = PreferencesController(settingsStore, slow, CoroutineScope(Dispatchers.Unconfined), manual)
        release = { while (pending.isNotEmpty()) pending.removeAt(0).run() }
        c.onUserChanged("adept42")
        c.toggleFavorite("ATP")                  // the load has not run yet
        assertFalse(c.ready)
        release()
        assertTrue(c.ready)
        assertEquals(setOf("HEM"), c.favorites.value)   // the file wins; the early tap did not overwrite it
        assertTrue(favs.saves.isEmpty())
    }

    @Test
    fun aLoadThatFinishesAfterTheUserChangedIsThrownAway() {
        val pending = ArrayList<Runnable>()
        val manual = object : kotlinx.coroutines.CoroutineDispatcher() {
            override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) { pending += block }
        }
        favs.files["adept42"] = setOf("ATP")
        val c = PreferencesController(settingsStore, favs, CoroutineScope(Dispatchers.Unconfined), manual)
        c.onUserChanged("adept42")
        c.onUserChanged(null)                    // locked before adept42's file was read
        while (pending.isNotEmpty()) pending.removeAt(0).run()
        assertEquals(emptySet<String>(), c.favorites.value)
    }

    // ---------------------------------------------------------------- theme, colours, tutorial (document 21)

    @Test
    fun theNewSettingsSurviveARoundTrip() {
        val s = Settings(theme = ThemeMode.LIGHT, atomColors = AtomColors.RASMOL, tutorialSeen = true)
        assertEquals(s, SettingsCodec.decode(SettingsCodec.encode(s)))
    }

    @Test
    fun aStoreThatPredatesTheNewSettingsGivesTheirDefaults() {
        // A settings file written before document 21: only the three old keys.
        val old = mapOf(SettingsCodec.MODEL to "STICKS", SettingsCodec.HYDROGENS to "false", SettingsCodec.SOUNDS to "true")
        val s = SettingsCodec.decode(old)
        assertEquals(Representation.STICKS, s.defaultModel)
        assertEquals(ThemeMode.SYSTEM, s.theme)
        assertEquals(AtomColors.JMOL, s.atomColors)
        assertFalse(s.tutorialSeen)
    }

    @Test
    fun oddNewValuesResetOnlyThemselves() {
        val s = SettingsCodec.decode(mapOf(SettingsCodec.THEME to "SEPIA", SettingsCodec.ATOM_COLORS to "1", SettingsCodec.TUTORIAL to "yes", SettingsCodec.SOUNDS to "false"))
        assertEquals(Settings(sounds = false), s)
    }

    @Test
    fun everyEncodedKeyIsReadBack() {
        // The store reads KEYS: a key written by encode and missing there would be saved and never read again.
        assertEquals(SettingsCodec.encode(Settings()).keys, SettingsCodec.KEYS.toSet())
        assertEquals(SettingsCodec.KEYS.size, SettingsCodec.KEYS.toSet().size)
    }

    @Test
    fun theThemeFollowsThePhoneOnlyWhenAskedTo() {
        assertTrue(ThemeMode.SYSTEM.isDark(phoneIsDark = true))
        assertFalse(ThemeMode.SYSTEM.isDark(phoneIsDark = false))
        assertTrue(ThemeMode.DARK.isDark(phoneIsDark = false))
        assertFalse(ThemeMode.LIGHT.isDark(phoneIsDark = true))
    }

    @Test
    fun theKeysCycleThroughEveryChoice() {
        assertEquals(listOf(ThemeMode.DARK, ThemeMode.LIGHT, ThemeMode.SYSTEM), ThemeMode.entries.map { it.next() })
        assertEquals(listOf(AtomColors.RASMOL, AtomColors.JMOL), AtomColors.entries.map { it.next() })
    }

    @Test
    fun loadedTurnsTrueOnlyAfterTheStoreWasRead() {
        val pending = ArrayList<Runnable>()
        val manual = object : kotlinx.coroutines.CoroutineDispatcher() {
            override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) { pending += block }
        }
        val stored = MemorySettingsStore(Settings(tutorialSeen = true))
        val c = PreferencesController(stored, favs, CoroutineScope(Dispatchers.Unconfined), manual)
        assertFalse("before the read, the settings are only the defaults", c.loaded.value)
        assertFalse(c.settings.value.tutorialSeen)
        while (pending.isNotEmpty()) pending.removeAt(0).run()
        assertTrue(c.loaded.value)
        assertTrue(c.settings.value.tutorialSeen)
    }
}
