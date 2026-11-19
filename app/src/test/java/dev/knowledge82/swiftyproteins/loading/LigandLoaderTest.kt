package dev.knowledge82.swiftyproteins.loading

import dev.knowledge82.swiftyproteins.cif.CifFailure
import dev.knowledge82.swiftyproteins.molecule.Atom
import dev.knowledge82.swiftyproteins.molecule.Molecule
import dev.knowledge82.swiftyproteins.net.LigandLoadResult
import dev.knowledge82.swiftyproteins.net.LoadError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The loader with a FAKE source (no network). The test creates two worlds like the real app has:
 * a single "main" thread where load/cancel are called and the state is published, and a pool of worker threads
 * where the (slow) source runs.
 */
class LigandLoaderTest {

    private val mainThread = Executors.newSingleThreadExecutor { Thread(it, "fake-main").apply { isDaemon = true } }
    private val workPool = Executors.newCachedThreadPool { Thread(it, "fake-work").apply { isDaemon = true } }
    private val scope = CoroutineScope(SupervisorJob() + mainThread.asCoroutineDispatcher())

    @After
    fun tearDown() {
        scope.cancel()
        mainThread.shutdownNow()
        workPool.shutdownNow()
    }

    private fun loader(source: LigandSource) = LigandLoader(source, scope, workPool.asCoroutineDispatcher())

    private fun molecule(id: String) = Molecule(id, listOf(Atom("C1", "C", 0f, 0f, 0f)), emptyList())

    /** Runs [block] on the fake main thread and waits for it, like a tap handler would. */
    private fun <T> onMain(block: () -> T): T = mainThread.submit<T> { block() }.get(5, TimeUnit.SECONDS)

    /** Waits (at most 5 s) until the state satisfies [condition] and returns it. Fails with a timeout if it never does. */
    private fun awaitState(loader: LigandLoader, condition: (LoadState) -> Boolean): LoadState =
        runBlocking { withTimeout(5_000) { loader.state.first(condition) } }

    /** Gives a job that was cancelled time to (wrongly) publish its late result, then drains the main thread. */
    private fun letLateResultsArrive() {
        Thread.sleep(150)
        onMain { }
    }

    // ---------------------------------------------------------------- the normal path

    @Test
    fun startsIdle() {
        assertEquals(LoadState.Idle, loader { _, _ -> error("never called") }.state.value)
    }

    @Test
    fun showsLoadingWhileTheWorkRunsAndThenTheMolecule() {
        val release = CountDownLatch(1)
        val loader = loader { id, _ ->
            release.await(5, TimeUnit.SECONDS)
            LigandLoadResult.Success(molecule(id))
        }

        onMain { loader.load("ATP") }
        assertEquals(LoadState.Loading("ATP"), loader.state.value)   // synchronously, before any work finishes

        release.countDown()
        assertEquals(LoadState.Loaded(molecule("ATP")), awaitState(loader) { it !is LoadState.Loading })
    }

    @Test
    fun theSourceReceivesTheId() {
        val received = mutableListOf<String>()
        val loader = loader { id, _ -> received += id; LigandLoadResult.Success(molecule(id)) }
        onMain { loader.load("HEM") }
        awaitState(loader) { it is LoadState.Loaded }
        assertEquals(listOf("HEM"), received)
    }

    // ---------------------------------------------------------------- the guarantee: Loading always ends

    @Test
    fun everyKindOfResultLeavesTheLoadingState() {
        val cases = listOf(
            LigandLoadResult.Success(molecule("X")) to LoadState.Loaded(molecule("X")),
            LigandLoadResult.Failure(LoadError.NoConnection) to LoadState.Failed("X", LoadError.NoConnection),
            LigandLoadResult.Failure(LoadError.Timeout) to LoadState.Failed("X", LoadError.Timeout),
            LigandLoadResult.Failure(LoadError.NotFound) to LoadState.Failed("X", LoadError.NotFound),
            LigandLoadResult.Failure(LoadError.ServerError(500)) to LoadState.Failed("X", LoadError.ServerError(500)),
            LigandLoadResult.Failure(LoadError.Incomplete) to LoadState.Failed("X", LoadError.Incomplete),
            LigandLoadResult.Failure(LoadError.TooLarge(10)) to LoadState.Failed("X", LoadError.TooLarge(10)),
            LigandLoadResult.Failure(LoadError.Corrupt(CifFailure.NoDataBlock)) to
                LoadState.Failed("X", LoadError.Corrupt(CifFailure.NoDataBlock)),
            LigandLoadResult.Failure(LoadError.InvalidId("X")) to LoadState.Failed("X", LoadError.InvalidId("X")),
            LigandLoadResult.Failure(LoadError.NetworkError("tls")) to LoadState.Failed("X", LoadError.NetworkError("tls")),
            LigandLoadResult.Failure(LoadError.Unexpected("bug")) to LoadState.Failed("X", LoadError.Unexpected("bug")),
            LigandLoadResult.Cancelled to LoadState.Idle,
        )
        for ((result, expected) in cases) {
            val loader = loader { _, _ -> result }
            onMain { loader.load("X") }
            assertEquals("for $result", expected, awaitState(loader) { it !is LoadState.Loading })
        }
    }

    @Test
    fun aSourceThatThrowsEndsAsAnUnexpectedFailureNotAsAnEternalSpinner() {
        val loader = loader { _, _ -> throw IllegalStateException("boom") }
        onMain { loader.load("X") }
        val state = awaitState(loader) { it !is LoadState.Loading }
        assertTrue("got $state", state is LoadState.Failed && state.error is LoadError.Unexpected)
        assertTrue((((state as LoadState.Failed).error) as LoadError.Unexpected).description.contains("boom"))
    }

    @Test
    fun afterAFailureTheSameLoaderCanTryAgain() {
        var attempt = 0
        val loader = loader { id, _ ->
            if (++attempt == 1) LigandLoadResult.Failure(LoadError.NoConnection) else LigandLoadResult.Success(molecule(id))
        }
        onMain { loader.load("X") }
        assertEquals(LoadState.Failed("X", LoadError.NoConnection), awaitState(loader) { it !is LoadState.Loading })

        onMain { loader.load("X") }
        assertEquals(LoadState.Loaded(molecule("X")), awaitState(loader) { it !is LoadState.Loading })
    }

    // ---------------------------------------------------------------- one load at a time

    @Test
    fun aNewLoadReplacesTheRunningOneAndTheOldResultNeverShowsUp() {
        val startedA = CountDownLatch(1)
        val releaseA = CountDownLatch(1)
        val finishedA = CountDownLatch(1)
        val sawCancelled = AtomicBoolean(false)
        val loader = loader { id, isCancelled ->
            if (id == "A") {
                startedA.countDown()
                releaseA.await(5, TimeUnit.SECONDS)
                sawCancelled.set(isCancelled())
                finishedA.countDown()
            }
            LigandLoadResult.Success(molecule(id))
        }

        onMain { loader.load("A") }
        assertTrue(startedA.await(5, TimeUnit.SECONDS))   // A must really be running, not just queued
        onMain { loader.load("B") }
        assertEquals(LoadState.Loaded(molecule("B")), awaitState(loader) { it is LoadState.Loaded })

        releaseA.countDown()                       // A finishes now, late
        assertTrue(finishedA.await(5, TimeUnit.SECONDS))
        letLateResultsArrive()

        assertTrue("A was never told to stop", sawCancelled.get())
        assertEquals(LoadState.Loaded(molecule("B")), loader.state.value)
    }

    // ---------------------------------------------------------------- cancelling

    @Test
    fun cancelReturnsToIdleAndTheLateResultIsIgnored() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val loader = loader { id, _ ->
            started.countDown()
            release.await(5, TimeUnit.SECONDS)
            finished.countDown()
            LigandLoadResult.Success(molecule(id))
        }
        onMain { loader.load("A") }
        assertEquals(LoadState.Loading("A"), loader.state.value)
        assertTrue(started.await(5, TimeUnit.SECONDS))    // the work is really running when we cancel

        onMain { loader.cancel() }
        assertEquals(LoadState.Idle, loader.state.value)

        release.countDown()
        assertTrue(finished.await(5, TimeUnit.SECONDS))
        letLateResultsArrive()
        assertEquals(LoadState.Idle, loader.state.value)
    }

    @Test
    fun cancelTellsTheSourceToStopSoTheNetworkWorkEnds() {
        val started = CountDownLatch(1)
        val stopped = CountDownLatch(1)
        val loader = loader { _, isCancelled ->
            started.countDown()
            while (!isCancelled()) Thread.sleep(5)    // like the read loop of the downloader
            stopped.countDown()
            LigandLoadResult.Cancelled
        }
        onMain { loader.load("A") }
        assertTrue(started.await(5, TimeUnit.SECONDS))

        onMain { loader.cancel() }

        assertTrue("the source never saw isCancelled() == true", stopped.await(5, TimeUnit.SECONDS))
        assertEquals(LoadState.Idle, loader.state.value)
    }

    @Test
    fun cancelWhenNothingIsRunningDoesNothing() {
        val loader = loader { _, _ -> error("never called") }
        onMain { loader.cancel() }
        assertEquals(LoadState.Idle, loader.state.value)
    }
}
