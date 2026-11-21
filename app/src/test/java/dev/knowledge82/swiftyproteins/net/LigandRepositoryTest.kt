package dev.knowledge82.swiftyproteins.net

import dev.knowledge82.swiftyproteins.cif.CifFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The repository with a FAKE fetcher (no network at all), plus a few end-to-end tests with the real fetcher
 * against a local server.
 */
class LigandRepositoryTest {

    private val real001: String by lazy {
        javaClass.getResourceAsStream("/cif/001.cif")!!.bufferedReader().use { it.readText() }
    }

    /** A fetcher that returns what we tell it and remembers which URLs were requested. */
    private class FakeFetcher(private val answer: () -> FetchResult) : HttpFetcher {
        val urls = mutableListOf<String>()
        override fun fetch(url: String, isCancelled: () -> Boolean): FetchResult {
            urls += url
            return answer()
        }
    }

    private fun repo(answer: () -> FetchResult) = FakeFetcher(answer).let { it to LigandRepository(it, "http://base/") }

    private fun errorOf(result: LigandLoadResult): LoadError {
        assertTrue("expected a Failure but got $result", result is LigandLoadResult.Failure)
        return (result as LigandLoadResult.Failure).error
    }

    // ---------------------------------------------------------------- success and the URL

    @Test
    fun aGoodBodyBecomesAMolecule() {
        val (fetcher, repository) = repo { FetchResult.Body(real001) }
        val result = repository.load("001")
        assertTrue("got $result", result is LigandLoadResult.Success)
        val molecule = (result as LigandLoadResult.Success).molecule
        assertEquals("001", molecule.id)
        assertEquals(87, molecule.atoms.size)
        assertEquals(listOf("http://base/001.cif"), fetcher.urls)
    }

    @Test
    fun theIdIsTrimmedAndUppercasedBeforeBuildingTheUrl() {
        val (fetcher, repository) = repo { FetchResult.HttpError(404) }
        repository.load("  atp ")
        assertEquals(listOf("http://base/ATP.cif"), fetcher.urls)
    }

    @Test
    fun theDefaultBaseUrlIsTheRealOne() {
        val fetcher = FakeFetcher { FetchResult.HttpError(404) }
        LigandRepository(fetcher).load("HEM")
        assertEquals(listOf("https://files.rcsb.org/ligands/view/HEM.cif"), fetcher.urls)
    }

    // ---------------------------------------------------------------- invalid ids never reach the network

    @Test
    fun invalidIdsAreRejectedWithoutAnyRequest() {
        for (bad in listOf("", "   ", "../x", "ABCDEF", "A B", "A/B", "a?b=1", "AT\nP", "ÁTP", "AT.P")) {
            val (fetcher, repository) = repo { FetchResult.Body(real001) }
            assertEquals("for '$bad'", LoadError.InvalidId(bad), errorOf(repository.load(bad)))
            assertTrue("a request was made for '$bad'", fetcher.urls.isEmpty())
        }
    }

    @Test
    fun idsOfOneAndFiveCharactersAreAccepted() {
        for (ok in listOf("A", "ZN", "001", "ABCDE", "12345")) {
            val (fetcher, repository) = repo { FetchResult.HttpError(404) }
            repository.load(ok)
            assertEquals("http://base/$ok.cif", fetcher.urls.single())
        }
    }

    // ---------------------------------------------------------------- every FetchResult has its own LoadError

    @Test
    fun status404And410AreNotFound() {
        assertEquals(LoadError.NotFound, errorOf(repo { FetchResult.HttpError(404) }.second.load("XYZ")))
        assertEquals(LoadError.NotFound, errorOf(repo { FetchResult.HttpError(410) }.second.load("XYZ")))
    }

    @Test
    fun otherStatusesAreServerErrors() {
        assertEquals(LoadError.ServerError(500), errorOf(repo { FetchResult.HttpError(500) }.second.load("XYZ")))
        assertEquals(LoadError.ServerError(403), errorOf(repo { FetchResult.HttpError(403) }.second.load("XYZ")))
        assertEquals(LoadError.ServerError(503), errorOf(repo { FetchResult.HttpError(503) }.second.load("XYZ")))
    }

    @Test
    fun transportProblemsAreMapped() {
        assertEquals(LoadError.NoConnection, errorOf(repo { FetchResult.NoConnection("x") }.second.load("XYZ")))
        assertEquals(LoadError.Timeout, errorOf(repo { FetchResult.Timeout }.second.load("XYZ")))
        assertEquals(LoadError.Incomplete, errorOf(repo { FetchResult.Incomplete(10, 5) }.second.load("XYZ")))
        assertEquals(LoadError.TooLarge(77), errorOf(repo { FetchResult.TooLarge(77) }.second.load("XYZ")))
        assertEquals(LoadError.NetworkError("tls"), errorOf(repo { FetchResult.Failed("tls") }.second.load("XYZ")))
    }

    @Test
    fun cancellationIsNotAnError() {
        val result = repo { FetchResult.Cancelled }.second.load("XYZ")
        assertEquals(LigandLoadResult.Cancelled, result)
    }

    @Test
    fun aBodyThatIsNotCifIsCorrupt() {
        val html = "<html><body>Service unavailable</body></html>"
        assertEquals(
            LoadError.Corrupt(CifFailure.NoDataBlock),
            errorOf(repo { FetchResult.Body(html) }.second.load("XYZ")),
        )
        assertEquals(
            LoadError.Corrupt(CifFailure.NoDataBlock),
            errorOf(repo { FetchResult.Body("") }.second.load("XYZ")),
        )
    }

    // ---------------------------------------------------------------- the safety net

    @Test
    fun aFetcherThatThrowsBecomesUnexpectedInsteadOfCrashing() {
        val error = errorOf(repo { throw IllegalStateException("boom") }.second.load("XYZ"))
        assertTrue("got $error", error is LoadError.Unexpected && "boom" in error.description)
    }

    @Test
    fun theCancellationFlagIsPassedToTheFetcher() {
        val received = mutableListOf<() -> Boolean>()
        val fetcher = object : HttpFetcher {
            override fun fetch(url: String, isCancelled: () -> Boolean): FetchResult {
                received += isCancelled
                return FetchResult.Cancelled
            }
        }
        var flag = false
        LigandRepository(fetcher).load("XYZ", { flag })   // NOT a trailing lambda: that one would be onStage (document 20)
        assertEquals(false, received.single()())
        flag = true
        assertEquals(true, received.single()())
    }

    // ---------------------------------------------------------------- end to end: real fetcher + local server

    private fun endToEnd(server: TestHttpServer) =
        LigandRepository(HttpUrlConnectionFetcher(connectTimeoutMs = 2_000, readTimeoutMs = 2_000), server.baseUrl)

    @Test
    fun endToEndTheRealFileTravelsThroughHttpAndBecomesAMolecule() {
        TestHttpServer { request, s ->
            if (requestPath(request) == "/ligands/view/001.cif") s.respond(body = real001.toByteArray())
            else s.respond("404 Not Found")
        }.use { server ->
            val result = endToEnd(server).load("001")
            assertTrue("got $result", result is LigandLoadResult.Success)
            assertEquals(87, (result as LigandLoadResult.Success).molecule.atoms.size)
            assertEquals("GET /ligands/view/001.cif HTTP/1.1", server.requests.single().lineSequence().first())
        }
    }

    @Test
    fun endToEndAnUnknownLigandIs404() {
        TestHttpServer { _, s -> s.respond("404 Not Found") }.use { server ->
            assertEquals(LoadError.NotFound, errorOf(endToEnd(server).load("QQQ")))
        }
    }

    @Test
    fun endToEndAFileCutInTheMiddleIsIncompleteNotCorrupt() {
        // The server announces the full length but the connection dies half way: the typical bad mobile network.
        val half = real001.toByteArray().copyOf(real001.length / 2)
        TestHttpServer { _, s -> s.respond(body = half, lengthHeader = real001.length.toLong()) }.use { server ->
            assertEquals(LoadError.Incomplete, errorOf(endToEnd(server).load("001")))
        }
    }

    @Test
    fun endToEndAnHtmlErrorPageWithStatus200IsCorrupt() {
        TestHttpServer { _, s -> s.respond(body = "<html>Proxy login required</html>".toByteArray()) }.use { server ->
            assertEquals(LoadError.Corrupt(CifFailure.NoDataBlock), errorOf(endToEnd(server).load("001")))
        }
    }

    @Test
    fun endToEndNothingListeningIsNoConnection() {
        val dead = TestHttpServer { _, _ -> }.use { it.baseUrl }
        val repository = LigandRepository(HttpUrlConnectionFetcher(connectTimeoutMs = 2_000), dead)
        assertEquals(LoadError.NoConnection, errorOf(repository.load("001")))
    }

    @Test
    fun endToEndAServerThatNeverAnswersIsATimeout() {
        TestHttpServer { _, _ -> Thread.sleep(3_000) }.use { server ->
            val repository = LigandRepository(HttpUrlConnectionFetcher(readTimeoutMs = 300), server.baseUrl)
            assertEquals(LoadError.Timeout, errorOf(repository.load("001")))
        }
    }

    // ---------------------------------------------------------------- a documented limitation

    @Test
    fun aDownloadCutExactlyAtARowBoundaryWithConsistentLengthPassesAsAMoleculeWithFewerAtoms() {
        // If the cut falls between two atom rows AND Content-Length matches what was sent (a proxy that rewrites
        // it, or a server that generates a short file), nothing can detect it: the file is well formed.
        // This test does not defend the behaviour: it DOCUMENTS it, so nobody believes the check is perfect.
        val lines = real001.lines()
        // The atom rows start right after the last "_chem_comp_atom." header line; keep only the first 10.
        val firstAtomRow = lines.indexOfLast { it.startsWith("_chem_comp_atom.") } + 1
        val cut = lines.take(firstAtomRow + 10).joinToString("\n") + "\n"
        val body = cut.toByteArray()
        TestHttpServer { _, s -> s.respond(body = body) }.use { server ->
            val result = endToEnd(server).load("001")
            assertTrue("got $result", result is LigandLoadResult.Success)
            assertEquals(10, (result as LigandLoadResult.Success).molecule.atoms.size)
        }
    }

    @Test
    fun aDownloadCutBetweenTwoBondRowsPassesAsAMoleculeWithFewerBonds() {
        // Same limitation, in the bond table: all 87 atoms arrive, but only some of the 90 bonds.
        val lines = real001.lines()
        val firstBondRow = lines.indexOfLast { it.startsWith("_chem_comp_bond.") } + 1
        val body = (lines.take(firstBondRow + 40).joinToString("\n") + "\n").toByteArray()   // keep 40 bond rows
        TestHttpServer { _, s -> s.respond(body = body) }.use { server ->
            val result = endToEnd(server).load("001")
            assertTrue("got $result", result is LigandLoadResult.Success)
            val molecule = (result as LigandLoadResult.Success).molecule
            assertEquals(87, molecule.atoms.size)
            assertEquals(40, molecule.bonds.size)
        }
    }
}
