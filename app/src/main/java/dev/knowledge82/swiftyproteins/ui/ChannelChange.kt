package dev.knowledge82.swiftyproteins.ui

/**
 * The change of screen as a DARK PULSE of the cogitator's tube (document 26): for an instant the glass sinks into a
 * smouldering phosphor green, dark at the edges, the screens are swapped while it covers everything, and it falls
 * away from the new picture. Short, and dark rather than bright: a surge in the machine, not a lamp.
 *
 * Fourth version (document 26, §3.3, tells the three before it, all seen on the A33): TV static ("old television on
 * Earth"), a pale-green flash (too bright), a blink to black with an afterglow (did not read on the phone).
 * The tube switching ON stays for the cold start only ([CrtTransition]).
 *
 * Pure arithmetic, tested; [CrtSwitch] reads the clock and draws.
 *
 *     progress 0 ──── SWAP_AT ───────────────────────── 1          (MILLIS)
 *              the pulse rises │ swap under the full pulse, it falls fast, then fades
 */
object ChannelChange {

    /** The whole change. */
    const val MILLIS = 160f

    /** Where the screens swap: the pulse covers everything there (its colours are opaque), nothing of either shows. */
    const val SWAP_AT = 0.3f

    /** The scanlines across the pulse at its strongest: dark lines, the texture of a tube. */
    const val SCAN_MAX = 0.35f

    /**
     * What to draw at one moment.
     *
     * @property pulse 0..1, how strongly the dark green covers the screen (1 = only the pulse, the picture hidden)
     * @property scan  0..[SCAN_MAX], the scanlines over it
     */
    data class Frame(val pulse: Float, val scan: Float)

    /** How far the change is, 0..1, [elapsedMillis] after starting from [start]. */
    fun progress(start: Float, elapsedMillis: Float): Float =
        (start.coerceIn(0f, 1f) + elapsedMillis.coerceAtLeast(0f) / MILLIS).coerceAtMost(1f)

    fun frame(progress: Float): Frame {
        val p = progress.coerceIn(0f, 1f)
        val pulse = if (p < SWAP_AT) {
            p / SWAP_AT                       // up in a straight line: about 50 ms
        } else {
            val t = (p - SWAP_AT) / (1f - SWAP_AT)
            (1f - t) * (1f - t)               // down fast, then a soft tail
        }
        return Frame(pulse = pulse, scan = SCAN_MAX * pulse)
    }

    /** Have the screens been swapped at [progress]? */
    fun swapped(progress: Float): Boolean = progress >= SWAP_AT

    /**
     * Where a NEW change starts when one is still running with [pulse] on screen: on the rising side, at the same
     * pulse. The picture never comes back to show a screen that is not the one wanted.
     */
    fun restartAt(pulse: Float): Float = pulse.coerceIn(0f, 1f) * SWAP_AT
}
