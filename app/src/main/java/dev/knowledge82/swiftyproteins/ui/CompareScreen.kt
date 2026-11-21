package dev.knowledge82.swiftyproteins.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.knowledge82.swiftyproteins.catalog.LigandCatalog
import dev.knowledge82.swiftyproteins.catalog.LigandIndex
import dev.knowledge82.swiftyproteins.molecule.Comparison
import dev.knowledge82.swiftyproteins.molecule.MolecularFormula
import dev.knowledge82.swiftyproteins.molecule.Molecule
import dev.knowledge82.swiftyproteins.scene.AtomColors
import dev.knowledge82.swiftyproteins.scene.ElementStyle
import dev.knowledge82.swiftyproteins.scene.FpsMeter
import dev.knowledge82.swiftyproteins.scene.FpsReading
import dev.knowledge82.swiftyproteins.scene.Representation
import dev.knowledge82.swiftyproteins.scene.SceneFraming
import dev.knowledge82.swiftyproteins.scene.SceneLook
import dev.knowledge82.swiftyproteins.sound.LocalSounds
import dev.knowledge82.swiftyproteins.sound.SoundCue
import dev.knowledge82.swiftyproteins.ui.theme.SwiftyColors
import dev.knowledge82.swiftyproteins.ui.theme.SwiftyDataText
import dev.knowledge82.swiftyproteins.ui.theme.glowing
import kotlinx.coroutines.delay

/**
 * "CONCORDANCE": two molecules side by side (bonus VII.5 "comparison view of two molecules", document 24).
 *
 *     < ATP :: CFF                       H   SPIN
 *     ┌ ATP ──────────┐  ┌ CFF ──────────┐
 *     │    3D          │  │    3D          │     each scene its own, its own FPS
 *     └ C10H16… 60 FPS┘  └ C8H10… IDLE   ┘
 *     Formula   C10H16N5O13P3   C8H10N4O2       the numbers, left and right like the scenes
 *     Mass      507.2 g/mol     194.2 g/mol
 *     ...
 *     C ● 10 8    H ● 16 10 ...                 a tap on an element lights it in BOTH scenes
 *
 * The keys act on both scenes at once: H shows or hides the hydrogens of both, SPIN turns both together (the same angle
 * in every frame, [MoleculeScene]'s spinSince). Each scene can still be turned and zoomed by hand on its own.
 *
 * WHY TWO SCENES ARE AFFORDABLE. Each scene draws only when something changes (document 12): at rest both are IDLE.
 * The two FPS counters show what two scenes cost while they assemble or spin (bonus VII.4).
 */
@Composable
fun CompareScreen(
    first: Molecule,
    second: Molecule,
    onBack: () -> Unit,
    model: Representation = Representation.BALL_AND_STICK,
    startWithHydrogens: Boolean = true,
    atomColors: AtomColors = AtomColors.JMOL,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    val sounds = LocalSounds.current

    var showHydrogens by rememberSaveable { mutableStateOf(startWithHydrogens) }
    // The moment the spin started (the frames' clock), or null. Kept over a rotation: the angle simply goes on.
    var spinSince by rememberSaveable { mutableStateOf<Long?>(null) }
    var highlight by rememberSaveable { mutableStateOf<String?>(null) }
    val hasHydrogens = remember(first, second) { (first.atoms + second.atoms).any { it.element == "H" } }

    val rows = remember(first, second) { Comparison.rows(first, second) }
    val elements = remember(first, second) { Comparison.elements(first, second) }
    val match = remember(first, second) { Comparison.matchLine(first, second) }

    // One counter per scene, refreshed 4 times a second (as on the molecule screen).
    val meters = remember { listOf(FpsMeter(), FpsMeter()) }
    var fpsFirst by remember { mutableStateOf<FpsReading>(FpsReading.Idle) }
    var fpsSecond by remember { mutableStateOf<FpsReading>(FpsReading.Idle) }
    LaunchedEffect(meters) {
        while (true) {
            delay(FPS_REFRESH_MILLIS)
            val now = System.nanoTime()
            fpsFirst = meters[0].reading(now)
            fpsSecond = meters[1].reading(now)
        }
    }
    // Made ONCE: a new lambda at each recomposition would be a "changed" parameter of the scene, and a scene that runs
    // again asks SceneView for frames (document 21, §20). The FPS lambdas read the number only when the label draws.
    val onFrames = remember(meters) { listOf<(Long) -> Unit>({ meters[0].onFrame(it) }, { meters[1].onFrame(it) }) }
    val readings = remember { listOf<() -> FpsReading>({ fpsFirst }, { fpsSecond }) }

    // The orientation from the configuration, NOT from a BoxWithConstraints: the scenes must stay outside one
    // (document 21, §20: it ran its content again at every measure, and the scenes never went IDLE).
    val configuration = LocalConfiguration.current
    val landscape = configuration.screenWidthDp > configuration.screenHeightDp
    val look = SceneLook.of(SwiftyColors.palette.isDark)

    val header = @Composable {
        CompareHeader(
            first = first,
            second = second,
            model = model,
            hasHydrogens = hasHydrogens,
            showHydrogens = showHydrogens,
            onToggleHydrogens = {
                showHydrogens = !showHydrogens
                if (!showHydrogens && highlight == "H") highlight = null
            },
            spinning = spinSince != null,
            onToggleSpin = { spinSince = if (spinSince == null) System.nanoTime() else null },
            onBack = onBack,
        )
    }
    val scenes = @Composable { m: Modifier ->
        Row(modifier = m, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((n, molecule) in listOf(first, second).withIndex()) {
                SceneCell(
                    molecule = molecule,
                    model = model,
                    showHydrogens = showHydrogens,
                    highlight = highlight,
                    spinSince = spinSince,
                    atomColors = atomColors,
                    look = look,
                    onFrame = onFrames[n],
                    fps = readings[n],
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
    }
    val table = @Composable { m: Modifier, scrollable: Boolean ->
        NumbersPanel(
            first = first,
            second = second,
            rows = rows,
            elements = elements,
            match = match,
            highlight = highlight,
            showHydrogens = showHydrogens,
            atomColors = atomColors,
            onElement = { element ->
                sounds.play(SoundCue.KEY)
                highlight = if (highlight == element) null else element
            },
            scrollable = scrollable,
            modifier = m,
        )
    }

    CogitatorBackground(modifier) {
        if (landscape) {
            Row(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    header()
                    scenes(Modifier.fillMaxWidth().weight(1f))
                }
                // The side column scrolls as a whole, so the table inside must NOT scroll on its own (a scrolling child
                // of a scrolling parent gets an unlimited height, and Compose refuses it).
                Column(modifier = Modifier.width(COMPARE_SIDE_WIDTH).fillMaxHeight().verticalScroll(rememberScrollState())) {
                    table(Modifier.fillMaxWidth(), false)
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                header()
                scenes(Modifier.fillMaxWidth().weight(1f))
                table(Modifier.fillMaxWidth().heightIn(max = TABLE_MAX_HEIGHT), true)
            }
        }
    }
}

// Portrait: the table takes at most this much; the scenes get the rest. Landscape: the table column's width.
private val TABLE_MAX_HEIGHT = 300.dp
private val COMPARE_SIDE_WIDTH = 330.dp

/** "CONCORDANCE // TWO PATTERNS": back, "ATP :: CFF", the model, and the H and SPIN keys that act on both scenes. */
@Composable
private fun CompareHeader(
    first: Molecule,
    second: Molecule,
    model: Representation,
    hasHydrogens: Boolean,
    showHydrogens: Boolean,
    onToggleHydrogens: () -> Unit,
    spinning: Boolean,
    onToggleSpin: () -> Unit,
    onBack: () -> Unit,
) {
    CogitatorPanel(label = "CONCORDANCE // TWO PATTERNS", fill = SwiftyColors.ConsoleOverlay, contentPadding = 8.dp, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackKey(onClick = onBack)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${first.id} :: ${second.id}",
                    style = MaterialTheme.typography.headlineMedium.glowing(SwiftyColors.Gold, 0.3f),
                    color = SwiftyColors.Gold,
                    maxLines = 1,
                )
                Text(
                    model.label.uppercase(),
                    style = MaterialTheme.typography.labelSmall.glowing(SwiftyColors.Phosphor),
                    color = SwiftyColors.Phosphor,
                    maxLines = 1,
                )
            }
            if (hasHydrogens) {
                ConsoleKey(
                    text = "H",
                    onClick = onToggleHydrogens,
                    contentDescription = "Hydrogen atoms of both molecules",
                    active = showHydrogens,
                    stateDescription = if (showHydrogens) "shown" else "hidden",
                )
                Spacer(Modifier.width(8.dp))
            }
            ConsoleKey(
                text = "SPIN",
                onClick = onToggleSpin,
                contentDescription = "Turn both molecules together",
                active = spinning,
                stateDescription = if (spinning) "on" else "off",
            )
        }
    }
}

/**
 * One molecule: its 3D scene in a framed window, its formula and its own FPS counter. The window's size is measured
 * once it is laid out (the camera needs its shape); until then the scene waits, as on the molecule screen.
 */
@Composable
private fun SceneCell(
    molecule: Molecule,
    model: Representation,
    showHydrogens: Boolean,
    highlight: String?,
    spinSince: Long?,
    atomColors: AtomColors,
    look: SceneLook,
    onFrame: (Long) -> Unit,
    fps: () -> FpsReading,
    modifier: Modifier,
) {
    var size by remember { mutableStateOf<IntSize?>(null) }
    CogitatorPanel(label = molecule.id, fill = SwiftyColors.ConsoleOverlay, contentPadding = 6.dp, fillHeight = true, modifier = modifier) {
        Box(Modifier.fillMaxWidth().weight(1f).onSizeChanged { size = it }) {
            val s = size
            if (s != null && s.width > 0 && s.height > 0) {
                MoleculeScene(
                    molecule = molecule,
                    representation = model,
                    showHydrogens = showHydrogens,
                    selection = null,
                    // An element only one of them has lights nothing in the other: nothing to do here.
                    highlightElement = highlight,
                    focus = null,
                    measured = emptyList(),
                    showLabels = false,
                    // A view to compare, not to inspect: taps do nothing (dragging and pinching still turn and zoom).
                    onTap = {},
                    onDoubleTap = {},
                    modifier = Modifier.fillMaxSize(),
                    onFrame = onFrame,
                    look = look,
                    atomColors = atomColors,
                    contentPadding = PaddingValues(0.dp),
                    visibleAspect = SceneFraming.aspectKey(s.width.toFloat(), s.height.toFloat()),
                    spinSince = spinSince,
                )
            }
            CornerBrackets(Modifier.fillMaxSize().padding(4.dp))
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                formulaText(MolecularFormula.of(molecule.atoms)),
                style = SwiftyDataText.small.glowing(SwiftyColors.Gold, 0.3f),
                color = SwiftyColors.Gold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            FpsLabel(fps)
        }
    }
}

/**
 * The numbers of both molecules, in two columns that match the scenes (left, right), then every element with its two
 * counts. The larger of two different values is gold; equal values are dim, both. An element is a key: it lights its
 * atoms in both scenes.
 */
@Composable
private fun NumbersPanel(
    first: Molecule,
    second: Molecule,
    rows: List<Comparison.Row>,
    elements: List<Comparison.ElementRow>,
    match: String,
    highlight: String?,
    showHydrogens: Boolean,
    atomColors: AtomColors,
    onElement: (String) -> Unit,
    scrollable: Boolean,
    modifier: Modifier,
) {
    CogitatorPanel(label = "CONCORDANCE // NUMBERS", fill = SwiftyColors.ConsoleOverlay, contentPadding = 12.dp, modifier = modifier) {
        val body = if (scrollable) Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()) else Modifier
        Column(body) {
            Text(match, style = MaterialTheme.typography.labelMedium.glowing(SwiftyColors.Phosphor), color = SwiftyColors.Phosphor)
            Spacer(Modifier.height(6.dp))
            TableLine("", first.id, second.id, larger = 0, header = true)
            for (row in rows) {
                if (row.label == "Formula") {
                    // The formula with real subscripts, as everywhere else in the app (document 15).
                    TableLine(row.label, row.first, row.second, larger = 0, formulaOf = listOf(first, second))
                } else {
                    TableLine(row.label, row.first, row.second, row.larger)
                }
            }
            Spacer(Modifier.height(8.dp))
            ConsoleRule(color = SwiftyColors.Brass)
            Spacer(Modifier.height(4.dp))
            for (e in elements) {
                // Hidden hydrogens cannot be lit: their key is off while H is off.
                val enabled = showHydrogens || e.element != "H"
                ElementLine(e, on = e.element == highlight, enabled = enabled, atomColors = atomColors, onClick = { onElement(e.element) })
            }
        }
    }
}

/** One line of the table: the label, then the value of each molecule. */
@Composable
private fun TableLine(
    label: String,
    first: String,
    second: String,
    larger: Int,
    header: Boolean = false,
    formulaOf: List<Molecule>? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).semantics(mergeDescendants = true) { },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = SwiftyDataText.small.glowing(SwiftyColors.Phosphor), color = SwiftyColors.Phosphor, modifier = Modifier.weight(LABEL_WEIGHT), maxLines = 1)
        for ((n, value) in listOf(first, second).withIndex()) {
            val ink = when {
                header -> SwiftyColors.Gold
                first == second -> SwiftyColors.Ash
                larger == (if (n == 0) -1 else 1) -> SwiftyColors.Gold
                else -> SwiftyColors.Bone
            }
            val style = if (header) SwiftyDataText.medium.glowing(ink, 0.3f) else SwiftyDataText.small
            val m = Modifier.weight(VALUE_WEIGHT)
            if (formulaOf != null) {
                Text(formulaText(MolecularFormula.of(formulaOf[n].atoms)), style = style, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, modifier = m)
            } else {
                Text(value, style = style, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, modifier = m)
            }
        }
    }
}

/** One element: its colour and symbol (a 48 dp key), and how many atoms of it each molecule has. */
@Composable
private fun ElementLine(e: Comparison.ElementRow, on: Boolean, enabled: Boolean, atomColors: AtomColors, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(enabled = enabled, onClickLabel = if (on) "Stop highlighting" else "Highlight this element in both molecules") { onClick() }
            .semantics {
                contentDescription = "${ElementStyle.nameOf(e.element)}: ${e.first} and ${e.second}"
                stateDescription = if (on) "highlighted" else "not highlighted"
            }
            .border(if (on) 1.5.dp else 0.dp, if (on) SwiftyColors.Gold else Color.Transparent, CutCornerShape(4.dp))
            .padding(horizontal = 4.dp),
    ) {
        Row(modifier = Modifier.weight(LABEL_WEIGHT), verticalAlignment = Alignment.CenterVertically) {
            val rgb = ElementStyle.colorOf(e.element, atomColors)
            Box(Modifier.size(10.dp).background(Color((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF), CircleShape).border(0.5.dp, SwiftyColors.Bronze, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(e.element, style = SwiftyDataText.medium, color = if (!enabled) SwiftyColors.Disabled else if (on) SwiftyColors.Gold else SwiftyColors.Bone)
        }
        for ((n, count) in listOf(e.first, e.second).withIndex()) {
            val other = if (n == 0) e.second else e.first
            val ink = when {
                count == other -> SwiftyColors.Ash
                count > other -> SwiftyColors.Gold
                else -> SwiftyColors.Bone
            }
            Text(count.toString(), style = SwiftyDataText.small, color = ink, textAlign = TextAlign.End, modifier = Modifier.weight(VALUE_WEIGHT))
        }
    }
}

// The table's columns: the label a little narrower than each value (formulas are the longest values).
private const val LABEL_WEIGHT = 0.8f
private const val VALUE_WEIGHT = 1f

/**
 * "CONCORDANCE // SELECT": which ligand to set beside [exclude]. The same search as the index (by ID), the same cells
 * in short; the open molecule itself is not offered.
 */
@Composable
fun ComparePicker(
    catalog: LigandCatalog?,
    index: LigandIndex,
    archived: Set<String>,
    exclude: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(catalog, query, exclude) { catalog?.search(query)?.filter { it != exclude } ?: emptyList() }
    Dialog(onDismissRequest = onDismiss) {
        CogitatorPanel(label = "CONCORDANCE // SELECT", cut = 11.dp, modifier = Modifier.widthIn(max = 440.dp)) {
            Text("Compare $exclude with…", style = MaterialTheme.typography.titleMedium, color = SwiftyColors.Gold)
            Spacer(Modifier.height(8.dp))
            QueryField(query = query, onQueryChange = { query = it }, enabled = catalog != null)
            Spacer(Modifier.height(6.dp))
            Text(
                ListTexts.patternsFound(catalog != null, results.size, query, results.size),
                style = MaterialTheme.typography.labelSmall.glowing(SwiftyColors.Phosphor),
                color = SwiftyColors.Phosphor,
            )
            // The list takes what the dialog leaves; LazyColumn composes only the rows on screen.
            LazyColumn(modifier = Modifier.weight(1f, fill = false).heightIn(max = PICKER_LIST_MAX)) {
                items(results, key = { it }) { id ->
                    PickRow(id = id, entry = index[id], archived = id in archived, onClick = { onPick(id) })
                }
            }
            Spacer(Modifier.height(12.dp))
            SecondaryButton("Cancel", onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp))
        }
    }
}

private val PICKER_LIST_MAX = 380.dp

/** "> CFF   C8H10N4O2 · 24 ATOMS   ARCHIVED": one choice of the picker, all of it a 56 dp button. */
@Composable
private fun PickRow(id: String, entry: LigandIndex.Entry?, archived: Boolean, onClick: () -> Unit) {
    val sounds = LocalSounds.current
    Column {
        Surface(
            onClick = { sounds.play(SoundCue.KEY); onClick() },
            color = Color.Transparent,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .semantics { contentDescription = ListTexts.cellDescription(id, entry?.name, entry?.formula, entry?.atoms, archived, action = "Compare") },
        ) {
            Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("> $id", style = SwiftyDataText.large.glowing(SwiftyColors.Bone, 0.25f), color = SwiftyColors.Bone, modifier = Modifier.width(80.dp))
                if (entry != null) {
                    val parts = remember(entry) { MolecularFormula.parse(entry.formula) }
                    Text(
                        if (parts != null) formulaText(parts) else androidx.compose.ui.text.AnnotatedString(entry.formula),
                        style = SwiftyDataText.small.glowing(SwiftyColors.Phosphor),
                        color = SwiftyColors.Phosphor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                if (archived) Text("ARCHIVED", style = MaterialTheme.typography.labelSmall, color = SwiftyColors.Brass, maxLines = 1)
            }
        }
        ConsoleRule()
    }
}
