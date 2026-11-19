package dev.knowledge82.swiftyproteins.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

/** The REAL fetcher against a local server that misbehaves in every way the internet can. */
class HttpUrlConnectionFetcherTest {

    // Short limits so the timeout tests take a fraction of a second.
    private fun fetcher(
        readTimeoutMs: Int = 2_000,
        totalTimeoutMs: Long = 10_000,
        maxBytes: Int = 1_000_000,
    ) = HttpUrlConnectionFetcher(
        connectTimeoutMs = 2_000,
        readTimeoutMs = readTimeoutMs,
        totalTimeoutMs = totalTimeoutMs,
        maxBytes = maxBytes,
    )

    private fun fetch(server: TestHttpServer, f: HttpFetcher = fetcher(), isCancelled: () -> Boolean = { false }) =
        f.fetch("http://127.0.0.1:${server.port}/x.cif", isCancelled)

    // ---------------------------------------------------------------- success

    @Test
    fun aSuccessfulResponseGivesItsBodyDecodedAsUtf8() {
        TestHttpServer { _, s -> s.respond(body = "Ångström ✓".toByteArray(Charsets.UTF_8)) }.use { server ->
            assertEquals(FetchResult.Body("Ångström ✓"), fetch(server))
        }
    }

    @Test
    fun anEmptyBodyIsAnEmptyBody() {
        TestHttpServer { _, s -> s.respond(body = ByteArray(0)) }.use { server ->
            assertEquals(FetchResult.Body(""), fetch(server))
        }
    }

    @Test
    fun aChunkedResponseIsReassembled() {
        TestHttpServer { _, s -> s.respondChunked(listOf("hello ".toByteArray(), "world".toByteArray())) }.use { server ->
            assertEquals(FetchResult.Body("hello world"), fetch(server))
        }
    }

    @Test
    fun aRedirectIsFollowed() {
        TestHttpServer { request, s ->
            if (requestPath(request) == "/x.cif") {
                s.respond("302 Found", lengthHeader = 0, extraHeaders = listOf("Location: /moved.cif"))
            } else {
                s.respond(body = "arrived".toByteArray())
            }
        }.use { server ->
            assertEquals(FetchResult.Body("arrived"), fetch(server))
        }
    }

    @Test
    fun theRequestAsksForNoCompressionAndIdentifiesItself() {
        TestHttpServer { _, s -> s.respond(body = "x".toByteArray()) }.use { server ->
            fetch(server)
            val request = server.requests.single()
            assertTrue(request, request.startsWith("GET /x.cif HTTP/1.1"))
            assertTrue(request, request.contains("Accept-Encoding: identity"))
            assertTrue(request, request.contains("User-Agent: SwiftyProteins"))
        }
    }

    // ---------------------------------------------------------------- the server says no

    @Test
    fun status404IsAnHttpError() {
        TestHttpServer { _, s -> s.respond("404 Not Found", body = "nope".toByteArray()) }.use { server ->
            assertEquals(FetchResult.HttpError(404), fetch(server))
        }
    }

    @Test
    fun status500IsAnHttpError() {
        TestHttpServer { _, s -> s.respond("500 Internal Server Error") }.use { server ->
            assertEquals(FetchResult.HttpError(500), fetch(server))
        }
    }

    // ---------------------------------------------------------------- we cannot reach the server

    @Test
    fun aClosedPortIsNoConnection() {
        val port = TestHttpServer { _, _ -> }.use { it.port }   // the server is closed when use ends
        val result = fetcher().fetch("http://127.0.0.1:$port/x.cif")
        assertTrue("got $result", result is FetchResult.NoConnection)
    }

    @Test
    fun networkExceptionsAreTranslated() {
        fun withFailure(e: IOException) = HttpUrlConnectionFetcher(open = { url ->
            object : HttpURLConnection(url) {
                override fun disconnect() {}
                override fun usingProxy() = false
                override fun connect() {}
                override fun getResponseCode(): Int = throw e
            }
        }).fetch("http://example.test/x.cif")

        assertTrue(withFailure(UnknownHostException("example.test")) is FetchResult.NoConnection)
        assertTrue(withFailure(java.net.ConnectException("refused")) is FetchResult.NoConnection)
        assertTrue(withFailure(java.net.NoRouteToHostException("no route")) is FetchResult.NoConnection)
        assertEquals(FetchResult.Timeout, withFailure(SocketTimeoutException("connect timed out")))
        assertTrue(withFailure(SSLHandshakeException("bad certificate")) is FetchResult.Failed)
        assertTrue(withFailure(IOException("something else")) is FetchResult.Failed)
    }

    @Test
    fun aTextThatIsNotAUrlFailsWithoutThrowing() {
        assertTrue(fetcher().fetch("not a url at all") is FetchResult.Failed)
        assertTrue(fetcher().fetch("") is FetchResult.Failed)
    }

    // ---------------------------------------------------------------- slow servers

    @Test
    fun aServerThatNeverAnswersIsATimeout() {
        TestHttpServer { _, _ -> Thread.sleep(3_000) }.use { server ->
            val started = System.nanoTime()
            val result = fetch(server, fetcher(readTimeoutMs = 300))
            val millis = (System.nanoTime() - started) / 1_000_000
            assertEquals(FetchResult.Timeout, result)
            assertTrue("took $millis ms", millis in 250..2_500)
        }
    }

    @Test
    fun aServerThatStopsHalfWayIsATimeout() {
        TestHttpServer { _, s ->
            val out = s.getOutputStream()
            out.write("HTTP/1.1 200 OK\r\nContent-Length: 100\r\nConnection: close\r\n\r\nabc".toByteArray())
            out.flush()
            Thread.sleep(3_000)   // 3 of 100 bytes sent, then silence
        }.use { server ->
            assertEquals(FetchResult.Timeout, fetch(server, fetcher(readTimeoutMs = 300)))
        }
    }

    @Test
    fun aServerThatDripsOneByteAtATimeHitsTheTotalLimit() {
        // Every single read succeeds well within the read timeout, so ONLY the total clock can stop this.
        TestHttpServer { _, s ->
            val out = s.getOutputStream()
            out.write("HTTP/1.1 200 OK\r\nContent-Length: 100\r\nConnection: close\r\n\r\n".toByteArray())
            repeat(100) {
                out.write('a'.code)
                out.flush()
                Thread.sleep(100)
            }
        }.use { server ->
            val started = System.nanoTime()
            val result = fetch(server, fetcher(readTimeoutMs = 2_000, totalTimeoutMs = 500))
            val millis = (System.nanoTime() - started) / 1_000_000
            assertEquals(FetchResult.Timeout, result)
            assertTrue("took $millis ms", millis in 450..2_500)
        }
    }

    // ---------------------------------------------------------------- incomplete and oversized bodies

    @Test
    fun aBodyShorterThanItsContentLengthIsIncomplete() {
        TestHttpServer { _, s ->
            s.respond(body = ByteArray(100) { 'a'.code.toByte() }, lengthHeader = 1_000)   // promises 1000, sends 100
        }.use { server ->
            assertEquals(FetchResult.Incomplete(expectedBytes = 1_000, receivedBytes = 100), fetch(server))
        }
    }

    @Test
    fun aChunkedBodyThatIsNeverFinishedIsIncomplete() {
        TestHttpServer { _, s -> s.respondChunked(listOf("hello".toByteArray()), finish = false) }.use { server ->
            assertEquals(FetchResult.Incomplete(expectedBytes = -1, receivedBytes = 5), fetch(server))
        }
    }

    @Test
    fun anAnnouncedHugeBodyIsRefusedWithoutReadingIt() {
        TestHttpServer { _, s -> s.respond(body = ByteArray(0), lengthHeader = 10_000_000) }.use { server ->
            assertEquals(FetchResult.TooLarge(1_000), fetch(server, fetcher(maxBytes = 1_000)))
        }
    }

    @Test
    fun anUnannouncedHugeBodyIsCutWhenItPassesTheLimit() {
        TestHttpServer { _, s -> s.respondChunked(listOf(ByteArray(2_000) { 'a'.code.toByte() })) }.use { server ->
            assertEquals(FetchResult.TooLarge(1_000), fetch(server, fetcher(maxBytes = 1_000)))
        }
    }

    /**
     * A connection that answers with exactly what a test says, with no network and no JDK in between.
     * The JDK decides by itself how a body that ends too early looks (a clean end, or an IOException), so the tests
     * about WHAT OUR CODE DOES with the announced length use this one and do not depend on that.
     */
    private fun fakeConnection(announcedLength: Long, encoding: String?, body: ByteArray) =
        HttpUrlConnectionFetcher(open = { url ->
            object : HttpURLConnection(url) {
                override fun disconnect() {}
                override fun usingProxy() = false
                override fun connect() {}
                override fun getResponseCode() = 200
                override fun getContentLengthLong() = announcedLength
                override fun getContentEncoding() = encoding
                override fun getInputStream() = java.io.ByteArrayInputStream(body)
            }
        }).fetch("http://example.test/x.cif")

    @Test
    fun aBodyShorterThanAnnouncedIsIncompleteEvenWhenTheStreamEndsCleanly() {
        val result = fakeConnection(announcedLength = 1_000, encoding = null, body = ByteArray(100) { 'a'.code.toByte() })
        assertEquals(FetchResult.Incomplete(expectedBytes = 1_000, receivedBytes = 100), result)
    }

    @Test
    fun whenTheServerCompressesAnyway_theLengthIsNotComparedBecauseItCountsCompressedBytes() {
        // Content-Length would describe the COMPRESSED bytes, which are not the ones we read (on Android the
        // library may decompress by itself). So with Content-Encoding present the comparison is skipped.
        val result = fakeConnection(announcedLength = 1_000, encoding = "gzip", body = ByteArray(100) { 'a'.code.toByte() })
        assertTrue("got $result", result is FetchResult.Body)
    }

    @Test
    fun aBodyExactlyAtTheLimitIsAccepted() {
        TestHttpServer { _, s -> s.respond(body = ByteArray(1_000) { 'a'.code.toByte() }) }.use { server ->
            val result = fetch(server, fetcher(maxBytes = 1_000))
            assertTrue("got $result", result is FetchResult.Body && result.text.length == 1_000)
        }
    }

    // ---------------------------------------------------------------- cancellation

    @Test
    fun cancellingBeforeStartingNeverTouchesTheServer() {
        TestHttpServer { _, s -> s.respond(body = "x".toByteArray()) }.use { server ->
            assertEquals(FetchResult.Cancelled, fetch(server, isCancelled = { true }))
            assertTrue(server.requests.isEmpty())
        }
    }

    @Test
    fun cancellingWhileTheBodyArrivesStopsTheDownload() {
        TestHttpServer { _, s ->
            val out = s.getOutputStream()
            out.write("HTTP/1.1 200 OK\r\nContent-Length: 100\r\nConnection: close\r\n\r\n".toByteArray())
            repeat(100) {
                out.write('a'.code)
                out.flush()
                Thread.sleep(50)
            }
        }.use { server ->
            var asked = 0
            val started = System.nanoTime()
            val result = fetch(server, isCancelled = { ++asked > 3 })
            val millis = (System.nanoTime() - started) / 1_000_000
            assertEquals(FetchResult.Cancelled, result)
            assertTrue("took $millis ms, should be far below the 5 s the full body needs", millis < 2_000)
        }
    }
}
