package dev.knowledge82.swiftyproteins.ui

import kotlin.math.cbrt

/**
 * The change of screen as the cogitator's tube BLINKING (document 26): the old screen goes dark, the screens are swapped
 * in the black, and the new one comes up out of it under a pale green AFTERGLOW of the phosphor, which fades.
 * Black first, then light: the eye rests on the black, and the glow says "a tube", not "a lamp".
 *
 * Third version (document 26, §3): a TV's static read as "old television on Earth"; a pale-green flash over the whole
 * glass was too bright on a dark app. The tube switching ON stays for the cold start only ([CrtTransition]).
 *
 * Pure arithmetic, tested; [CrtSwitch] reads the clock and draws.
 *
 *     progress 0 ── SWAP_AT ──────────────────────────────── 1          (MILLIS)
 *              to black │ swap in the black; the black lifts fast, the afterglow rises over the new picture and fades
 */
object ChannelChange {

    /** The whole change: a blink. */
    const val MILLIS = 180f

    /** Where the screens swap: the glass is fully black there, nothing of either screen shows. */
    const val SWAP_AT = 0.3f

    /** The afterglow at its brightest: the new picture shows through it. */
    const val GLOW_MAX = 0.6f

    /** The scanlines at their strongest, over the black and the glow. */
    const val SCAN_MAX = 0.4f

    /**
     * What to draw at one moment, in this order: the [glow] over the picture, the [black] over that, the scanlines.
     *
     * @property black 0..1, the dark glass over the picture (1 = only black, the picture hidden)
     * @property glow  0..[GLOW_MAX], the green afterglow of the phosphor over the NEW picture
     * @property scan  0..[SCAN_MAX], the scanlines
     */
    data class Frame(val black: Float, val glow: Float, val scan: Float)

    /** How far the change is, 0..1, [elapsedMillis] after starting from [start]. */
    fun progress(start: Float, elapsedMillis: Float): Float =
        (start.coerceIn(0f, 1f) + elapsedMillis.coerceAtLeast(0f) / MILLIS).coerceAtMost(1f)

    fun frame(progress: Float): Frame {
        val p = progress.coerceIn(0f, 1f)
        if (p < SWAP_AT) {
            val b = p / SWAP_AT   // to black in a straight line: about 55 ms
            return Frame(black = b, glow = 0f, scan = SCAN_MAX * b)
        }
        // u goes from 1 (the swap) to 0 (the end). The black lifts as u³ (fast at once); the glow is u²·(1 - u³):
        // nothing under full black, then up as the black lifts, then down to nothing, scaled so its peak is GLOW_MAX.
        val u = 1f - (p - SWAP_AT) / (1f - SWAP_AT)
        val black = u * u * u
        val glow = GLOW_MAX * u * u * (1f - black) / GLOW_SHAPE_PEAK
        return Frame(black = black, glow = glow, scan = SCAN_MAX * maxOf(black, glow))
    }

    /** Have the screens been swapped at [progress]? */
    fun swapped(progress: Float): Boolean = progress >= SWAP_AT

    /**
     * Where a NEW change starts when one is still running with [black] on screen: on the darkening side, at the same
     * black. The picture never comes back to show a screen that is not the one wanted.
     */
    fun restartAt(black: Float): Float = black.coerceIn(0f, 1f) * SWAP_AT

    // The peak of u²·(1 - u³) on 0..1: at u³ = 2/5 (where its derivative 2u - 5u⁴ is zero), u²·3/5.
    private val GLOW_SHAPE_PEAK: Float = cbrt(0.4f).let { it * it * 0.6f }
}
