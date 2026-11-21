package dev.knowledge82.swiftyproteins.scene

/** What the frame counter shows. */
sealed interface FpsReading {
    /** The scene is not drawing: nothing changed, so SceneView parked the render loop (it draws ON DEMAND). */
    data object Idle : FpsReading

    /** Frames per second over the last second, and the longest gap between two frames in it (a stutter shows here). */
    data class Running(val fps: Int, val worstFrameMillis: Int) : FpsReading
}

/**
 * Counts the frames the 3D view really presents (SceneView's `onFrame`, called once per frame that reached the
 * screen) and turns them into "FPS". Pure Kotlin, tested with made-up timestamps.
 *
 * SceneView 4 renders ON DEMAND by default: while nothing moves it draws NOTHING, so a counter that only divided frames
 * by time would show "0 FPS" for a perfectly healthy idle scene. Hence [FpsReading.Idle]: no frame for [IDLE_AFTER_NANOS].
 *
 * The timestamps are kept in a ring of [capacity] longs, not in a list: `onFrame` runs 60-120 times a second, and a
 * list of Long would box (allocate) every value: garbage for the collector in the hottest path of the app.
 * Not thread-safe: written and read on the main thread only (onFrame and the screen both run there).
 */
class FpsMeter(private val capacity: Int = 256) {

    private val times = LongArray(capacity)
    private var size = 0
    private var next = 0   // where the next timestamp goes

    fun onFrame(frameTimeNanos: Long) {
        // A timestamp older than the last one (should not happen) would break the gaps: ignored.
        if (size > 0 && frameTimeNanos < times[(next - 1 + capacity) % capacity]) return
        times[next] = frameTimeNanos
        next = (next + 1) % capacity
        if (size < capacity) size++
    }

    /** The reading at [nowNanos] (same clock as the frame times: System.nanoTime / Choreographer). */
    fun reading(nowNanos: Long): FpsReading {
        if (size == 0) return FpsReading.Idle
        val last = times[(next - 1 + capacity) % capacity]
        if (nowNanos - last > IDLE_AFTER_NANOS) return FpsReading.Idle

        // Walk back from the newest frame while inside the window, measuring the gaps.
        var count = 1
        var first = last
        var worstGap = 0L
        var previous = last
        var i = 1
        while (i < size) {
            val t = times[(next - 1 - i + 2 * capacity) % capacity]
            if (last - t > WINDOW_NANOS) break
            worstGap = maxOf(worstGap, previous - t)
            previous = t
            first = t
            count++
            i++
        }
        if (count < 2 || last == first) return FpsReading.Idle
        val fps = ((count - 1) * 1_000_000_000L + (last - first) / 2) / (last - first)   // rounded
        val worstMillis = ((worstGap + 999_999) / 1_000_000).toInt()                      // rounded UP: never flatters
        return FpsReading.Running(fps.toInt(), worstMillis)
    }

    companion object {
        const val WINDOW_NANOS = 1_000_000_000L
        const val IDLE_AFTER_NANOS = 500_000_000L
    }
}

/** The words of the counter (top panel of the molecule screen). */
object FpsText {
    /** "60 FPS" or "IDLE". */
    fun label(reading: FpsReading): String = when (reading) {
        FpsReading.Idle -> "IDLE"
        is FpsReading.Running -> "${reading.fps} FPS"
    }

    /** The colour level: GOOD from 55 fps (a 60 Hz panel minus jitter), POOR under 30 (the subject's "< 30 FPS"). */
    enum class Level { GOOD, FAIR, POOR, IDLE }

    fun level(reading: FpsReading): Level = when {
        reading !is FpsReading.Running -> Level.IDLE
        reading.fps >= 55 -> Level.GOOD
        reading.fps >= 30 -> Level.FAIR
        else -> Level.POOR
    }
}
