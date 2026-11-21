package dev.knowledge82.swiftyproteins.ui

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.BoxWithConstraints
import dev.knowledge82.swiftyproteins.scene.ElementStyle
import dev.knowledge82.swiftyproteins.scene.AtomColors
import dev.knowledge82.swiftyproteins.molecule.MolecularFormula
import dev.knowledge82.swiftyproteins.catalog.LigandIndex
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.border
import androidx.compose.foundation.background
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
 * @param catalog  the ligands, or `null` if the file could not be read
 * @param index    name, formula and size of each ligand, for the cells and the search by name (document 21)
 * @param archived the ids with a copy on the phone: their cells say ARCHIVED
 */
@Composable
fun LigandListScreen(
    catalog: LigandCatalog?,
    index: LigandIndex = LigandIndex.EMPTY,
    archived: Set<String> = emptySet(),
    atomColors: AtomColors = AtomColors.JMOL,
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
    val results = remember(catalog, index, query, favorites, onlyFavorites) {
        Favorites.filter(catalog?.search(query, index::nameOf) ?: emptyList(), favorites, onlyFavorites)
    }
    val total = remember(catalog) { catalog?.search("")?.size ?: 0 }
    val available = catalog != null
    val focusManager = LocalFocusManager.current

    // The pieces of the screen, laid out in a column (portrait) or in two columns (landscape, document 21, §19): in
    // landscape the phone is only ~400 dp tall, and the header, the title, the search and the footer stacked above the
    // list left it NO room at all (seen on the A33).
    val header = @Composable {
        HeaderPanel(subtitle = "DATA-VAULT INDEX // PRIMARY ARCHIVE", action = {
            // The settings (bonus VII.2, document 16): a 48 dp cog in the header.
            IconButton(onClick = onSettings, modifier = Modifier.size(48.dp)) {
                Icon(painterResource(R.drawable.ic_cog), contentDescription = "Settings", tint = SwiftyColors.Gold, modifier = Modifier.size(26.dp))
            }
        })
    }
    val title = @Composable { TitlePlate(title = "Data-Vault Index", subtitle = "// BINHARIC PATTERN REGISTRY //") }
    val search = @Composable {
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
    }
    val list = @Composable { m: Modifier ->
        Box(modifier = m) {
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
                            entry = index[id],
                            archived = id in archived,
                            atomColors = atomColors,
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
    val footer = @Composable { StatusFooter(line = "NOOSPHERE LINK // STABLE", detail = "01001011 :: FORGE-NET ACTIVE") }

    CogitatorBackground(modifier) {
        BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
            val landscape = maxWidth > maxHeight
            // Read in THIS scope: inside the Row below it would come from an outer layout scope (@LayoutScopeMarker).
            val sideWidth = minOf(maxWidth * 0.42f, LIST_SIDE_MAX)
            if (landscape) {
                Row(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Left: who we are and what to search, scrolling if the keyboard or a big font needs it.
                    Column(
                        modifier = Modifier.width(sideWidth).fillMaxHeight().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        header()
                        title()
                        CogitatorPanel(label = ListTexts.archiveLabel(available, total), contentPadding = 12.dp) { search() }
                        footer()
                    }
                    // Right: the list, the whole height.
                    CogitatorPanel(label = "PATTERNS", modifier = Modifier.weight(1f).fillMaxHeight(), contentPadding = 12.dp, fillHeight = true) {
                        list(Modifier.fillMaxWidth().weight(1f))
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    header()
                    title()
                    CogitatorPanel(
                        label = ListTexts.archiveLabel(available, total),
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = 12.dp,
                        fillHeight = true,
                    ) {
                        search()
                        Spacer(Modifier.height(6.dp))
                        ConsoleRule(color = SwiftyColors.Brass)
                        list(Modifier.fillMaxWidth().weight(1f))
                    }
                    footer()
                }
            }
        }
    }
}

// The search column in landscape: about two fifths of the screen, never wider than this.
private val LIST_SIDE_MAX = 400.dp

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
        placeholder = { Text("Search by ID or name", style = SwiftyDataText.medium) },
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

/**
 * One ligand as a rich console cell (bonus VII.2 "custom cells", document 21), all of it tappable, the star its own key:
 *
 *     > ATP   ADENOSINE-5'-TRIPHOSPHATE                    ☆
 *             C10H16N5O13P3 · 47 ATOMS            ARCHIVED
 *             ▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇  (the share of each element, in its colour)
 *
 * Without an index entry (the file is missing, or a ligand added later) it is the old line: the id alone.
 */
@Composable
private fun PatternRow(
    id: String,
    entry: LigandIndex.Entry?,
    archived: Boolean,
    atomColors: AtomColors,
    favorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
) {
    val parts = remember(entry) { entry?.formula?.let { MolecularFormula.parse(it) } }
    val spoken = ListTexts.cellDescription(id, entry?.name, entry?.formula, entry?.atoms, archived)
    Column {
        Surface(
            onClick = onClick,
            color = Color.Transparent,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics { contentDescription = spoken },
        ) {
            Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(">", style = SwiftyDataText.large.glowing(SwiftyColors.Phosphor), color = SwiftyColors.Phosphor)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f).clearAndSetSemantics { }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(id, style = SwiftyDataText.large.glowing(SwiftyColors.Bone, 0.25f), color = SwiftyColors.Bone, modifier = Modifier.width(64.dp))
                        Text(
                            entry?.name?.takeIf { it.isNotEmpty() } ?: "·······",
                            style = SwiftyDataText.small,
                            color = if (entry == null) SwiftyColors.PhosphorDim else SwiftyColors.Ash,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (entry != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (parts != null) formulaText(parts) else AnnotatedString(entry.formula),
                                style = SwiftyDataText.small.glowing(SwiftyColors.Phosphor),
                                color = SwiftyColors.Phosphor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            Text(" · ${ListTexts.atoms(entry.atoms)}", style = SwiftyDataText.small, color = SwiftyColors.Ash, maxLines = 1)
                            Spacer(Modifier.weight(1f))
                            if (archived) Text("ARCHIVED", style = MaterialTheme.typography.labelSmall, color = SwiftyColors.Brass, maxLines = 1)
                        }
                        if (parts != null) {
                            Spacer(Modifier.height(4.dp))
                            CompositionBar(parts, atomColors)
                        }
                    }
                }
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

/** A 3 dp strip cut into the share of each element, in the colours of the 3D view (decoration: TalkBack has the words). */
@Composable
private fun CompositionBar(parts: List<MolecularFormula.Part>, atomColors: AtomColors) {
    Row(modifier = Modifier.fillMaxWidth().height(3.dp).border(0.5.dp, SwiftyColors.Bronze)) {
        for ((element, share) in ListTexts.composition(parts)) {
            val rgb = ElementStyle.colorOf(element, atomColors)
            Box(Modifier.weight(share).fillMaxHeight().background(Color(((rgb shr 16) and 0xFF), ((rgb shr 8) and 0xFF), rgb and 0xFF)))
        }
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
