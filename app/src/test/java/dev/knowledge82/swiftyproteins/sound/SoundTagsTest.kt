package dev.knowledge82.swiftyproteins.sound

import dev.knowledge82.swiftyproteins.cif.CifFailure
import dev.knowledge82.swiftyproteins.loading.LoadState
import dev.knowledge82.swiftyproteins.molecule.Atom
import dev.knowledge82.swiftyproteins.molecule.Molecule
import dev.knowledge82.swiftyproteins.net.LoadError
import dev.knowledge82.swiftyproteins.net.Origin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundTagsTest {

    private fun molecule(id: String, atoms: Int = 1) =
        Molecule(id, List(atoms) { Atom("C$it", "C", it.toFloat(), 0f, 0f) }, emptyList())

    @Test
    fun theSameStateGivesTheSameTag() {
        // What a rotation looks like: the ViewModel hands back the SAME state; nothing must sound again.
        val loaded = LoadState.Loaded(molecule("ATP"))
        assertEquals(loadSoundTag(loaded), loadSoundTag(LoadState.Loaded(molecule("ATP"))))
        assertEquals(loadSoundTag(LoadState.Loading("ATP")), loadSoundTag(LoadState.Loading("ATP")))
        // A quiet (archived) load is a different tag: it plays no modem, and a later loud one must still play it.
        assertTrue(loadSoundTag(LoadState.Loading("ATP", quiet = true)) != loadSoundTag(LoadState.Loading("ATP")))
    }

    @Test
    fun everyStepOfALoadIsANewTag() {
        val steps = listOf(
            LoadState.Idle,
            LoadState.Loading("ATP"),
            LoadState.Loaded(molecule("ATP")),
            LoadState.Loading("HEM"),
            LoadState.Failed("HEM", LoadError.NoConnection),
            LoadState.Failed("HEM", LoadError.Timeout),
            LoadState.Failed("ZZZ", LoadError.Timeout),
        )
        val tags = steps.map(::loadSoundTag)
        assertEquals(tags.size, tags.toSet().size)
    }

    @Test
    fun anotherMoleculeIsANewTag() {
        assertNotEquals(loadSoundTag(LoadState.Loaded(molecule("ATP"))), loadSoundTag(LoadState.Loaded(molecule("HEM"))))
    }

    @Test
    fun whereTheMoleculeCameFromDoesNotMatter() {
        // Same molecule, same ping: the origin is shown on screen, it is not a reason to sound twice.
        assertEquals(
            loadSoundTag(LoadState.Loaded(molecule("ATP"), Origin.NETWORK)),
            loadSoundTag(LoadState.Loaded(molecule("ATP"), Origin.ARCHIVE)),
        )
    }

    @Test
    fun theTagStaysShortWhateverTheMoleculeSize() {
        // It goes into the saved state of the activity: a toString() of a 256-atom molecule would be kilobytes.
        assertTrue(loadSoundTag(LoadState.Loaded(molecule("ATP", atoms = 256))).length < 20)
        assertTrue(loadSoundTag(LoadState.Failed("ATP", LoadError.Corrupt(CifFailure.NoDataBlock))).length < 40)
    }
}
