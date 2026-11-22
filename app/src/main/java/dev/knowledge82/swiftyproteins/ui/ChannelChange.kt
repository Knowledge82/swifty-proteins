package dev.knowledge82.swiftyproteins.ui

/**
 * The change of screen as an INTERLACED redraw of the cogitator's tube (document 26): the screen is cut into thin
 * horizontal bands; the old picture goes out band by band in two passes, first the odd bands, then the even ones,
 * top to bottom, the way a CRT writes an interlaced frame; the screens are swapped in the black; and the new picture
 * comes back in the same two passes. Each band blinks phosphor green the instant it changes.
 *
 * Sixth version (document 26, §3.3, tells the five before it, all tried on the A33). The tube switching ON stays for
 * the cold start only ([CrtTransition]).
 *
 * Pure arithmetic, tested; [CrtSwitch] reads the clock and draws one rectangle per band.
 *
 *     progress 0 ─────────── SWAP_AT ──────────────────── 1          (MILLIS)
 *              odd bands out, even out │ odd bands back, even back
 */
object ChannelChange {

    /** The whole change: four passes over the screen. */
    const val MILLIS = 200f

    /** Where the screens swap: every band is black. */
    const val SWAP_AT = 0.45f

    /** The height of a band, in dp: thin enough to read as scanlines, thick enough to see each pass. */
    const val BAND_DP = 4

    /** How long a band blinks green after it changes, as a share of its half of the change. */
    const val BLINK_SPAN = 0.025f

    /** The green blink at its brightest: going out, and coming back (softer, over the new picture). */
    const val BLINK_OUT = 0.6f
    const val BLINK_IN = 0.45f

    /** One band at one moment: black or not, and how strongly it blinks green (0 = not at all). */
    data class Band(val black: Boolean, val blink: Float)

    /** How far the change is, 0..1, [elapsedMillis] after starting from [start]. */
    fun progress(start: Float, elapsedMillis: Float): Float =
        (start.coerceIn(0f, 1f) + elapsedMillis.coerceAtLeast(0f) / MILLIS).coerceAtMost(1f)

    /**
     * When band [k] of [count] changes, 0..1 within each half: the ODD bands in the first half of a pass (top to
     * bottom), then the EVEN bands in the second half. Always below 1, so at the end of each half every band changed.
     */
    fun bandTime(k: Int, count: Int): Float {
        val perPass = (count + 1) / 2
        val pass = if (k % 2 == 1) 0 else 1
        return (pass + (k / 2).toFloat() / perPass) / 2f
    }

    /** Band [k] of [count] at [progress]. */
    fun band(k: Int, count: Int, progress: Float): Band {
        val p = progress.coerceIn(0f, 1f)
        if (p >= 1f) return Band(black = false, blink = 0f)        // the end: the new picture, clean
        val t = bandTime(k, count)
        // A band changes the moment its half passes its time: strictly AFTER it, so at 0 nothing has gone out yet,
        // and at the swap (0 of the second half) nothing has come back yet. It blinks just after it changes.
        return if (p < SWAP_AT) {
            val l = p / SWAP_AT
            Band(black = l > t, blink = if (l > t && l - t <= BLINK_SPAN) BLINK_OUT else 0f)
        } else {
            val l = (p - SWAP_AT) / (1f - SWAP_AT)
            Band(black = l <= t, blink = if (l > t && l - t <= BLINK_SPAN) BLINK_IN else 0f)
        }
    }

    /** How many bands cover a screen [heightPx] tall, with bands [bandPx] tall (the last one may be cut). */
    fun bandCount(heightPx: Float, bandPx: Float): Int = if (bandPx <= 0f) 1 else kotlin.math.ceil(heightPx / bandPx).toInt().coerceAtLeast(1)

    /** Have the screens been swapped at [progress]? */
    fun swapped(progress: Float): Boolean = progress >= SWAP_AT

    /**
     * Where a NEW change starts when one is still running at [progress]: going out, it simply goes on (it is already
     * hiding the screen that leaves); coming back, a new pass out starts from the beginning.
     */
    fun restartAt(progress: Float): Float = if (progress >= 0f && progress < SWAP_AT) progress else 0f
}
