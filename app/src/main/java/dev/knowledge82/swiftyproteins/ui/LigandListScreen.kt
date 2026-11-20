package dev.knowledge82.swiftyproteins.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.knowledge82.swiftyproteins.R
import dev.knowledge82.swiftyproteins.catalog.LigandCatalog
import dev.knowledge82.swiftyproteins.ui.theme.SwiftyColors
import dev.knowledge82.swiftyproteins.ui.theme.SwiftyDataText

/**
 * The list of ligands with the search box on top (design screens 05-08).
 *
 * It owns NO state: the text, the scroll position and what to do on a tap come from outside ("state hoisting").
 * That is what lets [SwiftyApp] keep them while the user looks at a molecule and comes back.
 * Only the LOOK changed with the design; the logic (search, keys, hiding the keyboard) is the same as before.
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
    val total = remember(catalog) { catalog?.search("")?.size ?: 0 }
    val available = catalog != null
    val focusManager = LocalFocusManager.current

    Column(modifier = modifier.fillMaxSize().imePadding()) {
        BrandBar()

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(16.dp))
            Text("Ligand catalog", style = MaterialTheme.typography.headlineMedium, color = SwiftyColors.Gold)
            Text(
                ListTexts.status(available, total, query, results.size),
                style = MaterialTheme.typography.labelSmall,
                color = if (available) SwiftyColors.Phosphor else SwiftyColors.Error,
            )
            Spacer(Modifier.height(12.dp))
            SearchField(query = query, onQueryChange = onQueryChange, enabled = available)
            Spacer(Modifier.height(12.dp))
            ColumnHeader()
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                catalog == null -> EmptyState(
                    icon = R.drawable.ic_warning,
                    iconTint = SwiftyColors.Error,
                    title = "Catalog unavailable",
                    message = "The ligand list could not be read. Reinstall the app.",
                )
                results.isEmpty() && query.isBlank() -> EmptyState(
                    icon = R.drawable.ic_warning,
                    iconTint = SwiftyColors.Amber,
                    title = "Catalog empty",
                    message = "The ligand list is empty.",
                )
                results.isEmpty() -> EmptyState(
                    icon = R.drawable.ic_search,
                    iconTint = SwiftyColors.Brass,
                    title = "No ligand matches '${query.trim()}'",
                    message = "Try another ID or clear the search.",
                    action = { SecondaryButton("Clear search", onClick = { onQueryChange("") }, icon = R.drawable.ic_close) },
                )
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // `key`: identifies each row by its id, so Compose can reuse rows when the list changes.
                    // LazyColumn composes ONLY the rows on screen (about 12), never all 1243.
                    items(results, key = { it }) { id ->
                        LigandRow(id = id, onClick = {
                            focusManager.clearFocus()   // hide the keyboard
                            onOpen(id)
                        })
                    }
                }
            }
        }

        // The status line at the bottom, small and grey: flavour, but every word of it is true.
        Text(
            ListTexts.footer(available, total, query, results.size),
            style = MaterialTheme.typography.labelSmall,
            color = SwiftyColors.Ash,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, enabled: Boolean) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        enabled = enabled,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        textStyle = MaterialTheme.typography.bodyLarge,
        placeholder = { Text("Search by ligand ID", style = MaterialTheme.typography.bodyLarge) },
        leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null, modifier = Modifier.size(20.dp)) },
        trailingIcon = if (query.isEmpty()) null else {
            {
                // IconButton gives the 48 dp touch target around a 20 dp icon.
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(painterResource(R.drawable.ic_clear), contentDescription = "Clear search", modifier = Modifier.size(20.dp))
                }
            }
        },
        shape = CutCornerShape(6.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = SwiftyColors.Bone,
            unfocusedTextColor = SwiftyColors.Bone,
            focusedContainerColor = SwiftyColors.Plate,
            unfocusedContainerColor = SwiftyColors.Plate,
            disabledContainerColor = SwiftyColors.Plate,
            focusedBorderColor = SwiftyColors.Gold,
            unfocusedBorderColor = SwiftyColors.Rivet,
            cursorColor = SwiftyColors.Gold,
            focusedLeadingIconColor = SwiftyColors.Gold,
            unfocusedLeadingIconColor = SwiftyColors.Ash,
            focusedTrailingIconColor = SwiftyColors.Bone,
            unfocusedTrailingIconColor = SwiftyColors.Bone,
            focusedPlaceholderColor = SwiftyColors.Ash,
            unfocusedPlaceholderColor = SwiftyColors.Ash,
        ),
    )
}

/** "ID ............ ACTION" with a brass rule below. */
@Composable
private fun ColumnHeader() {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        SectionLabel("ID", modifier = Modifier.weight(1f))
        SectionLabel("Action")
    }
    Spacer(Modifier.height(4.dp))
    Canvas(Modifier.fillMaxWidth().height(1.dp)) {
        drawLine(SwiftyColors.Brass, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
    }
}

/** One ligand: a red mark, the id in gold monospace, "INSPECT ›" on the right. 56 dp tall, all of it tappable. */
@Composable
private fun LigandRow(id: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = CutCornerShape(4.dp),
        color = SwiftyColors.Iron,
        border = BorderStroke(1.dp, SwiftyColors.Rivet),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The red mark is a FILL (decoration), not text: red stays legal (document 08a, section 3).
            Canvas(Modifier.width(3.dp).height(24.dp)) { drawRect(SwiftyColors.Blood) }
            Spacer(Modifier.width(14.dp))
            Text(id, style = SwiftyDataText.large, color = SwiftyColors.Gold, modifier = Modifier.weight(1f))
            SectionLabel("Inspect", color = SwiftyColors.Ash)
            Spacer(Modifier.width(6.dp))
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = SwiftyColors.Brass, modifier = Modifier.size(18.dp))
        }
    }
}

/** Centred icon, ornament, title, explanation and an optional action: empty search, unreadable catalog. */
@Composable
private fun EmptyState(
    @DrawableRes icon: Int,
    iconTint: Color,
    title: String,
    message: String,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = iconTint, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        SeamDivider()
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = SwiftyColors.Bone, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = SwiftyColors.Ash, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            action()
        }
    }
}
