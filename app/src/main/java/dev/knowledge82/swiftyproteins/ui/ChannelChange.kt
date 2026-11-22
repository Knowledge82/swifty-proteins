package dev.knowledge82.swiftyproteins.ui

/**
 * The change of screen as the cogitator REDRAWING its tube (document 26): a bright phosphor line sweeps down and wipes
 * the old screen to black behind it, the screens are swapped in the black, and the same line sweeps down again
 * drawing the new screen behind it, like a frame being written line by line on a CRT.
 *
 * Fifth version (document 26, §3.3, tells the four before it, all seen on the A33): TV static, a pale-green flash, a
 * blink with an afterglow, a dark pulse. The tube switching ON stays for the cold start only ([CrtTransition]).
 *
 * Pure arithmetic, tested; [CrtSwitch] reads the clock and draws.
 *
 *     progress 0 ──── SWAP_AT ──────────────── 1          (MILLIS)
 *              the line wipes down │ the line draws down
 *              (black above it)    │ (black below it)
 */
object ChannelChange {

    /**
     * The whole change: two sweeps of the screen. The speed of the proposal (its GIF); 150 ms was tried on the A33 and
     * was too fast to follow (document 26, §3).
     */
    const val MILLIS = 220f

    /** Where the screens swap: the first sweep has reached the bottom, the screen is all black. */
    const val SWAP_AT = 0.45f

    /**
     * What to draw at one moment, in fractions of the screen's height (0 = top, 1 = bottom).
     *
     * @property blackFrom where the black starts
     * @property blackTo   where it ends (blackFrom == blackTo: no black)
     * @property line      where the bright line is
     */
    data class Frame(val blackFrom: Float, val blackTo: Float, val line: Float)

    /** How far the change is, 0..1, [elapsedMillis] after starting from [start]. */
    fun progress(start: Float, elapsedMillis: Float): Float =
        (start.coerceIn(0f, 1f) + elapsedMillis.coerceAtLeast(0f) / MILLIS).coerceAtMost(1f)

    /** The line moves at a CONSTANT speed in each sweep, as the beam of a tube does. */
    fun frame(progress: Float): Frame {
        val p = progress.coerceIn(0f, 1f)
        return if (p < SWAP_AT) {
            val y = p / SWAP_AT
            Frame(blackFrom = 0f, blackTo = y, line = y)          // wiping: black above the line, the old screen below
        } else {
            val y = (p - SWAP_AT) / (1f - SWAP_AT)
            Frame(blackFrom = y, blackTo = 1f, line = y)          // drawing: the new screen above the line, black below
        }
    }

    /** Have the screens been swapped at [progress]? */
    fun swapped(progress: Float): Boolean = progress >= SWAP_AT

    /**
     * Where a NEW change starts when one is still running at [progress]: a wipe in progress simply goes on (it is
     * already hiding the screen that is leaving); a drawing in progress starts a new wipe from the top.
     */
    fun restartAt(progress: Float): Float = if (progress >= 0f && progress < SWAP_AT) progress else 0f
}
