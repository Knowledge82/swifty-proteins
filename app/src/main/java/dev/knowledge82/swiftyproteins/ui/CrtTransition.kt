package dev.knowledge82.swiftyproteins.ui

/**
 * The change of screen as an old cathode-ray tube (bonus VII.2 "smooth transitions", document 23): the screen that
 * leaves SWITCHES OFF (the picture is squeezed into a bright horizontal line, the line into a dot) and the new one
 * SWITCHES ON (the dot stretches into a line, the line opens into the picture).
 *
 * Everything here is pure arithmetic, tested on the JVM; [SwiftyApp] only reads the clock and draws the result.
 *
 * One number says where we are: the COVER, from 0 (the screen fully on) to 1 (off: one dot in the middle).
 *
 *     cover 0 ............ SQUEEZE ............ 1
 *           picture -> a line (vertical squeeze)  line -> a dot (horizontal shrink)
 */
object CrtTransition {

    /** Switching the old screen off, from fully on to the dot. Short: nobody waits for the old screen. */
    const val OFF_MILLIS = 240f

    /** Switching the new screen on, from the dot to fully on. Longer: this is the part people see. */
    const val ON_MILLIS = 420f

    /** The share of the cover spent squeezing the picture into a line; the rest shrinks the line into a dot. */
    const val SQUEEZE = 0.7f

    /**
     * What to draw for one cover value, in pixels around the centre of the screen.
     *
     * @property halfWidth  half the width of the window that still shows the picture (0 = a dot)
     * @property halfHeight half its height (never under half a line: a switched-off tube still shows its line)
     * @property glow       0..1, how white the window is washed: a squeezed picture gets BRIGHTER (the same beam
     *                      energy on fewer lines), so the line glows
     * @property scan       0..1, how strong the scanlines over the window are (they show as the tube warms up)
     * @property dot        0..1, how bright the dot in the middle is: it appears while the line shrinks, and it is
     *                      all that is left when the tube is off (the moment the screens are swapped)
     */
    data class Frame(val halfWidth: Float, val halfHeight: Float, val glow: Float, val scan: Float, val dot: Float)

    /**
     * The frame for [cover] on a screen of [width] x [height] pixels, with a line [line] pixels thick.
     * A cover outside 0..1 is taken as its nearest end.
     */
    fun frame(cover: Float, width: Float, height: Float, line: Float): Frame {
        val c = cover.coerceIn(0f, 1f)
        val squeeze = (c / SQUEEZE).coerceAtMost(1f)
        val shrink = ((c - SQUEEZE) / (1f - SQUEEZE)).coerceIn(0f, 1f)
        return Frame(
            halfWidth = (1f - easeIn(shrink)) * width / 2f,
            halfHeight = maxOf(line / 2f, (1f - easeIn(squeeze)) * height / 2f),
            glow = smooth(squeeze),
            scan = SCAN_MAX * squeeze,
            dot = smooth(shrink),
        )
    }

    /**
     * The cover while switching OFF, [elapsedMillis] after starting from [start]. A change of screen in the middle of
     * another one starts from where that one was, so the time left is shorter (a straight line from [start] to 1).
     */
    fun closing(start: Float, elapsedMillis: Float): Float =
        (start.coerceIn(0f, 1f) + elapsedMillis.coerceAtLeast(0f) / OFF_MILLIS).coerceAtMost(1f)

    /** The cover while switching ON, [elapsedMillis] after starting from [start] (1 = from the dot). */
    fun opening(start: Float, elapsedMillis: Float): Float =
        (start.coerceIn(0f, 1f) - elapsedMillis.coerceAtLeast(0f) / ON_MILLIS).coerceAtLeast(0f)

    /** Scanlines at their strongest: visible, but the picture stays readable through them. */
    const val SCAN_MAX = 0.6f

    // Slow start, fast end: a tube collapses faster and faster as the field dies.
    private fun easeIn(t: Float): Float = t * t

    // The classic smoothstep: 0 and 1 at the ends, no jump at either.
    private fun smooth(t: Float): Float = t * t * (3f - 2f * t)
}
