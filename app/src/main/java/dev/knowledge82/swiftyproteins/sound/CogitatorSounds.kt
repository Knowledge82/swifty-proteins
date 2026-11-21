package dev.knowledge82.swiftyproteins.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.SystemClock
import androidx.compose.runtime.staticCompositionLocalOf
import dev.knowledge82.swiftyproteins.R
import java.util.concurrent.ConcurrentHashMap

/**
 * Plays the cues with a [SoundPool] (document 14): the Android player for SHORT sounds, decoded once into memory and
 * started with almost no delay (MediaPlayer would decode every time: too slow for a key click).
 *
 * ONE instance for the whole app ([of]), built with the APPLICATION context: it lives as long as the process, so it
 * holds no activity (no leak) and is not rebuilt on every rotation. The ten files take about 500 KB decoded.
 */
class CogitatorSounds private constructor(context: Context) : SoundPlayer {

    private val pool = SoundPool.Builder()
        .setMaxStreams(MAX_STREAMS)
        .setAudioAttributes(
            AudioAttributes.Builder()
                // "Sonification": interface sounds. Android routes them like the other UI sounds of the phone.
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val gate = SoundGate(SystemClock::elapsedRealtime)

    // Loading is ASYNCHRONOUS: the boot hum is asked for in the very first frame, usually BEFORE its file is decoded.
    // Such a cue is remembered ([waiting]) and played when its file arrives, if that happens within [LATE_MILLIS];
    // later than that it would sound out of place, so it is dropped. Nothing ever waits for a sound.
    private val ready = ConcurrentHashMap.newKeySet<Int>()
    private val waiting = ConcurrentHashMap<Int, Pair<SoundCue, Long>>()
    private val soundIds: Map<SoundCue, Int>
    private val streams = ConcurrentHashMap<SoundCue, Int>()

    init {
        pool.setOnLoadCompleteListener { _, id, status ->
            if (status != 0) return@setOnLoadCompleteListener
            ready += id
            val (cue, askedAt) = waiting.remove(id) ?: return@setOnLoadCompleteListener
            if (SystemClock.elapsedRealtime() - askedAt <= LATE_MILLIS) start(cue, id)
        }
        soundIds = SoundCue.values().associateWith { pool.load(context, rawOf(it), 1) }
    }

    override fun play(cue: SoundCue) {
        // RINGER_MODE_SILENT / VIBRATE: the user asked the phone to be quiet. Checked on every play: it can change.
        val silent = audio.ringerMode != AudioManager.RINGER_MODE_NORMAL
        if (!gate.allow(cue, silent)) return
        val id = soundIds[cue] ?: return
        if (id in ready) start(cue, id) else waiting[id] = cue to SystemClock.elapsedRealtime()
    }

    private fun start(cue: SoundCue, id: Int) {
        val stream = pool.play(id, 1f, 1f, 1, 0, 1f)
        if (stream != 0) streams[cue] = stream
    }

    override fun stop(cue: SoundCue) {
        // Also a cue still WAITING for its file: a ligand from the cache arrives in milliseconds, and its "scan" must
        // not start after the "loaded" ping.
        soundIds[cue]?.let(waiting::remove)
        streams.remove(cue)?.let(pool::stop)
    }

    companion object {
        private const val MAX_STREAMS = 4

        /** How late a cue may still start once its file is loaded (the boot hum on a cold start). */
        private const val LATE_MILLIS = 400L

        @Volatile private var instance: CogitatorSounds? = null

        fun of(context: Context): CogitatorSounds =
            instance ?: synchronized(this) {
                instance ?: CogitatorSounds(context.applicationContext).also { instance = it }
            }

        /** The file of each cue. A `when` without `else`: a new cue does not compile until it has its file. */
        private fun rawOf(cue: SoundCue): Int = when (cue) {
            SoundCue.BOOT_HUM -> R.raw.snd_boot_hum
            SoundCue.BOOT_LINE -> R.raw.snd_boot_line
            SoundCue.BOOT_DONE -> R.raw.snd_boot_done
            SoundCue.KEY -> R.raw.snd_key
            SoundCue.SIGNED_IN -> R.raw.snd_signed_in
            SoundCue.DENIED -> R.raw.snd_denied
            SoundCue.SCAN -> R.raw.snd_scan
            SoundCue.LOADED -> R.raw.snd_loaded
            SoundCue.SELECT -> R.raw.snd_select
            SoundCue.SHUTTER -> R.raw.snd_shutter
        }
    }
}

/** How the screens reach the player without passing it through every function. Silent by default (previews). */
val LocalSounds = staticCompositionLocalOf<SoundPlayer> { SoundPlayer.Silent }
