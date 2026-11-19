package dev.knowledge82.swiftyproteins.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.knowledge82.swiftyproteins.catalog.LigandCatalog

/**
 * The list of ligands with the search box on top.
 *
 * It owns NO state: the text, the scroll position and what to do on a tap come from outside ("state hoisting").
 * That is what lets [SwiftyApp] keep them while the user looks at a molecule and comes back.
 *
 * @param catalog the ligands, or `null` if the file could not be read
 */
@Composable
fun LigandListScreen(
    catalog: LigandCatalog?,
    query: String,
    onQueryChange: (String) -> Unit,
    listState: LazyListState,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Recomputed ONLY when the catalog or the text change (not on every recomposition). It is the "real time"
    // search: each keystroke changes `query`, this line runs again and the list below redraws.
    val results = remember(catalog, query) { catalog?.search(query) ?: emptyList() }
    val focusManager = LocalFocusManager.current

    Column(modifier = modifier.fillMaxSize().imePadding()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("Search ligand (for example ATP, hem, 01)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )

        when {
            catalog == null ->
                CenteredMessage("The ligand list could not be read. Reinstall the app.")
            results.isEmpty() && query.isBlank() ->
                CenteredMessage("The ligand list is empty.")
            results.isEmpty() ->
                CenteredMessage("No ligand matches \"${query.trim()}\".")
            else -> {
                Text(
                    text = if (query.isBlank()) "${results.size} ligands" else "${results.size} matches",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    // `key`: identifies each row by its id, so Compose can reuse rows when the list changes.
                    items(results, key = { it }) { id ->
                        Text(
                            text = id,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    focusManager.clearFocus()   // hide the keyboard
                                    onOpen(id)
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CenteredMessage(text: String) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text = text, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
    }
}
