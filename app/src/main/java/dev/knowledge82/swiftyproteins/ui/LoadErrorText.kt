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

/**
 * The flavour title of the error dialog (design v2): a Mechanicus headline ABOVE the plain message
 * ([userMessage]), never instead of it. Same exhaustive `when` rule.
 */
fun LoadError.flavourTitle(): String = when (this) {
    is LoadError.NoConnection -> "The Noosphere is Silent"
    is LoadError.Timeout -> "Time Has Forsaken Us"
    is LoadError.NotFound -> "The Archive is Empty"
    is LoadError.ServerError -> "The Forge is Troubled"
    is LoadError.Incomplete -> "The Transmission is Severed"
    is LoadError.TooLarge -> "A Pattern Too Vast"
    is LoadError.Corrupt -> "The Data-Slate is Damaged"
    is LoadError.InvalidId -> "The Rune is Invalid"
    is LoadError.NetworkError -> "The Noosphere is Disturbed"
    is LoadError.Unexpected -> "The Machine Spirit is Displeased"
}

/** The small tab on the dialog frame: the KIND of failure in console style ("NET / OFFLINE", "HTTP / 404"). */
fun LoadError.panelLabel(): String = when (this) {
    is LoadError.NoConnection -> "NET / OFFLINE"
    is LoadError.Timeout -> "NET / TIMEOUT"
    is LoadError.NotFound -> "HTTP / 404"
    is LoadError.ServerError -> "HTTP / $code"
    is LoadError.Incomplete -> "NET / SEVERED"
    is LoadError.TooLarge -> "DATA / OVERSIZE"
    is LoadError.Corrupt -> "DATA / CORRUPT"
    is LoadError.InvalidId -> "INPUT / INVALID"
    is LoadError.NetworkError -> "NET / FAULT"
    is LoadError.Unexpected -> "SYSTEM / FAULT"
}

/**
 * Which picture goes with each error. The design drew five (offline, not found, timeout, corrupt, invalid); our
 * other five errors get the generic one (WARNING). Kept as an enum (pure Kotlin) so the choice can be tested; the
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
