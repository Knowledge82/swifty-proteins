package dev.knowledge82.swiftyproteins.ui

import dev.knowledge82.swiftyproteins.loading.LoadState
import dev.knowledge82.swiftyproteins.net.Origin

/**
 * The screens of the app and which one is shown (document 21). Pure, tested: the root ([SwiftyApp]) only DRAWS the
 * answer, and switches between two answers like an old tube ([CrtSwitch], bonus VII.2 "smooth transitions between
 * views", document 23).
 *
 * A screen CARRIES what it needs to be drawn (the molecule screen carries its molecule). That matters for the change
 * of screen: while the old screen switches off it is still drawn, and by then the load state has already moved on; a
 * screen that read the molecule from the live state would have nothing left to draw.
 */
sealed interface Screen {
    /** Same key = same screen (no change of screen when only its data changes). */
    val key: String

    data object Boot : Screen { override val key = "boot" }
    data object Tutorial : Screen { override val key = "tutorial" }
    data object Access : Screen { override val key = "access" }
    /** The Data-Vault Index: the list of ligands. (Not called "List": that name belongs to Kotlin.) */
    data object Index : Screen { override val key = "index" }
    data object Settings : Screen { override val key = "settings" }
    data class Molecule(val molecule: dev.knowledge82.swiftyproteins.molecule.Molecule, val origin: Origin) : Screen {
        override val key = "molecule:${molecule.id}"
    }
}

object Navigation {

    /**
     * Which screen, in this order of priority:
     *
     * 1. the boot screen, until it finished AND the saved settings were read (before that, "tutorial seen" is
     *    the default `false` and the tutorial would flash for someone who finished it long ago);
     * 2. the first-run tutorial, before signing in (it shows nothing private);
     * 3. signing in: ALWAYS before anything with data (D43);
     * 4. the tutorial again, if asked for from the settings;
     * 5. the molecule, once loaded;
     * 6. the settings;
     * 7. the list.
     */
    fun screenOf(
        booted: Boolean,
        settingsLoaded: Boolean,
        tutorialSeen: Boolean,
        replayTutorial: Boolean,
        unlocked: Boolean,
        showSettings: Boolean,
        load: LoadState,
    ): Screen = when {
        !booted || !settingsLoaded -> Screen.Boot
        !tutorialSeen -> Screen.Tutorial
        !unlocked -> Screen.Access
        replayTutorial -> Screen.Tutorial
        load is LoadState.Loaded -> Screen.Molecule(load.molecule, load.origin)
        showSettings -> Screen.Settings
        else -> Screen.Index
    }

    /** The boot screen is a CRT starting up: dark whatever the theme. Every other screen follows the theme. */
    fun alwaysDark(screen: Screen): Boolean = screen == Screen.Boot

    /** How long a page of the tutorial takes to slide; 0 when the user asked the system for no animations. */
    fun durationMillis(reduceMotion: Boolean): Int = if (reduceMotion) 0 else 300
}
