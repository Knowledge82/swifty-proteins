package dev.knowledge82.swiftyproteins.sound

import dev.knowledge82.swiftyproteins.loading.LoadState

/**
 * A SHORT name of the load state for [SoundOnChange] (document 14, 7.4): equal for the same state, different for a
 * different one. Not `toString()`: that of `Loaded` would spell out the whole molecule, every atom, into the saved
 * state of the activity.
 */
fun loadSoundTag(state: LoadState): String = when (state) {
    is LoadState.Idle -> "idle"
    is LoadState.Loading -> (if (state.quiet) "quiet:" else "loading:") + state.id   // a quiet load makes no sound
    is LoadState.Loaded -> "loaded:${state.molecule.id}"
    is LoadState.Failed -> "failed:${state.id}:${state.error::class.simpleName}"   // the kind; a Corrupt carries a whole report
}
