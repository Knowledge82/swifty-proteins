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
