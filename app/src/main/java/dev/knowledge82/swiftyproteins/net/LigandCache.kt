package dev.knowledge82.swiftyproteins.net

import java.io.File
import java.io.IOException

/** A ligand file kept on the phone, and when it was saved (wall clock, ms since 1970). */
class CachedLigand(val text: String, val savedAtMillis: Long)

/**
 * The phone's copy of the ligand files already downloaded (document 13): the app works OFFLINE for them.
 * An interface: the repository is tested with one in memory; the app uses [FileLigandCache].
 *
 * Best effort by contract: a cache that cannot read or write must NEVER make a load fail. Implementations swallow their
 * own I/O problems ([read] returns null, [write] does nothing).
 */
interface LigandCache {
    fun read(id: String): CachedLigand?
    fun write(id: String, text: String)
    fun remove(id: String)

    /** The ids that have a copy (the "archived" mark of the list, document 21). Best effort: empty if unreadable. */
    fun ids(): Set<String> = emptySet()
}

/** No cache at all (the default of the repository, and what the old tests use). */
object NoLigandCache : LigandCache {
    override fun read(id: String): CachedLigand? = null
    override fun write(id: String, text: String) = Unit
    override fun remove(id: String) = Unit
}

/**
 * One `<ID>.cif` file per ligand in [dir] (on the phone: the app's CACHE folder, private, never backed up, and Android
 * may empty it when space runs short: right for a copy that can always be downloaded again).
 *
 * - Written ATOMICALLY (temporary file + rename): a cut in the middle never leaves half a file that looks complete.
 * - Size limit [maxBytes]: after each write the OLDEST files are deleted until the folder fits.
 * - The id was validated by the repository (only A-Z and 0-9): safe as a file name, no "../".
 *
 * Pure java.io: tested on the JVM with a temporary folder.
 */
class FileLigandCache(
    private val dir: File,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
    private val now: () -> Long = System::currentTimeMillis,
) : LigandCache {

    override fun read(id: String): CachedLigand? = try {
        val file = fileOf(id)
        if (file.isFile) CachedLigand(file.readText(Charsets.UTF_8), file.lastModified()) else null
    } catch (e: IOException) {
        null
    }

    override fun write(id: String, text: String) {
        try {
            dir.mkdirs()
            val tmp = File(dir, "$id.tmp")
            tmp.writeText(text, Charsets.UTF_8)
            tmp.setLastModified(now())
            if (!tmp.renameTo(fileOf(id))) tmp.delete()
            trim()
        } catch (e: IOException) {
            // Full disk, folder gone...: the ligand is shown anyway, it just is not kept.
        }
    }

    override fun remove(id: String) {
        fileOf(id).delete()
    }

    override fun ids(): Set<String> = try {
        dir.listFiles { f -> f.isFile && f.name.endsWith(SUFFIX) }
            ?.map { it.name.removeSuffix(SUFFIX) }
            ?.toSet() ?: emptySet()
    } catch (e: SecurityException) {
        emptySet()
    }

    /** Deletes the oldest files until the total fits in [maxBytes]. */
    private fun trim() {
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(SUFFIX) }?.toMutableList() ?: return
        var total = files.sumOf { it.length() }
        files.sortBy { it.lastModified() }   // oldest first
        for (f in files) {
            if (total <= maxBytes) break
            total -= f.length()
            f.delete()
        }
    }

    private fun fileOf(id: String) = File(dir, id + SUFFIX)

    companion object {
        /** 20 MB: a ligand file is a few kB to a few hundred kB; this keeps hundreds of them. */
        const val DEFAULT_MAX_BYTES = 20L * 1024 * 1024
        private const val SUFFIX = ".cif"
    }
}
