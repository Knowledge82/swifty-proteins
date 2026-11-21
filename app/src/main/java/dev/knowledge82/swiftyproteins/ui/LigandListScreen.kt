package dev.knowledge82.swiftyproteins.ui

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.knowledge82.swiftyproteins.R
import dev.knowledge82.swiftyproteins.catalog.LigandCatalog
import dev.knowledge82.swiftyproteins.prefs.Favorites
import dev.knowledge82.swiftyproteins.ui.theme.SwiftyColors
import dev.knowledge82.swiftyproteins.ui.theme.SwiftyDataText
import dev.knowledge82.swiftyproteins.ui.theme.glowing

/**
 * The Data-Vault Index: the list of ligands with the search box (design v2, screens 06-09).
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
    favorites: Set<String>,
    onlyFavorites: Boolean,
    onToggleOnlyFavorites: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Recomputed ONLY when the catalog, the text or the favourites filter change (not on every recomposition). It is
    // the "real time" search: each keystroke changes `query`, this line runs again and the list below redraws.
    val results = remember(catalog, query, favorites, onlyFavorites) {
        Favorites.filter(catalog?.search(query) ?: emptyList(), favorites, onlyFavorites)
    }
    val total = remember(catalog) { catalog?.search("")?.size ?: 0 }
    val available = catalog != null
    val focusManager = LocalFocusManager.current

    CogitatorBackground(modifier) {
        Column(
            modifier = Modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            HeaderPanel(subtitle = "DATA-VAULT INDEX // PRIMARY ARCHIVE", action = {
                // The settings (bonus VII.2, document 16): a 48 dp cog in the header.
                IconButton(onClick = onSettings, modifier = Modifier.size(48.dp)) {
                    Icon(painterResource(R.drawable.ic_cog), contentDescription = "Settings", tint = SwiftyColors.Gold, modifier = Modifier.size(26.dp))
                }
            })
            TitlePlate(title = "Data-Vault Index", subtitle = "// BINHARIC PATTERN REGISTRY //")

            CogitatorPanel(
                label = ListTexts.archiveLabel(available, total),
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = 12.dp,
                fillHeight = true,
            ) {
                CogitatorPanel(label = "AUSPEX QUERY", contentPadding = 6.dp, scanlines = false) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { QueryField(query = query, onQueryChange = onQueryChange, enabled = available) }
                        // "Favourites only" (document 16): a star, filled when the filter is on.
                        StarKey(
                            on = onlyFavorites,
                            onClick = onToggleOnlyFavorites,
                            description = "Show only favourites",
                            state = if (onlyFavorites) "on" else "off",
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    ListTexts.patternsFound(available, total, query, results.size, onlyFavorites),
                    style = MaterialTheme.typography.labelMedium.glowing(if (available) SwiftyColors.Phosphor else SwiftyColors.OpticRed),
                    color = if (available) SwiftyColors.Phosphor else SwiftyColors.OpticRed,
                )
                Text(":: COGITATOR SEARCH/ACCESS ::", style = MaterialTheme.typography.labelSmall, color = SwiftyColors.Ash)
                Spacer(Modifier.height(6.dp))
                ConsoleRule(color = SwiftyColors.Brass)

                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    when {
                        catalog == null -> EmptyState(
                            art = R.drawable.art_corrupt_slate,
                            message = "The ligand list could not be read. Reinstall the app.",
                        )
                        results.isEmpty() && onlyFavorites -> EmptyState(
                            art = R.drawable.art_servo_skull,
                            message = ListTexts.noFavorites(query),
                            action = { SecondaryButton("Show all", onClick = onToggleOnlyFavorites) },
                        )
                        results.isEmpty() && query.isBlank() -> EmptyState(
                            art = R.drawable.art_archive_404,
                            message = "The ligand list is empty.",
                        )
                        results.isEmpty() -> EmptyState(
                            art = R.drawable.art_servo_skull,
                            message = ListTexts.noMatch(query),
                            action = { SecondaryButton("Clear search", onClick = { onQueryChange("") }) },
                        )
                        else -> LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 4.dp),
                        ) {
                            // `key`: identifies each row by its id, so Compose can reuse rows when the list changes.
                            // LazyColumn composes ONLY the rows on screen (about 10), never all 1243.
                            items(results, key = { it }) { id ->
                                PatternRow(
                                    id = id,
                                    favorite = id in favorites,
                                    onToggleFavorite = { onToggleFavorite(id) },
                                    onClick = {
                                        focusManager.clearFocus()   // hide the keyboard
                                        onOpen(id)
                                    },
                                )
                            }
                        }
                    }
                }
            }

            StatusFooter(line = "NOOSPHERE LINK // STABLE", detail = "01001011 :: FORGE-NET ACTIVE")
        }
    }
}

/** The search box as a console prompt: "> SEARCH BY LIGAND ID", with a 48 dp clear key when there is text. */
@Composable
private fun QueryField(query: String, onQueryChange: (String) -> Unit, enabled: Boolean) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        enabled = enabled,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        textStyle = SwiftyDataText.medium.glowing(SwiftyColors.Phosphor),
        placeholder = { Text("Search by ligand ID", style = SwiftyDataText.medium) },
        leadingIcon = { Text(">", style = SwiftyDataText.large.glowing(SwiftyColors.Phosphor), color = SwiftyColors.Phosphor) },
        trailingIcon = if (query.isEmpty()) null else {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(painterResource(R.drawable.ic_clear), contentDescription = "Clear search", modifier = Modifier.size(20.dp))
                }
            }
        },
        shape = CutCornerShape(6.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = SwiftyColors.Phosphor,
            unfocusedTextColor = SwiftyColors.Phosphor,
            focusedContainerColor = SwiftyColors.Abyss,
            unfocusedContainerColor = SwiftyColors.Abyss,
            disabledContainerColor = SwiftyColors.Abyss,
            focusedBorderColor = SwiftyColors.Phosphor,
            unfocusedBorderColor = Color.Transparent,
            cursorColor = SwiftyColors.Phosphor,
            focusedTrailingIconColor = SwiftyColors.Gold,
            unfocusedTrailingIconColor = SwiftyColors.Gold,
            focusedPlaceholderColor = SwiftyColors.Ash,
            unfocusedPlaceholderColor = SwiftyColors.Ash,
        ),
    )
}

/** One ligand as a console line: "> 001 ........ [INSPECT] ☆". 56 dp tall, all of it tappable; the star is its own key. */
@Composable
private fun PatternRow(id: String, favorite: Boolean, onToggleFavorite: () -> Unit, onClick: () -> Unit) {
    Column {
        Surface(onClick = onClick, color = Color.Transparent, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Row(modifier = Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(">", style = SwiftyDataText.large.glowing(SwiftyColors.Phosphor), color = SwiftyColors.Phosphor)
                Spacer(Modifier.width(16.dp))
                Text(id, style = SwiftyDataText.large.glowing(SwiftyColors.Bone, 0.25f), color = SwiftyColors.Bone, modifier = Modifier.width(72.dp))
                Text("·······", style = SwiftyDataText.small, color = SwiftyColors.PhosphorDim, modifier = Modifier.weight(1f))
                Text("[INSPECT]", style = SwiftyDataText.small.glowing(SwiftyColors.Phosphor), color = SwiftyColors.Phosphor)
                StarKey(
                    on = favorite,
                    onClick = onToggleFavorite,
                    description = "Favourite $id",
                    state = if (favorite) "favourite" else "not favourite",
                )
            }
        }
        ConsoleRule()
    }
}

/** A painted illustration with a message (and an optional action) in the middle of the archive panel. */
@Composable
private fun EmptyState(art: Int, message: String, action: (@Composable () -> Unit)? = null) {
    CenteredColumn(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        ArtImage(art, size = 140.dp)
        Spacer(Modifier.height(14.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = SwiftyColors.Bone, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(18.dp))
            action()
        }
    }
}
