package dev.knowledge82.swiftyproteins.share

import dev.knowledge82.swiftyproteins.molecule.Molecule

/** The words that travel with the shared image. Pure Kotlin, tested. */
object ShareText {

    /** The band printed under the picture, and the text of the message: "Swifty Proteins · 001 · 87 atoms · 90 bonds". */
    fun caption(molecule: Molecule): String =
        "Swifty Proteins · ${molecule.id} · ${plural(molecule.atoms.size, "atom")} · ${plural(molecule.bonds.size, "bond")}"

    /** The title of Android's share sheet. */
    fun chooserTitle(molecule: Molecule): String = "Share ligand ${molecule.id}"

    private fun plural(n: Int, word: String) = if (n == 1) "1 $word" else "$n ${word}s"
}

/** Why a capture failed, in words the user can act on. The Android code maps PixelCopy's codes to these. */
enum class CaptureFailure {
    /** The 3D view has not drawn its first frame yet. */
    NOT_READY,
    /** No 3D surface found on screen (should not happen on this screen). */
    NO_SCENE,
    /** The copy did not finish in time. */
    TIMEOUT,
    /** The image could not be written to the phone. */
    WRITE_FAILED,
    /** No app on the phone can receive an image. */
    NO_RECEIVER,
    /** Anything else. */
    OTHER;

    fun userMessage(): String = when (this) {
        NOT_READY -> "The 3D view is not ready yet. Try again in a second."
        NO_SCENE -> "There is no 3D view to capture."
        TIMEOUT -> "Capturing the image took too long. Try again."
        WRITE_FAILED -> "The image could not be saved on the phone."
        NO_RECEIVER -> "No app on this phone can receive an image."
        OTHER -> "The image could not be captured. Try again."
    }
}

/** What the Share button is doing: the small status line above the atom card shows it. */
sealed interface ShareStatus {
    data object Idle : ShareStatus
    data object Working : ShareStatus
    data object Done : ShareStatus
    data class Failed(val failure: CaptureFailure) : ShareStatus

    /** A second tap while working is ignored (double taps would open two share sheets). */
    val canStart: Boolean get() = this !is Working

    /** The status line, or null when nothing is shown. */
    val label: String? get() = when (this) {
        Idle -> null
        Working -> "Preparing image…"
        Done -> "Image ready"
        is Failed -> failure.userMessage()
    }
}
