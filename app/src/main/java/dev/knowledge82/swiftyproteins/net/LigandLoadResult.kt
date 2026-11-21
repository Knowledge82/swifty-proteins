package dev.knowledge82.swiftyproteins.net

import dev.knowledge82.swiftyproteins.cif.CifFailure
import dev.knowledge82.swiftyproteins.molecule.Molecule

/**
 * The outcome of loading one ligand. The screen turns each case into something the user sees; none of
 * them is an exception, so none of them can crash the app.
 */
sealed interface LigandLoadResult {
    data class Success(val molecule: Molecule, val origin: Origin = Origin.NETWORK) : LigandLoadResult
    data class Failure(val error: LoadError) : LigandLoadResult

    /** The caller asked to stop. The screen shows nothing for it. */
    data object Cancelled : LigandLoadResult
}

/** Where a loaded molecule came from (shown on the molecule screen, document 13). */
enum class Origin {
    /** Downloaded just now. */
    NETWORK,

    /** The phone's copy, recent enough not to ask the server. */
    ARCHIVE,

    /** The phone's copy, used because the network failed. */
    ARCHIVE_OFFLINE,
}

/**
 * Why a ligand could not be loaded. The task asks for four user-visible errors; here is where each lives:
 * no network = [NoConnection], 404 = [NotFound], corrupt file = [Corrupt] / [Incomplete] / [TooLarge],
 * timeout = [Timeout]. The rest exist so that NOTHING is left unexplained.
 */
sealed interface LoadError {
    /** No network, unknown host, connection refused. */
    data object NoConnection : LoadError

    /** Too slow (connecting, waiting for data, or the whole download). */
    data object Timeout : LoadError

    /** The server says the ligand does not exist (404, 410). */
    data object NotFound : LoadError

    /** The server failed or refused (500, 503, 429...). */
    data class ServerError(val code: Int) : LoadError

    /** The download was cut before it ended. */
    data object Incomplete : LoadError

    /** The body is bigger than any ligand file can be. */
    data class TooLarge(val maxBytes: Int) : LoadError

    /** The body arrived whole but it is not a usable CIF file. The [failure] says exactly why. */
    data class Corrupt(val failure: CifFailure) : LoadError

    /** The id is not a plausible ligand id; no request was made. */
    data class InvalidId(val id: String) : LoadError

    /** Another network problem (TLS, malformed URL...). */
    data class NetworkError(val reason: String) : LoadError

    /** A bug of ours. Safety net; it should never appear. */
    data class Unexpected(val description: String) : LoadError
}
