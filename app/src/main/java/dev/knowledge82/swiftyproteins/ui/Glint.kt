package dev.knowledge82.swiftyproteins.ui

/**
 * The glint over the name "Swifty Proteins" in every header (document 23): every few seconds a slanted band of light
 * runs over the gilded letters from left to right, like light over polished metal. The same effect as the logo of
 * Chaos Mode (a Flutter ShaderMask with srcATop), redone in Compose.
 *
 * Pure: where the band is at each moment. The drawing is in [GlintText].
 */
object Glint {

    /** From the start of one pass to the start of the next. */
    const val EVERY_MILLIS = 6000L

    /** How long one pass takes. The rest of the cycle there is no band at all (and no frames are drawn for it). */
    const val SWEEP_MILLIS = 900L

    /** The first pass waits until the screen has switched on (document 23: the CRT change takes 660 ms). */
    const val FIRST_DELAY_MILLIS = 1200L

    /** Half the width of the band, as a share of the text's width. */
    const val HALF_BAND = 0.17f

    /** Where the centre of the band starts and ends, as a share of the width: outside the text on both sides. */
    const val FROM = -0.2f
    const val TO = 1.2f

    /** How far one pass has gone, 0..1, after [elapsedMillis]. */
    fun progress(elapsedMillis: Float): Float = (elapsedMillis / SWEEP_MILLIS).coerceIn(0f, 1f)

    /**
     * The centre of the band, as a share of the text's width, for a [progress] 0..1. It eases in and out (slow at
     * the edges, fast over the middle), as a reflection moves when a piece of metal turns under a lamp.
     */
    fun center(progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        return FROM + (TO - FROM) * (p * p * (3f - 2f * p))
    }
}
