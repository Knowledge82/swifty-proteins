package dev.knowledge82.swiftyproteins.ui

/**
 * The change of screen as an old television CHANGING CHANNEL (document 26): for a moment the picture drowns in
 * static ("snow"), the screens are swapped under it, and the static clears from the new picture while a bright bar
 * of the vertical hold rolls down it. No squeezing, nothing opening from the centre: that is the tube switching ON,
 * kept only for the cold start ([CrtTransition]).
 *
 * Pure arithmetic, tested; [CrtSwitch] reads the clock and draws.
 *
 *     progress 0 ........ SWAP_AT ................................ 1
 *              snow rises        the screens swap, snow clears, the bar rolls down
 */
object ChannelChange {

    /** The whole change: short, a flick of the dial. */
    const val MILLIS = 320f

    /** Where the screens swap: the static is complete there, nothing of either screen shows. */
    const val SWAP_AT = 0.35f

    /** The strongest scanlines (over the picture while the static rises and clears). */
    const val SCAN_MAX = 0.5f

    /**
     * What to draw at one moment.
     *
     * @property snow 0..1, how much the static covers the picture (1 = only static, the picture hidden)
     * @property roll where the vertical-hold bar is, 0 (top) to 1 (bottom), or null when there is none (before the swap)
     * @property scan 0..1, how strong the scanlines are
     */
    data class Frame(val snow: Float, val roll: Float?, val scan: Float)

    /** How far the change is, 0..1, [elapsedMillis] after starting from [start]. */
    fun progress(start: Float, elapsedMillis: Float): Float =
        (start.coerceIn(0f, 1f) + elapsedMillis.coerceAtLeast(0f) / MILLIS).coerceAtMost(1f)

    fun frame(progress: Float): Frame {
        val p = progress.coerceIn(0f, 1f)
        if (p < SWAP_AT) {
            // The static RISES in a straight line: quick and plain, like the dial between two stations.
            val s = p / SWAP_AT
            return Frame(snow = s, roll = null, scan = SCAN_MAX * s)
        }
        // After the swap it CLEARS slowly at first and then fast (1 - t²): the new picture "locks in".
        val t = (p - SWAP_AT) / (1f - SWAP_AT)
        return Frame(snow = 1f - t * t, roll = t, scan = SCAN_MAX * (1f - t))
    }

    /** Have the screens been swapped at [progress]? */
    fun swapped(progress: Float): Boolean = progress >= SWAP_AT

    /**
     * Where a NEW change starts when one is still running with [snow] on screen: on the rising side, at the same
     * amount of static. The static never drops back to show a screen that is not the one wanted.
     */
    fun restartAt(snow: Float): Float = snow.coerceIn(0f, 1f) * SWAP_AT
}
