package dev.knowledge82.swiftyproteins.ui

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.knowledge82.swiftyproteins.auth.AuthController
import dev.knowledge82.swiftyproteins.loading.LoadState
import kotlinx.coroutines.launch

/**
 * The root of the screen: decides WHAT to show (no navigation library, see D19).
 *
 *   not booted yet          -> the cogitator boot screen (the launch screen, once per start)
 *   locked                  -> sign-in / create account (document 05): ALWAYS before anything else (D43)
 *   Loaded                  -> the 3D screen of the molecule
 *   Idle / Loading / Failed -> the list, with a dialog on top while loading or when it failed
 *
 * While locked, the list and the molecule are NOT drawn, but their state is kept (here and in the ViewModels): after
 * signing in again the user is back where they were.
 */
@Composable
fun SwiftyApp(viewModel: LigandsViewModel, auth: AuthController) {
    val state by viewModel.loadState.collectAsStateWithLifecycle()
    val authState by auth.state.collectAsStateWithLifecycle()

    // Kept HERE (and not inside the list screen) because the list screen leaves the composition while the molecule
    // is shown, and anything it remembered would be lost: the user would come back to an empty search box at the top.
    var query by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // rememberSaveable: rotating the phone does not replay the boot screen; a fresh start does.
    var booted by rememberSaveable { mutableStateOf(false) }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        // consumeWindowInsets: tells the children "the system bars are already taken into account", so that
        // imePadding() inside the list does not add them a second time when the keyboard opens.
        val content = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding)

        val current = state   // `state` is a delegated property: copy it to be able to use `is` checks on it
        if (!booted) {
            val size = remember(viewModel) { viewModel.catalog?.search("")?.size }
            BootScreen(catalogSize = size, onFinished = { booted = true }, modifier = content)
        } else if (!authState.unlocked) {
            AccessScreen(state = authState, controller = auth, modifier = content)
        } else if (current is LoadState.Loaded) {
            MoleculeScreen(
                molecule = current.molecule,
                onBack = viewModel::cancel,
                modifier = content,
            )
        } else {
            LigandListScreen(
                catalog = viewModel.catalog,
                query = query,
                onQueryChange = { newText ->
                    query = newText
                    // A new search starts at the top; without this the list would keep an old position.
                    scope.launch { listState.scrollToItem(0) }
                },
                listState = listState,
                onOpen = viewModel::open,
                modifier = content,
            )
            LoadStateDialogs(
                state = current,
                onOpen = viewModel::open,
                onClose = viewModel::cancel,
            )
        }
        // After the boot screen, over everything (some appear right after unlocking, on top of the list).
        if (booted) AuthDialogs(state = authState, controller = auth)
    }
}
