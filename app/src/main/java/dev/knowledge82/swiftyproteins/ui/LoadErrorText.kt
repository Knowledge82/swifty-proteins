package dev.knowledge82.swiftyproteins.ui

import dev.knowledge82.swiftyproteins.net.LoadError

/**
 * What the user reads when a load fails. Pure Kotlin (no Android): it can be tested on the JVM.
 *
 * `when` over a sealed interface WITHOUT an `else` branch: if tomorrow someone adds a new [LoadError],
 * this file stops compiling until it gets a message. A new error can never reach the screen mute.
 *
 * The technical detail (the reason text, the parser failure) is NOT shown: the user cannot act on it.
 * It goes to Logcat instead (see `LoadStateDialogs`).
 */
fun LoadError.userMessage(id: String): String = when (this) {
    is LoadError.NoConnection ->
        "There is no network connection. Check your Wi-Fi or mobile data and try again."
    is LoadError.Timeout ->
        "The server took too long to answer. Try again in a moment."
    is LoadError.NotFound ->
        "The ligand \"$id\" does not exist on the server (error 404)."
    is LoadError.ServerError ->
        "The server had a problem (error $code). Try again later."
    is LoadError.Incomplete ->
        "The download was cut off before it finished. Try again."
    is LoadError.TooLarge ->
        "The file for \"$id\" is far bigger than any ligand file can be, so it was discarded."
    is LoadError.Corrupt ->
        "The file for \"$id\" arrived, but it is damaged or has an unexpected format."
    is LoadError.InvalidId ->
        "\"$id\" is not a valid ligand identifier."
    is LoadError.NetworkError ->
        "A network error happened. Try again."
    is LoadError.Unexpected ->
        "Something unexpected went wrong. Try again."
}

/**
 * Does it make sense to offer a "Retry" button? Only if repeating the same request can give another answer.
 *
 * - NotFound, InvalidId, TooLarge: the same request gets the same answer. No button.
 * - Corrupt: YES, on purpose. A hotel Wi-Fi login page answers "200 OK" with HTML instead of the CIF file, which
 *   arrives as "corrupt"; once the user logs in, retrying works.
 */
fun LoadError.canRetry(): Boolean = when (this) {
    is LoadError.NotFound, is LoadError.InvalidId, is LoadError.TooLarge -> false
    is LoadError.NoConnection, is LoadError.Timeout, is LoadError.ServerError,
    is LoadError.Incomplete, is LoadError.Corrupt, is LoadError.NetworkError,
    is LoadError.Unexpected -> true
}

/** The short title of the error dialog (the design puts a title above the message). Same exhaustive `when` rule. */
fun LoadError.title(): String = when (this) {
    is LoadError.NoConnection -> "No network connection"
    is LoadError.Timeout -> "Server timeout"
    is LoadError.NotFound -> "Ligand not found (404)"
    is LoadError.ServerError -> "Server error"
    is LoadError.Incomplete -> "Download interrupted"
    is LoadError.TooLarge -> "File too large"
    is LoadError.Corrupt -> "Received data is corrupt"
    is LoadError.InvalidId -> "Invalid ligand ID"
    is LoadError.NetworkError -> "Network error"
    is LoadError.Unexpected -> "Something went wrong"
}

/**
 * Which picture goes with each error. The design drew five (offline, not found, timeout, corrupt, invalid); our
 * other five errors get the generic warning sign. Kept as an enum (pure Kotlin) so the choice can be tested; the
 * screen turns it into a drawable.
 */
enum class ErrorGlyph { OFFLINE, NOT_FOUND, TIMEOUT, CORRUPT, INVALID, WARNING }

fun LoadError.glyph(): ErrorGlyph = when (this) {
    is LoadError.NoConnection, is LoadError.NetworkError -> ErrorGlyph.OFFLINE
    is LoadError.Timeout -> ErrorGlyph.TIMEOUT
    is LoadError.NotFound -> ErrorGlyph.NOT_FOUND
    is LoadError.Corrupt, is LoadError.Incomplete -> ErrorGlyph.CORRUPT
    is LoadError.InvalidId -> ErrorGlyph.INVALID
    is LoadError.ServerError, is LoadError.TooLarge, is LoadError.Unexpected -> ErrorGlyph.WARNING
}
