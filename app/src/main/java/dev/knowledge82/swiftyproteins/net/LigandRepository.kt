package dev.knowledge82.swiftyproteins.net

import dev.knowledge82.swiftyproteins.cif.CifParser
import dev.knowledge82.swiftyproteins.cif.ParseResult

/**
 * Downloads a ligand and turns it into a Molecule. It glues two things: the network ([HttpFetcher]) and the
 * parser ([CifParser]), and translates every outcome into a [LigandLoadResult].
 *
 * It BLOCKS the calling thread (network + parsing): call it from a background thread, never from the main one.
 * It never throws because of the network or the file.
 */
class LigandRepository(
    private val fetcher: HttpFetcher,
    private val baseUrl: String = DEFAULT_BASE_URL,
) {

    fun load(rawId: String, isCancelled: () -> Boolean = { false }): LigandLoadResult {
        val id = rawId.trim().uppercase()
        // The id goes INSIDE a URL: only letters and digits are allowed, so "../x" or "a?b" never reach the network.
        if (!ID_PATTERN.matches(id)) return failure(LoadError.InvalidId(rawId))

        return try {
            when (val fetched = fetcher.fetch("$baseUrl$id.cif", isCancelled)) {
                is FetchResult.Body -> parse(fetched.text)
                is FetchResult.HttpError -> failure(
                    if (fetched.code == 404 || fetched.code == 410) LoadError.NotFound
                    else LoadError.ServerError(fetched.code),
                )
                is FetchResult.NoConnection -> failure(LoadError.NoConnection)
                is FetchResult.Timeout -> failure(LoadError.Timeout)
                is FetchResult.Incomplete -> failure(LoadError.Incomplete)
                is FetchResult.TooLarge -> failure(LoadError.TooLarge(fetched.maxBytes))
                is FetchResult.Failed -> failure(LoadError.NetworkError(fetched.reason))
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

    companion object {
        const val DEFAULT_BASE_URL = "https://files.rcsb.org/ligands/view/"

        // Real ids are 1 to 3 letters/digits ("001", "ATP", "HEM"); the dictionary allows up to 5.
        private val ID_PATTERN = Regex("[A-Z0-9]{1,5}")
    }
}
