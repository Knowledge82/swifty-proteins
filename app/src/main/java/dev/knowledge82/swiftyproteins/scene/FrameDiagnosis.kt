package dev.knowledge82.swiftyproteins.scene

import kotlin.math.abs

/**
 * WHY is the scene drawing? (document 21, §19). The 3D view draws on demand: with nothing moving it should stop and the
 * counter should say IDLE. On the A33 it did not. Reading SceneView's code found one cause (§18.4); this class finds
 * the others on the phone itself, where the code can be watched running.
 *
 * Once a second, while frames are being drawn, it sums up what happened in that second:
 *
 *     frames=88 sceneRecompositions=0 contentRecompositions=0 cameraMoves=0
 *
 * - frames with recompositions: something above the scene keeps changing (the cause of §18.4 was of this kind);
 * - frames with camera moves: the camera is drifting (an inertia that never ends, a pose that is never equal);
 * - frames with neither: the request comes from outside our code (SceneView itself, the surface).
 *
 * Pure: the caller hands it the camera position and the frame times; it returns the line to log, or null.
 * Not thread-safe: everything runs on the main thread.
 */
class FrameDiagnosis(private val windowNanos: Long = 1_000_000_000L) {

    private var windowStart = -1L
    private var frames = 0
    private var sceneRecompositions = 0
    private var contentRecompositions = 0
    private var cameraMoves = 0
    private var lastX = Float.NaN
    private var lastY = Float.NaN
    private var lastZ = Float.NaN

    fun onSceneRecomposed() { sceneRecompositions++ }

    fun onContentRecomposed() { contentRecompositions++ }

    /** A presented frame at [timeNanos] with the camera at (x, y, z). Returns the summary when a window closes. */
    fun onFrame(timeNanos: Long, x: Float, y: Float, z: Float): String? {
        if (windowStart < 0) windowStart = timeNanos
        frames++
        if (!lastX.isNaN() && (abs(x - lastX) > MOVE_EPSILON || abs(y - lastY) > MOVE_EPSILON || abs(z - lastZ) > MOVE_EPSILON)) cameraMoves++
        lastX = x; lastY = y; lastZ = z
        if (timeNanos - windowStart < windowNanos) return null
        val line = "frames=$frames sceneRecompositions=$sceneRecompositions contentRecompositions=$contentRecompositions cameraMoves=$cameraMoves"
        windowStart = timeNanos
        frames = 0; sceneRecompositions = 0; contentRecompositions = 0; cameraMoves = 0
        return line
    }

    companion object {
        /** A camera that moves less than this (angstrom) between two frames is standing still. */
        const val MOVE_EPSILON = 1e-4f
        const val LOG_TAG = "SwiftyFrames"
    }
}
