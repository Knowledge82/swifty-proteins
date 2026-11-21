package dev.knowledge82.swiftyproteins.scene

import kotlin.math.abs

/**
 * WHY is the scene drawing? (document 21, §19). The 3D view draws on demand: with nothing moving it should stop and the
 * counter should say IDLE. On the A33 it did not. Reading SceneView's code found one cause (§18.4); this class finds
 * the others on the phone itself, where the code can be watched running.
 *
 * Once a second, while frames are being drawn, it sums up what happened in that second:
 *
 *     frames=88 sceneRecompositions=0 contentRecompositions=0 cameraMoves=0 changed=[]
 *
 * `changed` names the PARAMETERS of the scene that were different at its recompositions, with how many times:
 * `contentPadding:9` (a new value), `onTap~:3` (an equal value but a NEW object: what a careless lambda does).
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
    private val changed = sortedMapOf<String, Int>()
    private var lastX = Float.NaN
    private var lastY = Float.NaN
    private var lastZ = Float.NaN

    fun onSceneRecomposed(changedParameters: Collection<String> = emptyList()) {
        sceneRecompositions++
        for (name in changedParameters) changed[name] = (changed[name] ?: 0) + 1
    }

    fun onContentRecomposed() { contentRecompositions++ }

    /** A presented frame at [timeNanos] with the camera at (x, y, z). Returns the summary when a window closes. */
    fun onFrame(timeNanos: Long, x: Float, y: Float, z: Float): String? {
        if (windowStart < 0) windowStart = timeNanos
        frames++
        if (!lastX.isNaN() && (abs(x - lastX) > MOVE_EPSILON || abs(y - lastY) > MOVE_EPSILON || abs(z - lastZ) > MOVE_EPSILON)) cameraMoves++
        lastX = x; lastY = y; lastZ = z
        if (timeNanos - windowStart < windowNanos) return null
        val line = "frames=$frames sceneRecompositions=$sceneRecompositions contentRecompositions=$contentRecompositions " +
            "cameraMoves=$cameraMoves changed=${changed.entries.joinToString(prefix = "[", postfix = "]") { "${it.key}:${it.value}" }}"
        windowStart = timeNanos
        frames = 0; sceneRecompositions = 0; contentRecompositions = 0; cameraMoves = 0; changed.clear()
        return line
    }

    companion object {
        /**
         * Which of [current] differ from [previous] (same order, named by [names]), and fills [previous] with [current].
         * "name" for a different value, "name~" for an equal value in a new object. The first call reports nothing.
         */
        fun changes(names: List<String>, previous: Array<Any?>, current: List<Any?>, first: Boolean): List<String> {
            val out = ArrayList<String>()
            for (i in names.indices) {
                val old = previous[i]
                val new = current[i]
                if (!first) {
                    if (old != new) out += names[i] else if (old !== new) out += names[i] + "~"
                }
                previous[i] = new
            }
            return out
        }

        /** A camera that moves less than this (angstrom) between two frames is standing still. */
        const val MOVE_EPSILON = 1e-4f
        const val LOG_TAG = "SwiftyFrames"
    }
}
