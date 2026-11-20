package dev.knowledge82.swiftyproteins.splash

/**
 * Decides for how long the launch screen (splash) stays on screen.
 *
 * The system shows the splash by itself while the app starts, and takes it away as soon as the first frame of the app
 * is ready, which can take 200 ms. The evaluation sheet asks for AT LEAST one second, so we ask the system to keep it
 * a little longer. This class only holds the time rule; it knows nothing about Android, so it can be tested on the JVM.
 *
 * The clock is passed in (not read here): the activity gives it `SystemClock.uptimeMillis()`, a test gives it numbers.
 */
class SplashHold(
    private val startMillis: Long,
    private val minVisibleMillis: Long = MIN_VISIBLE_MILLIS,
) {

    /** `true` while the splash must stay. It ALWAYS ends: it depends only on the elapsed time, never on loading anything. */
    fun shouldKeep(nowMillis: Long): Boolean {
        val elapsed = nowMillis - startMillis
        // A clock that goes backwards (it should not: uptime is monotonic) must not freeze the app behind the splash.
        if (elapsed < 0) return false
        return elapsed < minVisibleMillis
    }

    companion object {
        /**
         * Hold time counted from the moment our activity starts. The system splash is already visible a little BEFORE
         * (process start), so the real visible time is longer than this; 1500 leaves margin over the required 1000.
         */
        const val MIN_VISIBLE_MILLIS = 1500L
    }
}
