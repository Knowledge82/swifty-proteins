package dev.knowledge82.swiftyproteins.prefs

import dev.knowledge82.swiftyproteins.net.LigandRepository
import java.io.File
import java.security.MessageDigest

/**
 * The favourite ligands of each ACCOUNT (bonus VII.5, document 16).
 *
 * Contract: it never throws. A file that cannot be read gives "no favourites"; a write that fails returns false and
 * the screen keeps the change in memory (it is a convenience, not data whose loss breaks anything).
 */
interface FavoritesStore {
    fun load(username: String): Set<String>
    fun save(username: String, ids: Set<String>): Boolean
}

/** For tests and previews: remembers nothing. */
object NoFavoritesStore : FavoritesStore {
    override fun load(username: String): Set<String> = emptySet()
    override fun save(username: String, ids: Set<String>): Boolean = true
}

/**
 * One small text file per account in [dir] (the app puts it in `noBackupFilesDir/favorites`, next to the accounts):
 * one ligand id per line, sorted.
 *
 * - **The file name is a HASH of the user name** (SHA-256, the first 32 hex digits), not the name itself: listing the
 *   folder does not reveal which accounts exist on the phone (the accounts file itself is encrypted, document 05).
 * - **Every id is checked when READ** with the same rule as a download ([LigandRepository.isValidId]): the file is
 *   ours, but a file on disk is still input. Anything else is dropped, and at most [MAX_FAVORITES] are kept.
 * - **Atomic write:** a temporary file, then a rename (the same pattern as the cache and the accounts).
 *
 * Pure `java.io`: tested on the JVM with a real temporary folder.
 */
class FileFavoritesStore(private val dir: File) : FavoritesStore {

    override fun load(username: String): Set<String> = try {
        val file = fileOf(username)
        if (!file.isFile) emptySet()
        else file.readLines().asSequence().map { it.trim() }.filter(LigandRepository::isValidId).take(MAX_FAVORITES).toSortedSet()
    } catch (e: Exception) {
        emptySet()
    }

    override fun save(username: String, ids: Set<String>): Boolean = try {
        dir.mkdirs()
        val clean = ids.filter(LigandRepository::isValidId).sorted().take(MAX_FAVORITES)
        val target = fileOf(username)
        val temp = File(dir, target.name + ".tmp")
        temp.writeText(clean.joinToString("\n", postfix = if (clean.isEmpty()) "" else "\n"))
        if (!temp.renameTo(target)) {
            temp.delete()
            false
        } else true
    } catch (e: Exception) {
        false
    }

    /** The file of an account: "fav_" + 32 hex digits of SHA-256(name) + ".txt". */
    internal fun fileOf(username: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(username.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it) }.take(32)
        return File(dir, "fav_$hex.txt")
    }

    companion object {
        /** More than the whole catalogue (1243): a hard ceiling against a damaged or forged file. */
        const val MAX_FAVORITES = 2_000
    }
}

/** The pure rule of the star key. */
object Favorites {
    /** The set after tapping the star of [id]: in → out, out → in. */
    fun toggle(current: Set<String>, id: String): Set<String> = if (id in current) current - id else current + id

    /** The list to show: everything, or only the favourites (keeping the catalogue's order). */
    fun filter(ids: List<String>, favorites: Set<String>, onlyFavorites: Boolean): List<String> =
        if (onlyFavorites) ids.filter { it in favorites } else ids
}
