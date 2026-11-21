package dev.knowledge82.swiftyproteins.sound

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * Runs [action] when [tag] takes a NEW value, once per real change (document 14, section 7.4).
 *
 * A plain `LaunchedEffect(state)` is not enough for sounds: rotating the phone recreates the activity, the whole
 * screen is composed again and every LaunchedEffect starts again, so the "42" of signing in or the ping of a loaded
 * molecule would sound again with nothing new on screen. Here the last tag is kept with [rememberSaveable], which
 * survives that recreation: after a rotation the tag is the same as the saved one and nothing sounds.
 *
 * The tag must be SHORT (it goes into the saved state bundle): an id, not a whole molecule.
 */
@Composable
fun SoundOnChange(tag: String, action: (tag: String) -> Unit) {
    var last by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(tag) {
        if (tag != last) {
            last = tag
            action(tag)
        }
    }
}
