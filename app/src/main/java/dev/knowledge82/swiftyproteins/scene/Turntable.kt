package dev.knowledge82.swiftyproteins.scene

/**
 * The turntable of the video (bonus VII.5 "video recording of molecule rotation", document 22). Pure, tested.
 *
 * The MOLECULE turns, not the camera: all of it hangs from one node whose rotation around the vertical axis goes from
 * 0 to 360 degrees at a constant speed. Constant, so that the last frame meets the first one and the video can loop
 * without a jump. The camera keeps the pose the user left it in: the video shows what the user was looking at.
 */
object Turntable {

    /** One full turn: long enough to see every side, short enough to share (about 2-4 MB at 30 fps). */
    const val TURN_MILLIS = 6_000L

    /** Frames per second of the video: the usual rate for a phone video, and half the A33's 60 Hz. */
    const val FRAME_RATE = 30

    /**
     * Time for the screen to change before the first frame is recorded: the panels go away, the camera re-aims at the
     * whole screen (the contentPadding animation, document 21) and, the window having changed shape, glides back to its
     * starting distance (SceneView takes ~0.6 s). The video must not start with that movement.
     */
    const val SETTLE_MILLIS = 800L

    /** Safety stop of the encoder, in case the turn never ends (it always should): the turn and one second. */
    const val MAX_RECORDING_MILLIS = TURN_MILLIS + 1_000L

    /** The angle after [elapsedNanos] of a turn that lasts [turnNanos]: 0 to 360, never beyond, never negative. */
    fun angle(elapsedNanos: Long, turnNanos: Long = TURN_MILLIS * 1_000_000L): Float =
        if (turnNanos <= 0) 360f else (360.0 * elapsedNanos.coerceIn(0L, turnNanos) / turnNanos).toFloat()

    /** Has the turn ended at [elapsedNanos]? */
    fun isDone(elapsedNanos: Long, turnNanos: Long = TURN_MILLIS * 1_000_000L): Boolean = elapsedNanos >= turnNanos

    /** "Recording 2.4 / 6.0 s": the line over the scene while it turns. */
    fun label(elapsedMillis: Long): String =
        String.format(java.util.Locale.US, "REC %.1f / %.1f s", elapsedMillis.coerceIn(0L, TURN_MILLIS) / 1000.0, TURN_MILLIS / 1000.0)
}
