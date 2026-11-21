package dev.knowledge82.swiftyproteins.net

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL
import java.net.UnknownHostException

/**
 * The real [HttpFetcher], built on `java.net.HttpURLConnection`: it comes with Android and the JVM, so it
 * adds no dependency. Pure Kotlin/Java: no `android.*`, so it can be tested on the JVM against a local server.
 *
 * Three different clocks protect us from a slow server: [connectTimeoutMs] (establishing the connection),
 * [readTimeoutMs] (silence between two chunks of data) and [totalTimeoutMs] (the whole body). The third one
 * is needed because a server that sends one byte every few seconds never trips the second.
 *
 * @param open how a connection is created. The default is the normal one; the tests pass a fake.
 */
class HttpUrlConnectionFetcher(
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 10_000,
    private val totalTimeoutMs: Long = 30_000,
    private val maxBytes: Int = 2_000_000,
    private val open: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
) : HttpFetcher {

    override fun fetch(url: String, isCancelled: () -> Boolean): FetchResult = fetch(url, isCancelled) { _, _ -> }

    override fun fetch(url: String, isCancelled: () -> Boolean, onProgress: (received: Long, expected: Long) -> Unit): FetchResult {
        if (isCancelled()) return FetchResult.Cancelled
        val deadline = System.nanoTime() + totalTimeoutMs * 1_000_000

        val connection = try {
            open(URI(url).toURL())
        } catch (e: IOException) {                 // MalformedURLException is an IOException
            return FetchResult.Failed("cannot open $url: $e")
        } catch (e: IllegalArgumentException) {    // URI(...) complains about a text that is not a URL
            return FetchResult.Failed("not a valid URL: $url")
        } catch (e: java.net.URISyntaxException) {
            return FetchResult.Failed("not a valid URL: $url")
        }

        try {
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.requestMethod = "GET"
            connection.useCaches = false
            // Without this, the server may compress the body and the size we read stops matching Content-Length.
            connection.setRequestProperty("Accept-Encoding", "identity")
            connection.setRequestProperty("User-Agent", "SwiftyProteins/1.0 (42 school project)")

            // ---- phase 1: connect, send the request, read the status line and the headers -------------
            val status = try {
                connection.responseCode   // this call is what actually connects
            } catch (e: SocketTimeoutException) {
                return FetchResult.Timeout
            } catch (e: UnknownHostException) {
                return FetchResult.NoConnection("unknown host: ${e.message}")
            } catch (e: SocketException) {      // ConnectException and NoRouteToHostException are SocketExceptions
                return FetchResult.NoConnection(e.toString())
            } catch (e: IOException) {
                return FetchResult.Failed(e.toString())
            }
            if (status !in 200..299) return FetchResult.HttpError(status)

            // ---- phase 2: the body -----------------------------------------------------------------------------
            return readBody(connection, deadline, isCancelled, onProgress)
        } finally {
            connection.disconnect()
        }
    }

    private fun readBody(
        connection: HttpURLConnection,
        deadline: Long,
        isCancelled: () -> Boolean,
        onProgress: (Long, Long) -> Unit,
    ): FetchResult {
        val declared = connection.contentLengthLong          // -1 when the server does not say
        if (declared > maxBytes) return FetchResult.TooLarge(maxBytes)
        // Only trust the announced length when nothing was compressed on the way.
        val checkLength = declared >= 0 && connection.contentEncoding == null
        // The percentage is only meaningful against a length we trust; otherwise "-1": bytes without a percentage.
        val announced = if (checkLength) declared else -1L
        onProgress(0L, announced)

        val body = ByteArrayOutputStream(if (declared in 0..maxBytes) declared.toInt() else 8_192)
        val buffer = ByteArray(8_192)
        try {
            connection.inputStream.use { input ->
                while (true) {
                    if (isCancelled()) return FetchResult.Cancelled
                    if (System.nanoTime() - deadline > 0) return FetchResult.Timeout
                    val count = input.read(buffer)
                    if (count == -1) break
                    if (body.size() + count > maxBytes) return FetchResult.TooLarge(maxBytes)
                    body.write(buffer, 0, count)
                    onProgress(body.size().toLong(), announced)
                }
            }
        } catch (e: SocketTimeoutException) {
            return FetchResult.Timeout
        } catch (e: IOException) {          // the connection broke while the body was arriving
            return FetchResult.Incomplete(declared, body.size().toLong())
        }

        if (checkLength && body.size() < declared) {
            return FetchResult.Incomplete(declared, body.size().toLong())
        }
        return FetchResult.Body(String(body.toByteArray(), Charsets.UTF_8))
    }
}
