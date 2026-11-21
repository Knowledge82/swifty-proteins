package dev.knowledge82.swiftyproteins.sound

/**
 * Every sound of the cogitator (document 14). Each one is a file `res/raw/snd_<name>.ogg` made by
 * `tools/sound/make_sounds.py`; the Android player maps cue -> file.
 *
 * [minGapMillis]: the same cue is not repeated sooner than this. A finger drumming on a key, or a hydrogen and its
 * neighbour tapped in a row, must not turn into a machine-gun of clicks.
 */
enum class SoundCue(val minGapMillis: Long) {
    /** The CRT powering up, when the boot screen appears. */
    BOOT_HUM(0),

    /** A teletype tick for each boot line. */
    BOOT_LINE(0),

    /** The two-bell "DONE". */
    BOOT_DONE(0),

    /** A relay click: any console key or button. */
    KEY(60),

    /** "101010" (42 in binary) and a glide: signed in. */
    SIGNED_IN(0),

    /** Two low buzzes: wrong password, blocked, a load that failed. */
    DENIED(300),

    /** The servo-skull's auspex sweep while a ligand is loading. */
    SCAN(0),

    /** The auspex ping: the molecule is ready. */
    LOADED(0),

    /** A soft blip: an atom was tapped. */
    SELECT(60),

    /** The pict-capture shutter: share. */
    SHUTTER(0),
}

/** Something that plays the cues. The screens only know this; tests and previews use [Silent]. */
interface SoundPlayer {
    fun play(cue: SoundCue)

    /** Stops [cue] if it is still sounding (the loading sweep, when the molecule arrives early). */
    fun stop(cue: SoundCue)

    object Silent : SoundPlayer {
        override fun play(cue: SoundCue) = Unit
        override fun stop(cue: SoundCue) = Unit
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
