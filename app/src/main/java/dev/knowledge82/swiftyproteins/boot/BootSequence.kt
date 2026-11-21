package dev.knowledge82.swiftyproteins.boot

import java.util.Locale

/**
 * The cogitator boot screen (design v2, screen 02): the app's LAUNCH SCREEN. Pure Kotlin, tested.
 *
 * The evaluation sheet asks for a launch screen visible at least 1 second (document 07b). With design v2 that job
 * moves from the system splash (now invisible, for as long as Android needs) to this in-app screen: console
 * lines appear one by one, a progress bar fills, and after [DURATION_MILLIS] the app goes on.
 *
 * A TAP skips it, but never before [MIN_VISIBLE_MILLIS]: an early tap is remembered and takes effect at 1 s, so
 * the launch screen is ALWAYS visible for the second the evaluation sheet asks for. It depends ONLY on time and on
 * that tap, so it always ends.
 */
object BootSequence {

    /** How long the boot screen stays if nobody taps: 4200 ms (1600 at first, then 3200, then one second more: the team's choice). */
    const val DURATION_MILLIS = 4_200L

    /** The minimum it is shown, even with a tap: the 1 s of the evaluation sheet. */
    const val MIN_VISIBLE_MILLIS = 1_000L

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
     * How many lines are visible after [elapsedMillis]: one more every 500 ms, starting with the first at 0 (all five
     * at 2 s, well before the end).
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

    /** A tap is allowed to end the screen from this moment on (the "TAP TO CONTINUE" hint appears then). */
    fun canSkip(elapsedMillis: Long): Boolean = elapsedMillis >= MIN_VISIBLE_MILLIS

    /** The screen ends when the time is over, or when the user tapped and the minimum second has passed. */
    fun isDone(elapsedMillis: Long, skipRequested: Boolean = false): Boolean =
        elapsedMillis >= DURATION_MILLIS || (skipRequested && canSkip(elapsedMillis))

    private const val LINE_EVERY_MILLIS = 500L
}
