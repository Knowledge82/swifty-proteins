package dev.knowledge82.swiftyproteins.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The repository's cache rules (document 13, D47), with a fake network and a cache in memory. */
class LigandRepositoryCacheTest {

    private val real001: String by lazy {
        javaClass.getResourceAsStream("/cif/001.cif")!!.bufferedReader().use { it.readText() }
    }

    private class MemoryCache : LigandCache {
        val files = mutableMapOf<String, CachedLigand>()
        var writes = 0
        override fun read(id: String) = files[id]
        override fun write(id: String, text: String) { writes++; files[id] = CachedLigand(text, savedAt) }
        override fun remove(id: String) { files.remove(id) }
        var savedAt = 0L
    }

    private var clock = 1_700_000_000_000L
    private val day = 24L * 60 * 60 * 1000
    private val cache = MemoryCache().apply { savedAt = clock }
    private var calls = 0
    private var answer: FetchResult = FetchResult.NoConnection("airplane mode")

    private val repository by lazy {
        LigandRepository(object : HttpFetcher {
            override fun fetch(url: String, isCancelled: () -> Boolean): FetchResult { calls++; return answer }
        }, "http://base/", cache) { clock }
    }

    private fun originOf(r: LigandLoadResult): Origin {
        assertTrue("got $r", r is LigandLoadResult.Success)
        return (r as LigandLoadResult.Success).origin
    }

    @Test
    fun aDownloadedLigandIsKept() {
        answer = FetchResult.Body(real001)
        assertEquals(Origin.NETWORK, originOf(repository.load("001")))
        assertEquals(real001, cache.files["001"]!!.text)
    }

    @Test
    fun aRecentCopyIsUsedWithoutTheNetwork() {
        cache.files["001"] = CachedLigand(real001, clock - 29 * day)
        assertEquals(Origin.ARCHIVE, originOf(repository.load("001")))
        assertEquals(0, calls)
    }

    @Test
    fun theOfflineDemo_downloadOnceThenAirplaneMode() {
        answer = FetchResult.Body(real001)
        repository.load("001")
        answer = FetchResult.NoConnection("airplane mode")
        val again = repository.load("001")
        assertEquals(Origin.ARCHIVE, originOf(again))
        assertEquals(87, (again as LigandLoadResult.Success).molecule.atoms.size)
    }

    @Test
    fun anOldCopyIsRefreshedWhenTheNetworkWorks() {
        cache.files["001"] = CachedLigand(real001, clock - 31 * day)
        answer = FetchResult.Body(real001)
        assertEquals(Origin.NETWORK, originOf(repository.load("001")))
        assertEquals(1, calls)
        assertEquals(clock, cache.files["001"]!!.savedAtMillis.also { cache.savedAt = clock })
    }

    @Test
    fun anOldCopyIsBetterThanANetworkError() {
        for (failure in listOf(
            FetchResult.NoConnection("x"), FetchResult.Timeout, FetchResult.HttpError(503),
            FetchResult.Incomplete(100, 10), FetchResult.Failed("tls"),
        )) {
            cache.files["001"] = CachedLigand(real001, clock - 90 * day)
            answer = failure
            assertEquals("$failure", Origin.ARCHIVE_OFFLINE, originOf(repository.load("001")))
        }
    }

    @Test
    fun aNotFoundIsTheServersAnswerAndDropsTheCopy() {
        cache.files["001"] = CachedLigand(real001, clock - 90 * day)
        answer = FetchResult.HttpError(404)
        val r = repository.load("001")
        assertEquals(LigandLoadResult.Failure(LoadError.NotFound), r)
        assertNull(cache.files["001"])
    }

    @Test
    fun aFileThatDoesNotParseIsNeverKept() {
        answer = FetchResult.Body("<html>hotel wifi login</html>")
        assertTrue(repository.load("001") is LigandLoadResult.Failure)
        assertEquals(0, cache.writes)
    }

    @Test
    fun aDamagedCopyIsDeletedAndTheNetworkAsked() {
        cache.files["001"] = CachedLigand("garbage", clock)
        answer = FetchResult.Body(real001)
        assertEquals(Origin.NETWORK, originOf(repository.load("001")))
        assertEquals(1, calls)
        assertEquals(real001, cache.files["001"]!!.text)
    }

    @Test
    fun withoutACopyTheErrorIsTheUsualOne() {
        answer = FetchResult.Timeout
        assertEquals(LigandLoadResult.Failure(LoadError.Timeout), repository.load("001"))
    }

    @Test
    fun anInvalidIdNeverTouchesTheCache() {
        assertEquals(LigandLoadResult.Failure(LoadError.InvalidId("../x")), repository.load("../x"))
        assertEquals(0, calls)
    }

    @Test
    fun aDamagedCopyIsDeletedEvenWhenOffline() {
        cache.files["001"] = CachedLigand("garbage", clock)
        answer = FetchResult.NoConnection("airplane mode")
        assertEquals(LigandLoadResult.Failure(LoadError.NoConnection), repository.load("001"))
        assertNull(cache.files["001"])
    }
}
