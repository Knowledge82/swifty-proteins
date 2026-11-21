package dev.knowledge82.swiftyproteins.sound

/**
 * Every sound of the cogitator (document 14). Each one is a file `res/raw/snd_<name>.ogg` made by
 * `tools/sound/make_sounds.py`; the Android player maps cue -> file.
 *
 * [minGapMillis]: the same cue is not repeated sooner than this. A finger drumming on a key, or a hydrogen and its
 * neighbour tapped in a row, must not turn into a machine-gun of clicks.
 */
enum class SoundCue(val minGapMillis: Long) {
    /** A relay, the organ's wind and a D minor chord, when the boot screen appears. The only long sound. */
    BOOT_HUM(0),

    /** A typebar striking: one dull "thock" for each boot line. */
    BOOT_LINE(0),

    /** A cathedral bell: "DONE". */
    BOOT_DONE(0),

    /** A heavy mechanical key, "thock": any console key or button. */
    KEY(60),

    /** An open fifth on the organ: signed in. */
    SIGNED_IN(0),

    /** Two dissonant organ stabs (tritones), falling: wrong password, blocked, a load that failed. */
    DENIED(300),

    /** A dial-up modem connecting (it dials 4, 2), while a ligand is loading; cut when it arrives. */
    SCAN(0),

    /** A latch and a ping: the molecule is ready. */
    LOADED(0),

    /** A small dull metal "tock": an atom was tapped. */
    SELECT(60),

    /** A reflex camera: mirror and shutter, two dry clicks. Share. */
    SHUTTER(0),
}

/** Something that plays the cues. The screens only know this; tests and previews use [Silent]. */
interface SoundPlayer {
    fun play(cue: SoundCue)

    /** Stops [cue] if it is still sounding (the loading sweep, when the molecule arrives early). */
    fun stop(cue: SoundCue)

    /** The "Sounds" setting (document 16): muted, nothing plays. */
    fun setMuted(muted: Boolean)

    object Silent : SoundPlayer {
        override fun play(cue: SoundCue) = Unit
        override fun stop(cue: SoundCue) = Unit
        override fun setMuted(muted: Boolean) = Unit
    }
}

/**
 * Decides whether a cue may sound NOW (document 14, D48). Pure Kotlin, tested with a fake clock.
 *
 * 1. Never when the phone is in SILENT or VIBRATE mode: a sound effect is not something to impose (and the subject
 *    is evaluated in a room full of people).
 * 2. Never the same cue twice within its [SoundCue.minGapMillis].
 *
 * @param now a monotonic clock in ms (SystemClock.elapsedRealtime on the phone)
 */
class SoundGate(private val now: () -> Long) {

    private val lastPlayed = LongArray(SoundCue.values().size) { NEVER }

    fun allow(cue: SoundCue, phoneSilent: Boolean): Boolean {
        if (phoneSilent) return false
        val t = now()
        val last = lastPlayed[cue.ordinal]
        if (last != NEVER && t - last < cue.minGapMillis) return false
        lastPlayed[cue.ordinal] = t
        return true
    }

    private companion object {
        const val NEVER = Long.MIN_VALUE
    }
}
