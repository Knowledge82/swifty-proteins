package dev.knowledge82.swiftyproteins.boot

import java.util.Locale

/**
 * The cogitator boot screen (design v2, screen 02): the app's LAUNCH SCREEN. Pure Kotlin, tested.
 *
 * The evaluation sheet asks for a launch screen visible at least 1 second (document 07b). With design v2 that job
 * moves from the system splash (now invisible, for as long as Android needs) to this in-app screen. Two phases:
 *  1. 0 .. [LINES_PHASE_MILLIS] (65 %): the console lines appear one by one and the progress bar fills;
 *  2. the rest (35 %): everything is loaded and "> DONE" blinks; after [DURATION_MILLIS] the app goes on.
 *
 * A TAP skips it, but never before [MIN_VISIBLE_MILLIS]: an early tap is remembered and takes effect at 1 s, so
 * the launch screen is ALWAYS visible for the second the evaluation sheet asks for. It depends ONLY on time and on
 * that tap, so it always ends.
 */
object BootSequence {

    /** How long the boot screen stays if nobody taps: 4200 ms (1600 at first, then 3200, then one second more: the team's choice). */
    const val DURATION_MILLIS = 4_200L

    /** End of the first phase (65 % of the time, chosen by the team): all lines shown, bar full, "DONE" from here. */
    const val LINES_PHASE_MILLIS = DURATION_MILLIS * 65 / 100

    /** "DONE" is lit for this long, then off for as long: 1470 ms of the second phase = three full blinks. */
    const val DONE_BLINK_MILLIS = 245L

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
     * How many lines are visible after [elapsedMillis]: the first phase is split in [total] equal slices, one line
     * per slice, the first at 0 (with 5 lines: one every 546 ms, the fifth at 2184 ms).
     * With [reduceMotion] everything is shown at once (no typing effect), but the screen still lasts as long.
     */
    fun visibleLines(elapsedMillis: Long, total: Int, reduceMotion: Boolean = false): Int {
        if (reduceMotion) return total
        if (elapsedMillis < 0 || total <= 0) return total.coerceAtMost(1)
        val every = LINES_PHASE_MILLIS / total
        return (1 + (elapsedMillis / every).toInt()).coerceAtMost(total)
    }

    /** How many progress blocks are filled: proportional to the time, all of them at the end of the first phase. */
    fun progressBlocks(elapsedMillis: Long): Int {
        if (elapsedMillis <= 0) return 0
        return ((elapsedMillis * PROGRESS_BLOCKS) / LINES_PHASE_MILLIS).toInt().coerceAtMost(PROGRESS_BLOCKS)
    }

    /** Everything is loaded: the second phase ("> DONE") has started. */
    fun isComplete(elapsedMillis: Long): Boolean = elapsedMillis >= LINES_PHASE_MILLIS

    /**
     * Is "> DONE" drawn at this moment? Off before the second phase; then on/off every [DONE_BLINK_MILLIS], starting ON.
     * Computed from the same clock as the rest (no separate animation), so it is tested like the rest.
     * With [reduceMotion]: steadily on.
     */
    fun doneVisible(elapsedMillis: Long, reduceMotion: Boolean = false): Boolean {
        if (!isComplete(elapsedMillis)) return false
        if (reduceMotion) return true
        return ((elapsedMillis - LINES_PHASE_MILLIS) / DONE_BLINK_MILLIS) % 2 == 0L
    }

    /** "[##][##][  ][  ][  ]". */
    fun progressBar(filled: Int): String = (0 until PROGRESS_BLOCKS).joinToString("") { if (it < filled) "[##]" else "[  ]" }

    /** A tap is allowed to end the screen from this moment on (no hint on screen; TalkBack announces the action). */
    fun canSkip(elapsedMillis: Long): Boolean = elapsedMillis >= MIN_VISIBLE_MILLIS

    /** The screen ends when the time is over, or when the user tapped and the minimum second has passed. */
    fun isDone(elapsedMillis: Long, skipRequested: Boolean = false): Boolean =
        elapsedMillis >= DURATION_MILLIS || (skipRequested && canSkip(elapsedMillis))
}
