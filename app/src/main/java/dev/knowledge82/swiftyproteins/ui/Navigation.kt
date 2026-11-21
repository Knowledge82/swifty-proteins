package dev.knowledge82.swiftyproteins.ui

import dev.knowledge82.swiftyproteins.loading.LoadState
import dev.knowledge82.swiftyproteins.net.Origin

/**
 * The screens of the app and which one is shown (document 21). Pure, tested: the root ([SwiftyApp]) only DRAWS the
 * answer, and animates the change between two answers (bonus VII.2 "smooth transitions between views").
 *
 * A screen CARRIES what it needs to be drawn (the molecule screen carries its molecule). That matters for the
 * animation: while the old screen slides out it is still drawn, and by then the load state has already moved on;
 * a screen that read the molecule from the live state would have nothing left to draw.
 */
sealed interface Screen {
    /** Where it sits: going to a deeper screen slides forward, coming back slides back. */
    val depth: Int

    /** Same key = same screen (no animation when only its data changes). */
    val key: String

    data object Boot : Screen { override val depth = 0; override val key = "boot" }
    data object Tutorial : Screen { override val depth = 1; override val key = "tutorial" }
    data object Access : Screen { override val depth = 2; override val key = "access" }
    /** The Data-Vault Index: the list of ligands. (Not called "List": that name belongs to Kotlin.) */
    data object Index : Screen { override val depth = 3; override val key = "index" }
    data object Settings : Screen { override val depth = 4; override val key = "settings" }
    data class Molecule(val molecule: dev.knowledge82.swiftyproteins.molecule.Molecule, val origin: Origin) : Screen {
        override val depth = 4
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

    /**
     * +1: forward (the new screen comes from the right), -1: back (from the left), 0: the same level (a cross-fade).
     * The tutorial always goes forward, in and out: it is a detour, not a level. Going INTO it is the special case
     * (from the settings it is "deeper" by meaning, though it sits low in the list); coming OUT of it is forward by
     * depth already, since only the boot screen is lower.
     */
    fun direction(from: Screen, to: Screen): Int = when {
        to == Screen.Tutorial -> 1
        to.depth > from.depth -> 1
        to.depth < from.depth -> -1
        else -> 0
    }

    /** The boot screen is a CRT starting up: dark whatever the theme. Every other screen follows the theme. */
    fun alwaysDark(screen: Screen): Boolean = screen == Screen.Boot

    /** How long a change of screen takes; 0 when the user asked the system for no animations. */
    fun durationMillis(reduceMotion: Boolean): Int = if (reduceMotion) 0 else 300
}
