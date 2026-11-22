package dev.knowledge82.swiftyproteins.loading

/**
 * How long the loading indicator is shown (document 26). Pure, tested.
 *
 * - A ligand that has to be DOWNLOADED shows the loading dialog (the servo-skull and its sound) for at least
 *   [MIN_SHOWN_MILLIS]. On a fast network the file arrives in a few hundred milliseconds, and the dialog only
 *   flashed: the animation and its sound are part of the experience, so they get their time.
 * - A ligand that is ARCHIVED (on the phone) loads QUIETLY: no minimum, no dialog and no sound, and the screen simply
 *   changes channel. If an archived load still takes longer than [QUIET_GRACE_MILLIS] (an old copy being refreshed
 *   over a slow network), the dialog appears after all: the user is never left without feedback.
 */
object LoadingPolicy {

    /** The least time the loading dialog of a download stays on screen. */
    const val MIN_SHOWN_MILLIS = 2_000L

    /** How long a quiet (archived) load runs before its dialog appears anyway. */
    const val QUIET_GRACE_MILLIS = 600L

    /** The minimum time of a load: none for a quiet one. */
    fun minimumMillis(quiet: Boolean, minimum: Long = MIN_SHOWN_MILLIS): Long = if (quiet) 0L else minimum

    /** How much longer to wait, [elapsedMillis] after the start, to reach [minimumMillis]: never negative. */
    fun remainingMillis(minimumMillis: Long, elapsedMillis: Long): Long = (minimumMillis - elapsedMillis).coerceAtLeast(0L)
}
