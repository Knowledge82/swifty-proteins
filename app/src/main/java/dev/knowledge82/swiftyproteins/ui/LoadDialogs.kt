package dev.knowledge82.swiftyproteins.ui

import android.util.Log
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.knowledge82.swiftyproteins.R
import dev.knowledge82.swiftyproteins.loading.LoadState
import dev.knowledge82.swiftyproteins.net.LoadError
import dev.knowledge82.swiftyproteins.ui.theme.SwiftyColors

private const val TAG = "SwiftyProteins"

/**
 * Draws the dialog that goes with the current [LoadState], or nothing (design screens 09-14).
 *
 * - Loading: a modal dialog with the turning cog and a Cancel button. A MODAL dialog is the simplest way to block
 *   the screen below while loading (no taps reach the list), and the system Back button closes it as Cancel.
 * - Failed: title, picture and message for the error, with "Retry" when repeating makes sense.
 * - Idle / Loaded: nothing. The `when` has no `else`, so a fifth state would not compile until handled here.
 *
 * The LOGIC is the same as before the design; only the drawing changed (Dialog + our own panel instead of the
 * fixed layout of AlertDialog, which puts the buttons on the right and has no room for the icon well).
 *
 * @param onOpen  starts loading an id (used by "Retry")
 * @param onClose goes back to Idle (Cancel, Back, closing an error)
 */
@Composable
fun LoadStateDialogs(
    state: LoadState,
    onOpen: (String) -> Unit,
    onClose: () -> Unit,
) {
    when (state) {
        is LoadState.Loading -> LoadingDialog(id = state.id, onCancel = onClose)
        is LoadState.Failed -> {
            // The user sees a friendly sentence; the technical cause (parser failure, reason text) goes to Logcat.
            LaunchedEffect(state) {
                Log.w(TAG, "loading ${state.id} failed: ${state.error}")
            }
            ErrorDialog(
                id = state.id,
                error = state.error,
                onRetry = { onOpen(state.id) },
                onClose = onClose,
            )
        }
        is LoadState.Idle, is LoadState.Loaded -> Unit
    }
}

@Composable
private fun LoadingDialog(id: String, onCancel: () -> Unit) {
    Dialog(
        onDismissRequest = onCancel,                                    // the system Back button
        properties = DialogProperties(dismissOnClickOutside = false),   // an accidental tap must not cancel
    ) {
        DialogPanel(modifier = Modifier.widthIn(max = 440.dp)) {
            RotatingCog()
            Spacer(Modifier.height(16.dp))
            Text("Retrieving ligand", style = MaterialTheme.typography.titleLarge, color = SwiftyColors.Bone)
            Spacer(Modifier.height(4.dp))
            Text("Loading ligand $id…", style = MaterialTheme.typography.bodyLarge, color = SwiftyColors.Bone)
            Text("Connecting to molecule archive", style = MaterialTheme.typography.bodySmall, color = SwiftyColors.Ash)
            Spacer(Modifier.height(20.dp))
            SecondaryButton("Cancel", onClick = onCancel, icon = R.drawable.ic_close)
        }
    }
}

@Composable
private fun ErrorDialog(id: String, error: LoadError, onRetry: () -> Unit, onClose: () -> Unit) {
    val retry = error.canRetry()
    Dialog(onDismissRequest = onClose) {
        DialogPanel(modifier = Modifier.widthIn(max = 440.dp)) {
            // The error is shown by an icon AND a title AND a sentence: never by the red colour alone.
            IconWell(icon = glyphIcon(error.glyph()), tint = SwiftyColors.Error)
            Spacer(Modifier.height(16.dp))
            Text(error.title(), style = MaterialTheme.typography.titleLarge, color = SwiftyColors.Bone, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(error.userMessage(id), style = MaterialTheme.typography.bodyMedium, color = SwiftyColors.Ash, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            if (retry) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SecondaryButton("Close", onClick = onClose, icon = R.drawable.ic_close, modifier = Modifier.weight(1f))
                    PrimaryButton("Retry", onClick = onRetry, icon = R.drawable.ic_retry, modifier = Modifier.weight(1f))
                }
            } else {
                PrimaryButton("OK", onClick = onClose, modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp))
            }
        }
    }
}

/** The picture of each kind of error (the choice itself is pure and tested: [glyph]). */
@DrawableRes
private fun glyphIcon(glyph: ErrorGlyph): Int = when (glyph) {
    ErrorGlyph.OFFLINE -> R.drawable.ic_error_offline
    ErrorGlyph.NOT_FOUND -> R.drawable.ic_error_not_found
    ErrorGlyph.TIMEOUT -> R.drawable.ic_error_timeout
    ErrorGlyph.CORRUPT -> R.drawable.ic_error_corrupt
    ErrorGlyph.INVALID -> R.drawable.ic_error_invalid
    ErrorGlyph.WARNING -> R.drawable.ic_warning
}
