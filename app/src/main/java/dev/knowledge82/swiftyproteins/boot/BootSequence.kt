package dev.knowledge82.swiftyproteins.boot

import java.util.Locale

/**
 * The cogitator boot screen (design v2, screen 02): the app's LAUNCH SCREEN. Pure Kotlin, tested.
 *
 * The evaluation sheet asks for a launch screen visible at least 1 second (document 07b). With design v2 that job
 * moves from the system splash (now only the emblem, for as long as Android needs) to this in-app screen: console
 * lines appear one by one, a progress bar fills, and after [DURATION_MILLIS] the app goes on. It depends ONLY on
 * time, so it always ends.
 */
object BootSequence {

    /** How long the boot screen stays. 1600 ms: over the required 1000, with margin. */
    const val DURATION_MILLIS = 1_600L

    /** Number of "[##]" blocks of the progress bar when full. */
    const val PROGRESS_BLOCKS = 5

    /** The console lines. The data-vault line shows the real number of ligands (or that the list is unreadable). */
    fun lines(catalogSize: Int?): List<String> = listOf(
        "> LITANY OF IGNITION ... OK",
        "> MACHINE SPIRIT ... AWAKE",
        if (catalogSize == null) "> DATA-VAULT ... UNREADABLE" else "> DATA-VAULT ... " + String.format(Locale.US, "%,d", catalogSize),
        "> AUSPEX ENGINE ... READY",
        "> SEEKING ADEPT ...",
    )

    /**
     * How many lines are visible after [elapsedMillis]: one more every 250 ms, starting with the first at 0.
     * With [reduceMotion] everything is shown at once (no typing effect), but the screen still lasts as long.
     */
    fun visibleLines(elapsedMillis: Long, total: Int, reduceMotion: Boolean = false): Int {
        if (reduceMotion) return total
        if (elapsedMillis < 0) return 1
        return (1 + (elapsedMillis / LINE_EVERY_MILLIS).toInt()).coerceAtMost(total)
    }

    /** How many progress blocks are filled: proportional to the time, all of them at the end. */
    fun progressBlocks(elapsedMillis: Long): Int {
        if (elapsedMillis <= 0) return 0
        return ((elapsedMillis * PROGRESS_BLOCKS) / DURATION_MILLIS).toInt().coerceAtMost(PROGRESS_BLOCKS)
    }

    /** "[##][##][  ][  ][  ]". */
    fun progressBar(filled: Int): String = (0 until PROGRESS_BLOCKS).joinToString("") { if (it < filled) "[##]" else "[  ]" }

    /** The screen ends exactly when the time is over. */
    fun isDone(elapsedMillis: Long): Boolean = elapsedMillis >= DURATION_MILLIS

    private const val LINE_EVERY_MILLIS = 250L
}
