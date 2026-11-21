package dev.knowledge82.swiftyproteins.loading

import dev.knowledge82.swiftyproteins.molecule.Molecule
import dev.knowledge82.swiftyproteins.net.LoadError
import dev.knowledge82.swiftyproteins.net.Origin

/**
 * What the screen shows while a ligand is being loaded. Exactly ONE of these at any moment, so the screen can
 * never show a spinner and an error at the same time: the compiler does not even allow that state to exist.
 */
sealed interface LoadState {

    /** Nothing is being loaded and nothing is ready (the start, or the user left). */
    data object Idle : LoadState

    /** The download is in progress: show the loading indicator. It is the ONLY state that shows it. */
    data class Loading(val id: String) : LoadState

    /** The molecule is ready to be drawn. */
    data class Loaded(val molecule: Molecule, val origin: Origin = Origin.NETWORK) : LoadState

    /** It failed; [error] says why (the screen turns it into one of the four messages). */
    data class Failed(val id: String, val error: LoadError) : LoadState
}
