package dev.knowledge82.swiftyproteins.loading

import dev.knowledge82.swiftyproteins.cif.CifFailure
import dev.knowledge82.swiftyproteins.molecule.Atom
import dev.knowledge82.swiftyproteins.molecule.Molecule
import dev.knowledge82.swiftyproteins.net.LigandLoadResult
import dev.knowledge82.swiftyproteins.net.LoadError
import dev.knowledge82.swiftyproteins.net.Origin
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
import java.util.concurrent.atomic.AtomicReference

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
        assertEquals(LoadState.Idle, loader { _, _, _ -> error("never called") }.state.value)
    }

    @Test
    fun showsLoadingWhileTheWorkRunsAndThenTheMolecule() {
        val release = CountDownLatch(1)
        val loader = loader { id, _, _ ->
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
        val loader = loader { id, _, _ -> received += id; LigandLoadResult.Success(molecule(id)) }
        onMain { loader.load("HEM") }
        awaitState(loader) { it is LoadState.Loaded }
        assertEquals(listOf("HEM"), received)
    }

    // ---------------------------------------------------------------- the guarantee: Loading always ends

    @Test
    fun everyKindOfResultLeavesTheLoadingState() {
        val cases = listOf(
            LigandLoadResult.Success(molecule("X")) to LoadState.Loaded(molecule("X")),
            // Where the molecule came from travels to the screen (the tab shows it, document 13).
            LigandLoadResult.Success(molecule("X"), Origin.ARCHIVE_OFFLINE) to LoadState.Loaded(molecule("X"), Origin.ARCHIVE_OFFLINE),
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
            val loader = loader { _, _, _ -> result }
            onMain { loader.load("X") }
            assertEquals("for $result", expected, awaitState(loader) { it !is LoadState.Loading })
        }
    }

    @Test
    fun aSourceThatThrowsEndsAsAnUnexpectedFailureNotAsAnEternalSpinner() {
        val loader = loader { _, _, _ -> throw IllegalStateException("boom") }
        onMain { loader.load("X") }
        val state = awaitState(loader) { it !is LoadState.Loading }
        assertTrue("got $state", state is LoadState.Failed && state.error is LoadError.Unexpected)
        assertTrue((((state as LoadState.Failed).error) as LoadError.Unexpected).description.contains("boom"))
    }

    @Test
    fun afterAFailureTheSameLoaderCanTryAgain() {
        var attempt = 0
        val loader = loader { id, _, _ ->
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
        val loader = loader { id, isCancelled, _ ->
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
        val loader = loader { id, _, _ ->
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
        val loader = loader { _, isCancelled, _ ->
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
        val loader = loader { _, _, _ -> error("never called") }
        onMain { loader.cancel() }
        assertEquals(LoadState.Idle, loader.state.value)
    }

    // ---------------------------------------------------------------- stages (document 20)

    @Test
    fun theStageShowsWhileLoadingAndTheResultReplacesIt() {
        val release = CountDownLatch(1)
        val loader = loader { id, _, onStage ->
            onStage(LoadStage.Connecting)
            onStage(LoadStage.Downloading(10, 100))
            release.await(5, TimeUnit.SECONDS)
            LigandLoadResult.Success(molecule(id))
        }
        onMain { loader.load("ATP") }
        assertEquals(LoadState.Loading("ATP", LoadStage.Downloading(10, 100)), awaitState(loader) { (it as? LoadState.Loading)?.stage is LoadStage.Downloading })
        release.countDown()
        assertEquals(LoadState.Loaded(molecule("ATP")), awaitState(loader) { it !is LoadState.Loading })
    }

    @Test
    fun aLateStageOfACancelledLoadNeverBringsLoadingBack() {
        val started = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        val reported = CountDownLatch(1)
        val loader = loader { _, _, onStage ->
            started.countDown()
            cancelled.await(5, TimeUnit.SECONDS)
            onStage(LoadStage.Parsing)   // too late: the user already left
            reported.countDown()
            LigandLoadResult.Cancelled
        }
        onMain { loader.load("A") }
        assertTrue(started.await(5, TimeUnit.SECONDS))
        onMain { loader.cancel() }
        cancelled.countDown()
        assertTrue(reported.await(5, TimeUnit.SECONDS))
        letLateResultsArrive()
        assertEquals(LoadState.Idle, loader.state.value)
    }

    @Test
    fun aLateStageOfTheOldLoadNeverOverwritesTheNewOne() {
        val startedA = CountDownLatch(1)
        val replaced = CountDownLatch(1)
        val reportedA = CountDownLatch(1)
        val releaseB = CountDownLatch(1)
        val loader = loader { id, _, onStage ->
            if (id == "A") {
                startedA.countDown()
                replaced.await(5, TimeUnit.SECONDS)
                onStage(LoadStage.Downloading(1, 2))   // A's stage, after B took over
                reportedA.countDown()
            } else {
                onStage(LoadStage.Parsing)
                releaseB.await(5, TimeUnit.SECONDS)
            }
            LigandLoadResult.Success(molecule(id))
        }
        onMain { loader.load("A") }
        assertTrue(startedA.await(5, TimeUnit.SECONDS))
        onMain { loader.load("B") }
        awaitState(loader) { it == LoadState.Loading("B", LoadStage.Parsing) }
        replaced.countDown()
        assertTrue(reportedA.await(5, TimeUnit.SECONDS))
        letLateResultsArrive()
        assertEquals(LoadState.Loading("B", LoadStage.Parsing), loader.state.value)
        releaseB.countDown()
        assertEquals(LoadState.Loaded(molecule("B")), awaitState(loader) { it is LoadState.Loaded })
    }

    @Test
    fun aStageArrivingAfterTheResultIsIgnored() {
        val report = AtomicReference<(LoadStage) -> Unit>()   // set on the work thread, used on this one
        val loader = loader { id, _, onStage -> report.set(onStage); LigandLoadResult.Success(molecule(id)) }
        onMain { loader.load("ATP") }
        assertEquals(LoadState.Loaded(molecule("ATP")), awaitState(loader) { it is LoadState.Loaded })
        report.get()(LoadStage.Parsing)   // a straggler from the work thread
        letLateResultsArrive()
        assertEquals(LoadState.Loaded(molecule("ATP")), loader.state.value)
    }

    // ---------------------------------------------------------------- the minimum time of the dialog (document 26)

    private fun slowLoader(minimum: Long, source: LigandSource) = LigandLoader(source, scope, workPool.asCoroutineDispatcher(), minimum)

    @Test
    fun aDownloadStaysLoadingForTheMinimumEvenWhenTheFileIsInstant() {
        val loader = slowLoader(400L) { id, _, _ -> LigandLoadResult.Success(molecule(id)) }
        val start = System.nanoTime()
        onMain { loader.load("ATP") }
        Thread.sleep(200)
        assertEquals(LoadState.Loading("ATP"), loader.state.value)   // the file was there at once; the dialog stays
        assertEquals(LoadState.Loaded(molecule("ATP")), awaitState(loader) { it !is LoadState.Loading })
        assertTrue("waited ${(System.nanoTime() - start) / 1_000_000} ms", System.nanoTime() - start >= 380_000_000L)
    }

    @Test
    fun aQuietLoadHasNoMinimum() {
        val loader = slowLoader(2_000L) { id, _, _ -> LigandLoadResult.Success(molecule(id)) }
        val start = System.nanoTime()
        onMain { loader.load("ATP", quiet = true) }
        assertEquals(LoadState.Loaded(molecule("ATP")), awaitState(loader) { it !is LoadState.Loading })
        assertTrue(System.nanoTime() - start < 1_500_000_000L)
    }

    @Test
    fun aQuietLoadStaysQuietThroughItsStages() {
        val report = AtomicReference<(LoadStage) -> Unit>()
        val release = CountDownLatch(1)
        val loader = loader { id, _, onStage ->
            report.set(onStage)
            release.await(5, TimeUnit.SECONDS)
            LigandLoadResult.Success(molecule(id))
        }
        onMain { loader.load("ATP", quiet = true) }
        assertEquals(LoadState.Loading("ATP", quiet = true), loader.state.value)
        while (report.get() == null) Thread.sleep(5)
        report.get()(LoadStage.Parsing)
        assertEquals(LoadState.Loading("ATP", LoadStage.Parsing, quiet = true), awaitState(loader) { (it as? LoadState.Loading)?.stage == LoadStage.Parsing })
        release.countDown()
    }

    @Test
    fun aCancelDuringTheMinimumWaitPublishesNothing() {
        val loader = slowLoader(400L) { id, _, _ -> LigandLoadResult.Success(molecule(id)) }
        onMain { loader.load("ATP") }
        Thread.sleep(100)            // the file is in; the loader waits for the minimum
        onMain { loader.cancel() }
        Thread.sleep(450)            // past the end of the minimum
        onMain { }
        assertEquals(LoadState.Idle, loader.state.value)
    }

    @Test
    fun aFailureAlsoKeepsTheMinimum() {
        val loader = slowLoader(300L) { _, _, _ -> LigandLoadResult.Failure(LoadError.NoConnection) }
        val start = System.nanoTime()
        onMain { loader.load("ATP") }
        assertEquals(LoadState.Failed("ATP", LoadError.NoConnection), awaitState(loader) { it !is LoadState.Loading })
        assertTrue(System.nanoTime() - start >= 280_000_000L)
    }
}
