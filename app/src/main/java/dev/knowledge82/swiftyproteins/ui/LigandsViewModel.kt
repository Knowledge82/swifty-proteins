package dev.knowledge82.swiftyproteins.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.knowledge82.swiftyproteins.catalog.LigandCatalog
import dev.knowledge82.swiftyproteins.loading.LigandLoader
import dev.knowledge82.swiftyproteins.loading.LigandSource
import dev.knowledge82.swiftyproteins.loading.LoadState
import dev.knowledge82.swiftyproteins.net.HttpUrlConnectionFetcher
import dev.knowledge82.swiftyproteins.net.LigandRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow

private const val TAG = "SwiftyProteins"

/**
 * Thin wrapper (decision D18): it holds the objects that must SURVIVE a screen rotation and forwards the calls.
 * All the rules live in [LigandLoader] and [LigandCatalog], which are tested on the JVM.
 *
 * It receives plain lambdas instead of a `Context`, so nothing in here depends on Android classes except
 * `ViewModel` and `Log`.
 *
 * @param readCatalogText returns the whole text of `ligands.txt` (may throw if the file cannot be read)
 * @param source          loads one ligand (blocking; it is run on `Dispatchers.IO` by the loader)
 */
class LigandsViewModel(
    readCatalogText: () -> String,
    source: LigandSource,
) : ViewModel() {

    /** The ligand list, or `null` if `ligands.txt` could not be read (a broken installation: the screen says so). */
    val catalog: LigandCatalog? = try {
        LigandCatalog.fromText(readCatalogText())
    } catch (e: Exception) {
        Log.e(TAG, "ligands.txt could not be read", e)
        null
    }

    private val loader = LigandLoader(source, viewModelScope, Dispatchers.IO)

    /** What the screens observe. */
    val loadState: StateFlow<LoadState> = loader.state

    /** Called from the main thread (a tap). */
    fun open(id: String) = loader.load(id)

    /** Called from the main thread (Cancel, Back, closing an error). Returns the state to Idle. */
    fun cancel() = loader.cancel()
}

/**
 * Builds the [LigandsViewModel] with the real pieces: the file in `assets/` and the real network.
 *
 * A plain `ViewModelProvider.Factory` (and not the `viewModelFactory { initializer { } }` DSL) because overriding
 * `create(Class)` compiles with every version of the library, and we have not verified which one Gradle will pick.
 */
class LigandsViewModelFactory(context: Context) : ViewModelProvider.Factory {

    // The APPLICATION context, never an Activity: a ViewModel outlives the activity and would keep it in memory.
    private val appContext = context.applicationContext

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(LigandsViewModel::class.java)) {
            "This factory only builds LigandsViewModel, not ${modelClass.name}"
        }
        val repository = LigandRepository(HttpUrlConnectionFetcher())
        return LigandsViewModel(
            readCatalogText = {
                appContext.assets.open(CATALOG_FILE).bufferedReader().use { it.readText() }
            },
            source = { id, isCancelled -> repository.load(id, isCancelled) },
        ) as T
    }

    private companion object {
        const val CATALOG_FILE = "ligands.txt"
    }
}
