package dev.knowledge82.swiftyproteins.scene

/**
 * Building the 3D scene a few pieces per frame (bonus VII.4, document 20). Pure, tested.
 *
 * Why: every atom and every stick is a SceneView node, and creating one builds its mesh ON THE MAIN THREAD (Filament
 * requires it). The biggest ligand has 256 atoms and 255 bonds, drawn as about 800 nodes: built in one go, that is one
 * frozen frame long enough to see, with nothing on screen saying why. Built in batches, the screen keeps answering,
 * the molecule appears piece by piece, and "ASSEMBLY 312 / 511" says how far it is.
 *
 * The pieces are numbered: first the atoms (0 until atoms), then the bonds (atoms until atoms + bonds), each in the
 * order of the file. The batch adapts to the phone: it doubles while frames stay fast and halves when one is slow.
 */
object Assembly {

    const val FIRST_BATCH = 16
    const val MIN_BATCH = 4
    const val MAX_BATCH = 512

    /** A frame slower than this (60 Hz is 16.7 ms) means the batch was too big for this phone. */
    const val SLOW_FRAME_MILLIS = 24f

    /** A frame faster than this means there is room for more. */
    const val FAST_FRAME_MILLIS = 18f

    /** How many pieces the molecule has: its atoms, then its bonds. */
    fun total(atoms: Int, bonds: Int): Int = atoms + bonds

    /** The next batch after a frame that took [frameMillis]. */
    fun nextBatch(batch: Int, frameMillis: Float): Int = when {
        frameMillis > SLOW_FRAME_MILLIS -> (batch / 2).coerceAtLeast(MIN_BATCH)
        frameMillis < FAST_FRAME_MILLIS -> (batch * 2).coerceAtMost(MAX_BATCH)
        else -> batch
    }

    /** How many pieces exist after one more batch. */
    fun advance(built: Int, batch: Int, total: Int): Int = (built + batch).coerceAtMost(total)

    /** Atom [i] is in the scene once [built] pieces exist. */
    fun hasAtom(i: Int, built: Int): Boolean = i < built

    /** Bond [k] comes after every atom; it is in the scene once [built] pieces exist. */
    fun hasBond(k: Int, atoms: Int, built: Int): Boolean = atoms + k < built

    /** "ASSEMBLY 312 / 511", or null when the scene is complete (the line goes away). */
    fun label(built: Int, total: Int): String? = if (built >= total) null else "ASSEMBLY ${built.coerceAtLeast(0)} / $total"
}
