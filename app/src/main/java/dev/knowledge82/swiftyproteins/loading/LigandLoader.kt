package dev.knowledge82.swiftyproteins.loading

import dev.knowledge82.swiftyproteins.net.LigandLoadResult
import dev.knowledge82.swiftyproteins.net.LoadError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Whatever can load a ligand by id. The real one is [dev.knowledge82.swiftyproteins.net.LigandRepository].
 * It reports the stage it is in through `onStage` (document 20), from ITS thread (a background one).
 */
typealias LigandSource = (id: String, isCancelled: () -> Boolean, onStage: (LoadStage) -> Unit) -> LigandLoadResult

/**
 * Runs a load in the background and publishes its progress as a [LoadState].
 *
 * The guarantee this class exists for: **after [load] the state is [LoadState.Loading] until the work ends, and
 * then it is ALWAYS something else** (loaded, failed or idle), whatever happened: success, any of the errors, a
 * bug that throws, or a cancellation. The loading indicator can therefore never stay on the screen forever.
 *
 * Threads: [load] and [cancel] are meant to be called from ONE thread (the main thread). The scope must run its
 * continuations on that same thread (`viewModelScope` does). The slow work runs on [workDispatcher].
 *
 * Progress (document 20): the source reports stages from the background thread. They are NOT written to the state
 * there: each one is handed to the main thread, and written only if the state is still Loading and THIS load is
 * still the current one. Every write of the state happens on one thread, so a late stage of a cancelled load can
 * never turn "Idle" back into "Loading", nor overwrite the result: the guarantee above still holds.
 */
class LigandLoader(
    private val source: LigandSource,
    private val scope: CoroutineScope,
    private val workDispatcher: CoroutineDispatcher,
) {
    private val mutableState = MutableStateFlow<LoadState>(LoadState.Idle)

    /** What the screen observes. */
    val state: StateFlow<LoadState> = mutableState.asStateFlow()

    private var job: Job? = null

    /** Starts loading [id]. If another load is running it is cancelled first and its result is thrown away. */
    fun load(id: String) {
        job?.cancel()
        mutableState.value = LoadState.Loading(id)

        job = scope.launch {
            val mine = coroutineContext.job
            // Called on the WORK thread: hands the stage over to the main thread, where the state is written.
            val onStage: (LoadStage) -> Unit = { stage ->
                scope.launch { if (job === mine && mutableState.value is LoadState.Loading) mutableState.value = LoadState.Loading(id, stage) }
            }
            val result = try {
                // The lambda { !isActive } is what the network code asks while it reads: false once this job
                // has been cancelled. Inside withContext, `isActive` is the one of THIS block.
                withContext(workDispatcher) { source(id, { !isActive }, onStage) }
            } catch (e: CancellationException) {
                throw e   // cancelled: do not publish anything, somebody else already decided the state
            } catch (e: Exception) {
                LigandLoadResult.Failure(LoadError.Unexpected(e.toString()))
            }

            mutableState.value = when (result) {
                is LigandLoadResult.Success -> LoadState.Loaded(result.molecule, result.origin)
                is LigandLoadResult.Failure -> LoadState.Failed(id, result.error)
                is LigandLoadResult.Cancelled -> LoadState.Idle
            }
        }
    }

    /** The user left: stops the work and returns to [LoadState.Idle]. Whatever arrives later is ignored. */
    fun cancel() {
        job?.cancel()
        job = null
        mutableState.value = LoadState.Idle
    }
}
