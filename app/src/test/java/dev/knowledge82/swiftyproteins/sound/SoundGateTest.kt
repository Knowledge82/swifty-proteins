package dev.knowledge82.swiftyproteins.sound

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SoundGateTest {

    private var clock = 10_000L
    private val gate = SoundGate { clock }

    @Test
    fun nothingSoundsWhenThePhoneIsSilent() {
        for (cue in SoundCue.values()) assertFalse("$cue", gate.allow(cue, phoneSilent = true))
    }

    @Test
    fun theFirstTimeAlwaysSounds() {
        for (cue in SoundCue.values()) assertTrue("$cue", gate.allow(cue, phoneSilent = false))
    }

    @Test
    fun aKeyIsNotRepeatedWithinItsGap() {
        assertTrue(gate.allow(SoundCue.KEY, false))
        clock += 59
        assertFalse(gate.allow(SoundCue.KEY, false))
        clock += 1
        assertTrue(gate.allow(SoundCue.KEY, false))
    }

    @Test
    fun aRefusedPlayDoesNotRestartTheGap() {
        assertTrue(gate.allow(SoundCue.DENIED, false))
        clock += 200
        assertFalse(gate.allow(SoundCue.DENIED, false))   // too soon...
        clock += 100
        assertTrue(gate.allow(SoundCue.DENIED, false))    // ...but 300 ms after the one that SOUNDED, it sounds
    }

    @Test
    fun cuesDoNotBlockEachOther() {
        assertTrue(gate.allow(SoundCue.KEY, false))
        assertTrue(gate.allow(SoundCue.SELECT, false))
        assertTrue(gate.allow(SoundCue.DENIED, false))
    }

    @Test
    fun aSilentMomentIsNotCountedAsAPlay() {
        assertFalse(gate.allow(SoundCue.KEY, true))
        assertTrue(gate.allow(SoundCue.KEY, false))
    }

    @Test
    fun cuesThatMayRepeatFreelyHaveNoGap() {
        assertTrue(gate.allow(SoundCue.BOOT_LINE, false))
        assertTrue(gate.allow(SoundCue.BOOT_LINE, false))
    }

    @Test
    fun everyCueHasItsSoundFile() {
        // The Android player loads res/raw/snd_<cue in lower case>.ogg: a cue without its file would be mute.
        val raw = File("src/main/res/raw")
        for (cue in SoundCue.values()) {
            assertTrue("missing ${cue.name}", File(raw, "snd_${cue.name.lowercase()}.ogg").isFile)
        }
        assertEquals(SoundCue.values().size, raw.listFiles { f -> f.name.startsWith("snd_") }!!.size)
    }
}
