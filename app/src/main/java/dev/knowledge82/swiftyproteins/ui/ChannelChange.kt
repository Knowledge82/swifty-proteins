package dev.knowledge82.swiftyproteins.ui

/**
 * The change of screen as a FLASH of the cogitator's tube (document 26): the whole glass lights up for an
 * instant in pale phosphor green, the screens are swapped while it is fully lit, and the light falls away from the
 * new picture. Short and bright, like a surge through the machine; not the static of a television on Earth (that was
 * the first version, and it read as "old TV", not as Mechanicus: document 26, §3.3).
 *
 * The switching ON of the tube stays for the cold start only ([CrtTransition]).
 *
 * Pure arithmetic, tested; [CrtSwitch] reads the clock and draws.
 *
 *     progress 0 ──── SWAP_AT ───────────────────────── 1          (MILLIS)
 *              the light rises │ swap under full light, the light falls fast, then fades
 */
object ChannelChange {

    /** The whole change: half of the first version's 320 ms. A blink. */
    const val MILLIS = 160f

    /** Where the screens swap: the glass is fully lit there, nothing of either screen shows. */
    const val SWAP_AT = 0.3f

    /** The faint scanlines over the light, strongest at the peak (it is a tube, not a white page). */
    const val SCAN_MAX = 0.25f

    /**
     * What to draw at one moment.
     *
     * @property flash 0..1, how strongly the light covers the screen (1 = only light, the picture hidden)
     * @property scan  0..1, how strong the scanlines over the light are
     */
    data class Frame(val flash: Float, val scan: Float)

    /** How far the change is, 0..1, [elapsedMillis] after starting from [start]. */
    fun progress(start: Float, elapsedMillis: Float): Float =
        (start.coerceIn(0f, 1f) + elapsedMillis.coerceAtLeast(0f) / MILLIS).coerceAtMost(1f)

    fun frame(progress: Float): Frame {
        val p = progress.coerceIn(0f, 1f)
        val flash = if (p < SWAP_AT) {
            p / SWAP_AT                       // up in a straight line: about 50 ms
        } else {
            val t = (p - SWAP_AT) / (1f - SWAP_AT)
            (1f - t) * (1f - t)               // down fast, then a soft tail: how a flash fades from the eye
        }
        return Frame(flash = flash, scan = SCAN_MAX * flash)
    }

    /** Have the screens been swapped at [progress]? */
    fun swapped(progress: Float): Boolean = progress >= SWAP_AT

    /**
     * Where a NEW change starts when one is still running with [flash] on screen: on the rising side, at the same
     * light. The light never drops to show a screen that is not the one wanted.
     */
    fun restartAt(flash: Float): Float = flash.coerceIn(0f, 1f) * SWAP_AT
}
