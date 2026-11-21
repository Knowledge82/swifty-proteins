package dev.knowledge82.swiftyproteins.net

import dev.knowledge82.swiftyproteins.cif.CifParser
import dev.knowledge82.swiftyproteins.cif.ParseResult

/**
 * Downloads a ligand and turns it into a Molecule. It glues the network ([HttpFetcher]), the parser ([CifParser]) and
 * the phone's copy of the files ([LigandCache], document 13), and translates every outcome into a [LigandLoadResult].
 *
 * The cache rules (D47):
 *  1. A copy younger than [FRESH_MILLIS] is used AT ONCE, without the network: instant, and it works offline.
 *  2. Otherwise the file is downloaded. Only a file that PARSED is saved (a "200 OK" hotel login page never is).
 *  3. If the download fails for a NETWORK reason (no connection, timeout, server error, cut), an older copy is used
 *     anyway: better a month-old ligand than none. A 404 is the server's ANSWER, not a network failure: the copy is
 *     dropped and "not found" is shown.
 *  4. A copy that no longer parses (damaged on disk) is deleted and treated as missing.
 *
 * It BLOCKS the calling thread (network + parsing + files): call it from a background thread, never from the main one.
 * It never throws because of the network or the file.
 */
class LigandRepository(
    private val fetcher: HttpFetcher,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val cache: LigandCache = NoLigandCache,
    private val now: () -> Long = System::currentTimeMillis,
) {

    fun load(rawId: String, isCancelled: () -> Boolean = { false }): LigandLoadResult {
        val id = rawId.trim().uppercase()
        // The id goes INSIDE a URL: only letters and digits are allowed, so "../x" or "a?b" never reach the network.
        if (!ID_PATTERN.matches(id)) return failure(LoadError.InvalidId(rawId))

        return try {
            val copy = cache.read(id)?.let { cached -> fromCache(id, cached) }
            if (copy != null && now() - copy.second < FRESH_MILLIS) return copy.first   // rule 1

            when (val fetched = fetcher.fetch("$baseUrl$id.cif", isCancelled)) {
                is FetchResult.Body -> parse(fetched.text).also {
                    if (it is LigandLoadResult.Success) cache.write(id, fetched.text)   // rule 2
                }
                is FetchResult.HttpError ->
                    if (fetched.code == 404 || fetched.code == 410) {
                        cache.remove(id)
                        failure(LoadError.NotFound)
                    } else {
                        offline(copy, LoadError.ServerError(fetched.code))
                    }
                is FetchResult.NoConnection -> offline(copy, LoadError.NoConnection)
                is FetchResult.Timeout -> offline(copy, LoadError.Timeout)
                is FetchResult.Incomplete -> offline(copy, LoadError.Incomplete)
                is FetchResult.TooLarge -> failure(LoadError.TooLarge(fetched.maxBytes))
                is FetchResult.Failed -> offline(copy, LoadError.NetworkError(fetched.reason))
                is FetchResult.Cancelled -> LigandLoadResult.Cancelled
            }
        } catch (e: Exception) {
            // A bug of ours, never the network (the fetcher does not throw). It becomes a value instead of a crash.
            failure(LoadError.Unexpected(e.toString()))
        }
    }

    private fun parse(text: String): LigandLoadResult = when (val parsed = CifParser.parse(text)) {
        is ParseResult.Success -> LigandLoadResult.Success(parsed.molecule)
        is ParseResult.Failure -> failure(LoadError.Corrupt(parsed.failure))
    }

    private fun failure(error: LoadError) = LigandLoadResult.Failure(error)

    /** The cached copy as a result + its date, or null (and the copy deleted) if it no longer parses (rule 4). */
    private fun fromCache(id: String, cached: CachedLigand): Pair<LigandLoadResult.Success, Long>? =
        when (val parsed = CifParser.parse(cached.text)) {
            is ParseResult.Success -> LigandLoadResult.Success(parsed.molecule, Origin.ARCHIVE) to cached.savedAtMillis
            is ParseResult.Failure -> {
                cache.remove(id)
                null
            }
        }

    /** Rule 3: the network failed; an older copy, if there is one, is better than the error. */
    private fun offline(copy: Pair<LigandLoadResult.Success, Long>?, error: LoadError): LigandLoadResult =
        copy?.first?.copy(origin = Origin.ARCHIVE_OFFLINE) ?: failure(error)

    companion object {
        const val DEFAULT_BASE_URL = "https://files.rcsb.org/ligands/view/"

        /** A copy younger than this is used without asking the server (30 days: ligand definitions rarely change). */
        const val FRESH_MILLIS = 30L * 24 * 60 * 60 * 1000

        // Real ids are 1 to 3 letters/digits ("001", "ATP", "HEM"); the dictionary allows up to 5.
        private val ID_PATTERN = Regex("[A-Z0-9]{1,5}")
    }
}
