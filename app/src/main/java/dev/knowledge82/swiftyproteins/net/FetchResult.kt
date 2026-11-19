package dev.knowledge82.swiftyproteins.net

/**
 * What happened when we asked a server for a file. It describes the TRANSPORT only (did the bytes arrive?);
 * it knows nothing about CIF. Nothing in here is an exception: every outcome is a value.
 */
sealed interface FetchResult {

    /** Status 2xx and the whole body arrived, decoded as UTF-8. */
    data class Body(val text: String) : FetchResult

    /** The server answered, but not with success (404, 500...). */
    data class HttpError(val code: Int) : FetchResult

    /** We could not reach the server: no network, unknown host, connection refused or unreachable. */
    data class NoConnection(val reason: String) : FetchResult

    /** The server or the network was too slow: connecting, waiting for data, or the total time limit. */
    data object Timeout : FetchResult

    /**
     * The connection ended before the whole body arrived.
     * @param expectedBytes what the server announced in `Content-Length`, or -1 if it did not.
     */
    data class Incomplete(val expectedBytes: Long, val receivedBytes: Long) : FetchResult

    /** The body is bigger than we accept; we stopped reading. */
    data class TooLarge(val maxBytes: Int) : FetchResult

    /** The caller asked to stop (the user left the screen). Not an error. */
    data object Cancelled : FetchResult

    /** Any other network problem (a TLS failure, a malformed URL...). */
    data class Failed(val reason: String) : FetchResult
}

/**
 * Something that can download a URL. It is an interface so the code that USES it (the repository) can be
 * tested with a fake, and the real implementation can be tested against a local server.
 */
interface HttpFetcher {

    /**
     * Downloads [url] with a GET. BLOCKS the calling thread, so it must not run on the main thread.
     * It never throws because of the network: every outcome is a [FetchResult].
     *
     * @param isCancelled asked repeatedly while the body is being read; when it answers true the download stops.
     */
    fun fetch(url: String, isCancelled: () -> Boolean = { false }): FetchResult
}
