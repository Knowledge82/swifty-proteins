package dev.knowledge82.swiftyproteins.ui

import android.util.Log
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import dev.knowledge82.swiftyproteins.loading.LoadState
import dev.knowledge82.swiftyproteins.net.LoadError

private const val TAG = "SwiftyProteins"

/**
 * Draws the dialog that goes with the current [LoadState], or nothing.
 *
 * - Loading: a modal dialog with a spinner and a Cancel button. A MODAL dialog is the simplest way to block the
 *   screen below while loading (no taps reach the list), and the system Back button closes it as Cancel.
 * - Failed: the message for the error, with "Retry" when repeating makes sense.
 * - Idle / Loaded: nothing. The `when` has no `else`, so a fifth state would not compile until handled here.
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
    AlertDialog(
        onDismissRequest = onCancel,                                    // the system Back button
        properties = DialogProperties(dismissOnClickOutside = false),   // an accidental tap must not cancel
        title = { Text("Loading $id") },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Text("Downloading the ligand...")
            }
        },
        confirmButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

@Composable
private fun ErrorDialog(id: String, error: LoadError, onRetry: () -> Unit, onClose: () -> Unit) {
    val retry = error.canRetry()
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Could not load $id") },
        text = { Text(error.userMessage(id)) },
        confirmButton = {
            if (retry) TextButton(onClick = onRetry) { Text("Retry") }
            else TextButton(onClick = onClose) { Text("OK") }
        },
        dismissButton = {
            if (retry) TextButton(onClick = onClose) { Text("Close") }
        },
    )
}
