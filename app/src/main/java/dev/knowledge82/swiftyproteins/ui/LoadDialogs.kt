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
import dev.knowledge82.swiftyproteins.ui.theme.SwiftyDataText
import dev.knowledge82.swiftyproteins.ui.theme.glowing

private const val TAG = "SwiftyProteins"

/**
 * Draws the dialog that goes with the current [LoadState], or nothing (design v2, screens 10-15).
 *
 * - Loading: a modal dialog with the bobbing servo-skull and a Cancel button. A MODAL dialog is the simplest way to
 *   block the screen below while loading (no taps reach the list), and the system Back button closes it as Cancel.
 * - Failed: a picture, a flavour title, the PLAIN message and "Retry" when repeating makes sense.
 * - Idle / Loaded: nothing. The `when` has no `else`, so a fifth state would not compile until handled here.
 *
 * The LOGIC is the same as before the design; only the drawing changed.
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
        CogitatorPanel(label = "LOADING / AUSPEX REQUEST", cut = 11.dp, modifier = Modifier.widthIn(max = 440.dp)) {
            CenteredColumn {
                BobbingArt(R.drawable.art_servo_skull, size = 150.dp)
                Spacer(Modifier.height(12.dp))
                Text("Invoking the Machine Spirit", style = MaterialTheme.typography.titleLarge.glowing(SwiftyColors.Gold, 0.3f), color = SwiftyColors.Gold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Text("Downloading ligand $id.", style = MaterialTheme.typography.bodyMedium, color = SwiftyColors.Bone, textAlign = TextAlign.Center)
                Spacer(Modifier.height(18.dp))
                ConsoleRule(color = SwiftyColors.Brass)
                Spacer(Modifier.height(14.dp))
                SecondaryButton("Cancel", onClick = onCancel, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp))
            }
        }
    }
}

@Composable
private fun ErrorDialog(id: String, error: LoadError, onRetry: () -> Unit, onClose: () -> Unit) {
    val retry = error.canRetry()
    Dialog(onDismissRequest = onClose) {
        CogitatorPanel(label = error.panelLabel(), cut = 11.dp, modifier = Modifier.widthIn(max = 440.dp)) {
            CenteredColumn {
                // The error is shown by a picture AND a title AND a plain sentence: never by colour alone.
                ArtImage(glyphArt(error.glyph()), size = 140.dp)
                Spacer(Modifier.height(12.dp))
                Text(error.flavourTitle(), style = MaterialTheme.typography.titleLarge.glowing(SwiftyColors.Gold, 0.3f), color = SwiftyColors.Gold, textAlign = TextAlign.Center)
                // Which ligand: on its own line, because the subject's messages (used word for word) do not name it.
                Text("LIGAND $id", style = SwiftyDataText.medium.glowing(SwiftyColors.Phosphor), color = SwiftyColors.Phosphor, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Text(error.userMessage(id), style = MaterialTheme.typography.bodyMedium, color = SwiftyColors.Bone, textAlign = TextAlign.Center)
                Spacer(Modifier.height(18.dp))
                ConsoleRule(color = SwiftyColors.Brass)
                Spacer(Modifier.height(14.dp))
                if (retry) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PrimaryButton("Retry", onClick = onRetry, modifier = Modifier.weight(1f))
                        SecondaryButton("Close", onClick = onClose, modifier = Modifier.weight(1f))
                    }
                } else {
                    PrimaryButton("OK", onClick = onClose, modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp))
                }
            }
        }
    }
}

/** The painting of each kind of error (the choice itself is pure and tested: [glyph]). */
@DrawableRes
private fun glyphArt(glyph: ErrorGlyph): Int = when (glyph) {
    ErrorGlyph.OFFLINE -> R.drawable.art_broken_antenna
    ErrorGlyph.NOT_FOUND -> R.drawable.art_archive_404
    ErrorGlyph.TIMEOUT -> R.drawable.art_hourglass
    ErrorGlyph.CORRUPT -> R.drawable.art_corrupt_slate
    ErrorGlyph.INVALID -> R.drawable.art_skull_cog_art
    ErrorGlyph.WARNING -> R.drawable.art_servitor
}
