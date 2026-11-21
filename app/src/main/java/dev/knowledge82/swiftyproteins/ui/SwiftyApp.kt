package dev.knowledge82.swiftyproteins.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.knowledge82.swiftyproteins.auth.AuthController
import dev.knowledge82.swiftyproteins.loading.LoadState
import dev.knowledge82.swiftyproteins.prefs.PreferencesController
import dev.knowledge82.swiftyproteins.sound.LocalSounds
import dev.knowledge82.swiftyproteins.sound.SoundCue
import dev.knowledge82.swiftyproteins.sound.SoundOnChange
import dev.knowledge82.swiftyproteins.sound.loadSoundTag
import dev.knowledge82.swiftyproteins.ui.theme.AlwaysDark
import dev.knowledge82.swiftyproteins.ui.theme.SwiftyColors
import dev.knowledge82.swiftyproteins.ui.theme.SwiftyPalette
import dev.knowledge82.swiftyproteins.ui.theme.SwiftyProteinsTheme
import kotlinx.coroutines.launch

/**
 * The root of the screen: decides WHAT to show ([Navigation.screenOf], pure and tested; no navigation library, D19)
 * and changes from one screen to the next like an old tube switching off and on ([CrtSwitch], bonus VII.2, document 23).
 *
 *   boot screen      -> once per start, until the saved settings are read; ALWAYS dark (a CRT starting up)
 *   tutorial         -> the first time (and again from the settings)
 *   sign-in          -> ALWAYS before anything with data (document 05, D43)
 *   comparison       -> once a second molecule is loaded too (document 24)
 *   molecule         -> once loaded
 *   settings / index -> otherwise, with the load dialogs over the index
 *
 * While locked, the list and the molecule are NOT drawn, but their state is kept (here and in the ViewModels): after
 * signing in again the user is back where they were.
 *
 * @param onSystemBarsDark the activity paints the status and navigation bars for a dark (true) or light background
 */
@Composable
fun SwiftyApp(
    viewModel: LigandsViewModel,
    auth: AuthController,
    prefs: PreferencesController,
    onSystemBarsDark: (Boolean) -> Unit = {},
) {
    val state by viewModel.loadState.collectAsStateWithLifecycle()
    // The second molecule of the comparison (bonus VII.5, document 24), and whether its picker is open.
    val compareState by viewModel.compareState.collectAsStateWithLifecycle()
    var pickingCompare by rememberSaveable { mutableStateOf(false) }
    val authState by auth.state.collectAsStateWithLifecycle()

    // Settings and favourites (document 16). The favourites follow WHO is signed in: a lock empties them at once.
    val settings by prefs.settings.collectAsStateWithLifecycle()
    val settingsLoaded by prefs.loaded.collectAsStateWithLifecycle()
    val favorites by prefs.favorites.collectAsStateWithLifecycle()
    val archived by viewModel.archived.collectAsStateWithLifecycle()
    LaunchedEffect(authState.signedInAs) { prefs.onUserChanged(authState.signedInAs) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var replayTutorial by rememberSaveable { mutableStateOf(false) }
    var onlyFavorites by rememberSaveable { mutableStateOf(false) }

    // Sounds of the flows (document 14, 7.4). SoundOnChange, not a bare LaunchedEffect: a rotation must not replay them.
    val sounds = LocalSounds.current
    LaunchedEffect(settings.sounds) { sounds.setMuted(!settings.sounds) }
    SoundOnChange(if (authState.unlocked) "unlocked" else "locked") { if (it == "unlocked") sounds.play(SoundCue.SIGNED_IN) }
    SoundOnChange(authState.formError.orEmpty()) { if (it.isNotEmpty()) sounds.play(SoundCue.DENIED) }
    val loadNow = state
    SoundOnChange(loadSoundTag(loadNow)) {
        when (loadNow) {
            is LoadState.Loading -> sounds.play(SoundCue.SCAN)
            is LoadState.Loaded -> { sounds.stop(SoundCue.SCAN); sounds.play(SoundCue.LOADED) }
            is LoadState.Failed -> { sounds.stop(SoundCue.SCAN); sounds.play(SoundCue.DENIED) }
            is LoadState.Idle -> sounds.stop(SoundCue.SCAN)
        }
    }
    // The same sounds for the second molecule of a comparison.
    val compareNow = compareState
    SoundOnChange("compare:" + loadSoundTag(compareNow)) {
        when (compareNow) {
            is LoadState.Loading -> sounds.play(SoundCue.SCAN)
            is LoadState.Loaded -> { sounds.stop(SoundCue.SCAN); sounds.play(SoundCue.LOADED) }
            is LoadState.Failed -> { sounds.stop(SoundCue.SCAN); sounds.play(SoundCue.DENIED) }
            is LoadState.Idle -> Unit   // leaving the comparison: the SCAN of the first molecule is not ours to stop
        }
    }
    // A ligand that just loaded is now on the phone: its "archived" mark appears in the index (document 21).
    LaunchedEffect((loadNow as? LoadState.Loaded)?.molecule?.id, (compareNow as? LoadState.Loaded)?.molecule?.id) { viewModel.refreshArchived() }

    // Kept HERE (and not inside the list screen) because the list screen leaves the composition while the molecule
    // is shown, and anything it remembered would be lost: the user would come back to an empty search box at the top.
    var query by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // rememberSaveable: rotating the phone does not replay the boot screen; a fresh start does.
    var booted by rememberSaveable { mutableStateOf(false) }

    val screen = Navigation.screenOf(
        booted = booted,
        settingsLoaded = settingsLoaded,
        tutorialSeen = settings.tutorialSeen,
        replayTutorial = replayTutorial,
        unlocked = authState.unlocked,
        showSettings = showSettings,
        load = state,
        compare = compareState,
    )

    // THE THEME (bonus VII.2, document 21): the settings say light, dark or "as the phone"; the boot screen is always dark.
    val appDark = settings.theme.isDark(isSystemInDarkTheme())
    val barsDark = appDark || Navigation.alwaysDark(screen)
    LaunchedEffect(barsDark) { onSystemBarsDark(barsDark) }
    val reduce = rememberReduceMotion()

    SwiftyProteinsTheme(SwiftyPalette.of(appDark)) {
        // Behind the system bars too (edge to edge): the boot screen's black, or the theme's background.
        val behind = if (Navigation.alwaysDark(screen)) SwiftyPalette.Dark.abyss else SwiftyColors.Abyss
        Scaffold(modifier = Modifier.fillMaxSize(), containerColor = behind) { innerPadding ->
            // consumeWindowInsets: tells the children "the system bars are already taken into account", so that
            // imePadding() inside the list does not add them a second time when the keyboard opens.
            val content = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding)

            // The change of screen: an old tube switching off and on (bonus VII.2, document 23).
            CrtSwitch(target = screen, startOff = !booted, reduceMotion = reduce) { shown ->
                when (shown) {
                    Screen.Boot -> AlwaysDark {
                        val size = remember(viewModel) { viewModel.catalog?.search("")?.size }
                        BootScreen(catalogSize = size, onFinished = { booted = true }, modifier = content)
                    }
                    Screen.Tutorial -> OnboardingScreen(
                        onDone = {
                            replayTutorial = false
                            prefs.updateSettings { it.copy(tutorialSeen = true) }
                        },
                        modifier = content,
                    )
                    Screen.Access -> AccessScreen(state = authState, controller = auth, modifier = content)
                    is Screen.Molecule -> {
                        val id = shown.molecule.id
                        MoleculeScreen(
                            molecule = shown.molecule,
                            origin = shown.origin,
                            onBack = viewModel::cancel,
                            startModel = settings.defaultModel,
                            startWithHydrogens = settings.showHydrogens,
                            favorite = id in favorites,
                            onToggleFavorite = { prefs.toggleFavorite(id) },
                            atomColors = settings.atomColors,
                            onCompare = { pickingCompare = true },
                            modifier = content,
                        )
                        // CMP (document 24): pick the second ligand, then its load shows the same dialogs as the first.
                        if (pickingCompare) {
                            ComparePicker(
                                catalog = viewModel.catalog,
                                index = viewModel.index,
                                archived = archived,
                                exclude = id,
                                onPick = { second ->
                                    pickingCompare = false
                                    viewModel.openCompare(second)
                                },
                                onDismiss = { pickingCompare = false },
                            )
                        }
                        LoadStateDialogs(state = compareState, onOpen = viewModel::openCompare, onClose = viewModel::cancelCompare)
                    }
                    is Screen.Compare -> CompareScreen(
                        first = shown.first,
                        second = shown.second,
                        onBack = viewModel::cancelCompare,
                        model = settings.defaultModel,
                        startWithHydrogens = settings.showHydrogens,
                        atomColors = settings.atomColors,
                        modifier = content,
                    )
                    Screen.Settings -> SettingsScreen(
                        settings = settings,
                        onChange = prefs::updateSettings,
                        onBack = { showSettings = false },
                        onReplayTutorial = { replayTutorial = true },
                        modifier = content,
                    )
                    Screen.Index -> {
                        LigandListScreen(
                            catalog = viewModel.catalog,
                            index = viewModel.index,
                            archived = archived,
                            atomColors = settings.atomColors,
                            query = query,
                            onQueryChange = { newText ->
                                query = newText
                                // A new search starts at the top; without this the list would keep an old position.
                                scope.launch { listState.scrollToItem(0) }
                            },
                            listState = listState,
                            onOpen = viewModel::open,
                            favorites = favorites,
                            onlyFavorites = onlyFavorites,
                            onToggleOnlyFavorites = { onlyFavorites = !onlyFavorites },
                            onToggleFavorite = prefs::toggleFavorite,
                            onSettings = { showSettings = true },
                            modifier = content,
                        )
                        LoadStateDialogs(
                            state = state,
                            onOpen = viewModel::open,
                            onClose = viewModel::cancel,
                        )
                    }
                }
            }
            // After the boot screen, over everything (some appear right after unlocking, on top of the list).
            if (booted) AuthDialogs(state = authState, controller = auth)
        }
    }
}
