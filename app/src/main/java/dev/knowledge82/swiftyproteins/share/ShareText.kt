package dev.knowledge82.swiftyproteins.share

import dev.knowledge82.swiftyproteins.molecule.MolecularFormula
import dev.knowledge82.swiftyproteins.molecule.Molecule

/** The words that travel with the shared image. Pure Kotlin, tested. */
object ShareText {

    /**
     * The band printed under the picture: "Swifty Proteins · 001 · C35H42F2N2O6 · 87 atoms · 90 bonds".
     * The formula in PLAIN digits: the band is drawn with a font that may not have subscript glyphs.
     */
    fun caption(molecule: Molecule): String =
        "Swifty Proteins · ${molecule.id} · ${MolecularFormula.plain(molecule.atoms)} · ${sizeOf(molecule)}"

    /**
     * The text of the message (bonus VII.5: "name, number of atoms, formula"). Three lines:
     *
     *     Ligand ATP · ADENOSINE-5'-TRIPHOSPHATE
     *     C10H16N5O13P3 · 47 atoms · 49 bonds
     *     Viewed in Swifty Proteins
     *
     * Without a name in the file, the first line is just "Ligand ATP".
     */
    fun message(molecule: Molecule): String = buildString {
        append("Ligand ").append(molecule.id)
        molecule.name?.let { append(" · ").append(it) }
        append('\n').append(MolecularFormula.plain(molecule.atoms)).append(" · ").append(sizeOf(molecule))
        append("\nViewed in Swifty Proteins")
    }

    private fun sizeOf(molecule: Molecule) = "${plural(molecule.atoms.size, "atom")} · ${plural(molecule.bonds.size, "bond")}"

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

    /** The status line, or null when nothing is shown ("pict-capture": the design's word for a screenshot). */
    val label: String? get() = when (this) {
        Idle -> null
        Working -> "Preparing pict-capture…"
        Done -> "Pict-capture ready"
        is Failed -> failure.userMessage()
    }
}
