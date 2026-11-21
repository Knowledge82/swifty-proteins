package dev.knowledge82.swiftyproteins.prefs

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The settings and the favourites of whoever is signed in (document 16). Plain Kotlin with coroutines, like the
 * AuthController: tested on the JVM with fake stores.
 *
 * Disk work runs on [io], which the app gives as ONE thread (`Dispatchers.IO.limitedParallelism(1)`): two quick taps
 * on a star are saved in the order they happened, never two writes of the same file at once.
 */
class PreferencesController(
    private val settingsStore: SettingsStore,
    private val favoritesStore: FavoritesStore,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
) {
    private val mutableSettings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = mutableSettings.asStateFlow()

    // False until the saved settings were read (document 21): before that, `settings` holds the DEFAULTS, and a screen
    // that trusted them would show the first-run tutorial to someone who finished it long ago.
    private val mutableLoaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = mutableLoaded.asStateFlow()

    private val mutableFavorites = MutableStateFlow<Set<String>>(emptySet())
    val favorites: StateFlow<Set<String>> = mutableFavorites.asStateFlow()

    /** Whose favourites are loaded (null: nobody signed in). */
    private var user: String? = null

    // Bumped on every change of user: a load that finishes AFTER the user changed again is thrown away.
    private var generation = 0

    // The generation whose favourites are in memory. A star tapped BEFORE they are loaded would be overwritten by the
    // load (and saved over the real file): until then a tap is ignored. Loading takes milliseconds.
    private var loadedGeneration = -1

    /** True once the favourites of the signed-in user are in memory (the stars can be used). */
    val ready: Boolean get() = user != null && loadedGeneration == generation

    init {
        scope.launch(io) {
            mutableSettings.value = settingsStore.load()
            mutableLoaded.value = true
        }
    }

    fun updateSettings(change: (Settings) -> Settings) {
        val next = change(mutableSettings.value)
        mutableSettings.value = next
        scope.launch(io) { settingsStore.save(next) }
    }

    /**
     * Called whenever the signed-in user changes (null when locked). Locking clears the favourites from memory at
     * once: the next person to sign in must never see, even for a frame, the stars of the previous one.
     */
    fun onUserChanged(username: String?) {
        if (username == user) return
        user = username
        val mine = ++generation
        mutableFavorites.value = emptySet()
        if (username != null) {
            scope.launch(io) {
                val loaded = favoritesStore.load(username)
                if (mine == generation) {
                    mutableFavorites.value = loaded
                    loadedGeneration = mine
                }
            }
        }
    }

    /** The star of [id]: in memory at once (the screen answers the tap immediately), then saved for this user. */
    fun toggleFavorite(id: String) {
        val owner = user ?: return
        if (!ready) return
        val next = Favorites.toggle(mutableFavorites.value, id)
        mutableFavorites.value = next
        scope.launch(io) { favoritesStore.save(owner, next) }
    }
}
