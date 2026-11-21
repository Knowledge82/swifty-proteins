package dev.knowledge82.swiftyproteins.ui

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import dev.knowledge82.swiftyproteins.molecule.MolecularFormula
import dev.knowledge82.swiftyproteins.scene.Drawn
import dev.knowledge82.swiftyproteins.scene.Representation
import dev.knowledge82.swiftyproteins.scene.ScenePick
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.knowledge82.swiftyproteins.molecule.Molecule
import dev.knowledge82.swiftyproteins.scene.ElementLegend
import dev.knowledge82.swiftyproteins.scene.ElementStyle
import dev.knowledge82.swiftyproteins.net.Origin
import dev.knowledge82.swiftyproteins.scene.FpsMeter
import dev.knowledge82.swiftyproteins.sound.LocalSounds
import dev.knowledge82.swiftyproteins.sound.SoundCue
import dev.knowledge82.swiftyproteins.scene.FpsReading
import dev.knowledge82.swiftyproteins.scene.FpsText
import dev.knowledge82.swiftyproteins.share.CaptureException
import dev.knowledge82.swiftyproteins.share.CaptureFailure
import dev.knowledge82.swiftyproteins.share.MoleculeSharer
import dev.knowledge82.swiftyproteins.share.ShareStatus
import dev.knowledge82.swiftyproteins.ui.theme.SwiftyColors
import dev.knowledge82.swiftyproteins.ui.theme.SwiftyDataText
import dev.knowledge82.swiftyproteins.ui.theme.glowing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The screen of one molecule, the "Auspex Readout" (design v2, screens 16-22): the 3D scene full size, a panel on
 * top (back, pattern name, hydrogens, share), targeting brackets around the viewport, the share status, the readout of
 * the tapped atom and the CPK colour key.
 *
 * The LOGIC is the same as before the design (selection, hydrogen switch, Back). The Share button (document 09)
 * captures the 3D scene and opens Android's share sheet; a small status line says what it is doing.
 */
@Composable
fun MoleculeScreen(
    molecule: Molecule,
    origin: Origin,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The system Back button returns to the list, exactly like the button on screen.
    BackHandler(onBack = onBack)

    // rememberSaveable: all of this survives a rotation of the phone. At most ONE of selectedAtom / selectedBond is set.
    var selectedAtom by rememberSaveable { mutableStateOf<Int?>(null) }
    var selectedBond by rememberSaveable { mutableStateOf<Int?>(null) }
    var showHydrogens by rememberSaveable { mutableStateOf(true) }
    var representation by rememberSaveable { mutableStateOf(Representation.BALL_AND_STICK) }   // bonus VII.1
    var highlightElement by rememberSaveable { mutableStateOf<String?>(null) }                  // bonus VII.3
    var focus by rememberSaveable { mutableStateOf<Int?>(null) }                                // bonus VII.3
    val hasHydrogens = remember(molecule) { molecule.atoms.any { it.element == "H" } }
    val legend = remember(molecule, showHydrogens) { ElementLegend.of(molecule, showHydrogens) }

    // Share: the state of the button and the job. The job lives in the screen's coroutine scope, so leaving the
    // screen cancels a capture in progress (nothing is left half done in the background).
    var share by remember { mutableStateOf<ShareStatus>(ShareStatus.Idle) }
    val sounds = LocalSounds.current

    // The frame counter, in EVERY build (a learning project: the number is part of what we show). The scene reports
    // each presented frame; the label is refreshed 4 times a second (not on every frame: no recomposition at 60 Hz).
    val fpsMeter = remember { FpsMeter() }
    var fps by remember { mutableStateOf<FpsReading>(FpsReading.Idle) }
    LaunchedEffect(fpsMeter) {
        while (true) {
            delay(FPS_REFRESH_MILLIS)
            fps = fpsMeter.reading(System.nanoTime())   // the same clock as the frame times
        }
    }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val view = LocalView.current
    val onShare = {
        if (share.canStart) {
            share = ShareStatus.Working
            sounds.play(SoundCue.SHUTTER)
            scope.launch {
                share = try {
                    MoleculeSharer.share(context, view, molecule)
                    ShareStatus.Done
                } catch (e: CancellationException) {
                    throw e   // the screen went away: not a failure, just stop
                } catch (e: CaptureException) {
                    Log.w("SwiftyProteins", "share ${molecule.id} failed: ${e.failure}", e.cause)
                    ShareStatus.Failed(e.failure)
                } catch (e: Exception) {
                    Log.w("SwiftyProteins", "share ${molecule.id} failed", e)
                    ShareStatus.Failed(CaptureFailure.OTHER)
                }
            }
        }
    }
    // "Image ready" or an error stays on screen a few seconds, then the line goes away.
    LaunchedEffect(share) {
        if (share is ShareStatus.Done || share is ShareStatus.Failed) {
            delay(3_000)
            share = ShareStatus.Idle
        }
    }

    // What is REALLY highlighted: something that exists AND is drawn now (not a hidden hydrogen, not a bond in
    // space-filling). The remembered index stays: undoing the switch brings the highlight back.
    val selection = Drawn.selection(molecule, selectedAtom, selectedBond, showHydrogens, representation)
    fun select(pick: ScenePick?) {
        if (pick != null) sounds.play(SoundCue.SELECT)
        selectedAtom = (pick as? ScenePick.Atom)?.index
        selectedBond = (pick as? ScenePick.Bond)?.index
    }

    // The subject (VI.4): the atom information disappears when the user taps ANYWHERE else. A tap on the empty scene or
    // on another atom already does it (MoleculeScene); this makes a tap on a PANEL do it too. Only on the panels: the
    // middle of the screen must keep sending every touch to the molecule. The keys inside the panels (H, SHR, back)
    // keep their own action: they take the tap first. No ripple; TalkBack announces the action.
    val deselect = Modifier.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        enabled = selection != null,
        onClickLabel = "Hide the atom information",
    ) { select(null) }

    Box(modifier = modifier.fillMaxSize()) {
        MoleculeScene(
            molecule = molecule,
            representation = representation,
            showHydrogens = showHydrogens,
            selection = selection,
            highlightElement = highlightElement,
            focus = focus,
            onTap = ::select,
            // Double tap on an atom: orbit around it (and select it). On empty space: back to the whole molecule.
            // On a bond: nothing (a bond has no single point that is "it").
            onDoubleTap = { pick ->
                when (pick) {
                    is ScenePick.Atom -> { focus = pick.index; select(pick) }
                    null -> focus = null
                    is ScenePick.Bond -> Unit
                }
            },
            modifier = Modifier.fillMaxSize(),
            onFrame = fpsMeter::onFrame,
        )

        // The frame AROUND the scene. Only the panels catch touches (they are Surfaces); the brackets and the empty
        // middle let every touch through to the molecule.
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp)) {
            TopPanel(
                modifier = deselect,
                molecule = molecule,
                hasHydrogens = hasHydrogens,
                showHydrogens = showHydrogens,
                onToggleHydrogens = {
                    showHydrogens = !showHydrogens
                    // A highlighted element that disappears from the key stops being highlighted.
                    if (!showHydrogens && highlightElement == "H") highlightElement = null
                },
                onBack = onBack,
                share = share,
                onShare = onShare,
                fps = fps,
                origin = origin,
            )
            CornerBrackets(Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 14.dp))
            share.label?.let { text ->
                ShareStatusPanel(text = text, status = share, modifier = deselect)
                Spacer(Modifier.height(14.dp))
            }
            ReadoutPanel(molecule = molecule, selection = selection, hydrogensHidden = hasHydrogens && !showHydrogens, modifier = deselect)
            Spacer(Modifier.height(10.dp))
            ElementKey(
                elements = legend,
                highlighted = highlightElement,
                onToggle = { element -> highlightElement = if (highlightElement == element) null else element },
                representation = representation,
                onNextRepresentation = { representation = representation.next() },
                modifier = deselect,
            )
        }
    }
}

/** "AUSPEX // RUNNING": back, "Pattern 001", "87 ATOMS :: 90 BONDS", the H key and the SHR key. */
@Composable
private fun TopPanel(
    modifier: Modifier,
    molecule: Molecule,
    hasHydrogens: Boolean,
    showHydrogens: Boolean,
    onToggleHydrogens: () -> Unit,
    onBack: () -> Unit,
    share: ShareStatus,
    onShare: () -> Unit,
    fps: FpsReading,
    origin: Origin,
) {
    // The tab says where the molecule came from (document 13): downloaded now, or the phone's copy (offline too).
    val tab = when (origin) {
        Origin.NETWORK -> "AUSPEX // LIVE"
        Origin.ARCHIVE -> "AUSPEX // ARCHIVE"
        Origin.ARCHIVE_OFFLINE -> "AUSPEX // OFFLINE ARCHIVE"
    }
    CogitatorPanel(label = tab, fill = SwiftyColors.ConsoleOverlay, contentPadding = 8.dp, modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackKey(onClick = onBack)
            Column(modifier = Modifier.weight(1f)) {
                Text("Pattern ${molecule.id}", style = MaterialTheme.typography.displayLarge.glowing(SwiftyColors.Gold, 0.3f), color = SwiftyColors.Gold, maxLines = 1)
                Row {
                    Text(
                        "${molecule.atoms.size} ATOMS :: ${molecule.bonds.size} BONDS :: ",
                        style = MaterialTheme.typography.labelSmall.glowing(SwiftyColors.Phosphor),
                        color = SwiftyColors.Phosphor,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    // "60 FPS" while something moves, "IDLE" when the scene is parked (it draws on demand). The level
                    // is in the WORDS too (the number), never only in the colour.
                    val ink = fpsColor(FpsText.level(fps))
                    Text(FpsText.label(fps), style = MaterialTheme.typography.labelSmall.glowing(ink), color = ink, maxLines = 1)
                }
            }
            if (hasHydrogens) {
                ConsoleKey(
                    text = "H",
                    onClick = onToggleHydrogens,
                    contentDescription = "Hydrogen atoms",
                    active = showHydrogens,
                    stateDescription = if (showHydrogens) "shown" else "hidden",
                )
                Spacer(Modifier.width(8.dp))
            }
            ConsoleKey(
                // "SHR" as in the design; "..." while the capture is being prepared (the key is off: no double taps).
                text = if (share is ShareStatus.Working) "..." else "SHR",
                onClick = onShare,
                contentDescription = "Share an image of the molecule",
                enabled = share.canStart,
            )
        }
    }
}

/** Phosphor when smooth, amber when it drops under 55, optic red under 30 (the subject's "poor performance"). */
private fun fpsColor(level: FpsText.Level): Color = when (level) {
    FpsText.Level.GOOD -> SwiftyColors.Phosphor
    FpsText.Level.FAIR -> SwiftyColors.Amber
    FpsText.Level.POOR -> SwiftyColors.OpticRed
    FpsText.Level.IDLE -> SwiftyColors.Ash
}

private const val FPS_REFRESH_MILLIS = 250L

/** "PICT-CAPTURE ACTIVE" / "SYSTEM MESSAGE": what the Share key is doing. */
@Composable
private fun ShareStatusPanel(text: String, status: ShareStatus, modifier: Modifier) {
    val failed = status is ShareStatus.Failed
    CogitatorPanel(
        label = when (status) {
            is ShareStatus.Working -> "PICT-CAPTURE ACTIVE"
            is ShareStatus.Failed -> "PICT-CAPTURE FAILED"
            else -> "SYSTEM MESSAGE"
        },
        accent = if (failed) SwiftyColors.OpticRed else SwiftyColors.Brass,
        fill = SwiftyColors.ConsoleOverlay,
        contentPadding = 12.dp,
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium.glowing(if (failed) SwiftyColors.OpticRed else SwiftyColors.Phosphor),
            color = if (failed) SwiftyColors.OpticRed else SwiftyColors.Phosphor,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * "AUSPEX READOUT": what the user is looking at. Nothing selected: the MOLECULE (its name from the file and its
 * formula, bonus VII.5) and how to use the view. An atom: its name, element and X / Y / Z. A bond (bonus VII.3): its
 * two atoms, its order and its length.
 */
@Composable
private fun ReadoutPanel(molecule: Molecule, selection: ScenePick?, hydrogensHidden: Boolean, modifier: Modifier) {
    CogitatorPanel(label = "AUSPEX READOUT", fill = SwiftyColors.ConsoleOverlay, modifier = modifier.fillMaxWidth()) {
        when (selection) {
            null -> {
                Text(
                    molecule.name ?: "Ligand ${molecule.id}",
                    style = MaterialTheme.typography.titleMedium,
                    color = SwiftyColors.Bone,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(formulaText(molecule), style = SwiftyDataText.medium.glowing(SwiftyColors.Gold, 0.3f), color = SwiftyColors.Gold)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (hydrogensHidden) "Hydrogens hidden. Tap an atom or a bond." else "Drag to rotate. Pinch to zoom. Tap an atom or a bond. Double-tap to orbit it.",
                    style = MaterialTheme.typography.bodySmall.glowing(SwiftyColors.Phosphor),
                    color = SwiftyColors.Phosphor,
                )
            }
            is ScenePick.Atom -> {
                val atom = molecule.atoms[selection.index]
                Text(AtomText.title(atom), style = MaterialTheme.typography.titleMedium, color = SwiftyColors.Gold)
                Spacer(Modifier.height(8.dp))
                for ((axis, value) in listOf("X" to atom.x, "Y" to atom.y, "Z" to atom.z)) {
                    ReadoutRow(axis, AtomText.value(value))
                }
            }
            is ScenePick.Bond -> {
                Text(BondText.title(molecule, selection.index), style = MaterialTheme.typography.titleMedium, color = SwiftyColors.Gold)
                Spacer(Modifier.height(8.dp))
                ReadoutRow("Atoms", BondText.elements(molecule, selection.index))
                ReadoutRow("Length", BondText.length(molecule, selection.index))
            }
        }
    }
}

/** One line of the readout: a label on the left (phosphor), the value on the right. */
@Composable
private fun ReadoutRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = SwiftyDataText.medium.glowing(SwiftyColors.Phosphor), color = SwiftyColors.Phosphor, modifier = Modifier.weight(1f))
        Text(value, style = SwiftyDataText.medium, color = SwiftyColors.Bone)
    }
}

/**
 * The formula with real subscripts drawn in OUR font: each count is the same digits, lowered and at 75 % size. Not
 * Unicode subscripts (₁₀): neither of our fonts has those glyphs, and Android would take them from another font in the
 * middle of the formula (document 15).
 */
private fun formulaText(molecule: Molecule): AnnotatedString = buildAnnotatedString {
    for (part in MolecularFormula.of(molecule.atoms)) {
        append(part.element)
        if (part.count > 1) {
            withStyle(SpanStyle(fontSize = 0.75.em, baselineShift = BaselineShift.Subscript)) { append(part.count.toString()) }
        }
    }
}

/**
 * "ELEMENTS / CPK KEY": the colour of each element that is drawn, and the MODEL key (bonus VII.1). Tapping an element
 * highlights every atom of it (bonus VII.3); tapping it again, or another one, changes that. The letter is always
 * written: the colour never carries the meaning alone.
 */
@Composable
private fun ElementKey(
    elements: List<String>,
    highlighted: String?,
    onToggle: (String) -> Unit,
    representation: Representation,
    onNextRepresentation: () -> Unit,
    modifier: Modifier,
) {
    val sounds = LocalSounds.current
    CogitatorPanel(fill = SwiftyColors.ConsoleOverlay, contentPadding = 10.dp, scanlines = false, modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Elements / CPK key", style = MaterialTheme.typography.labelSmall, color = SwiftyColors.Ash, modifier = Modifier.weight(1f))
            ConsoleKey(
                text = representation.label.uppercase(),
                onClick = onNextRepresentation,
                contentDescription = "Model: ${representation.label}. Change the model",
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            for (element in elements) {
                val on = element == highlighted
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clickable(onClickLabel = if (on) "Stop highlighting" else "Highlight every atom of this element") {
                            sounds.play(SoundCue.KEY)
                            onToggle(element)
                        }
                        .semantics {
                            contentDescription = ElementStyle.nameOf(element)
                            stateDescription = if (on) "highlighted" else "not highlighted"
                        }
                        .border(if (on) 1.5.dp else 0.dp, if (on) SwiftyColors.Gold else Color.Transparent, CutCornerShape(4.dp))
                        .padding(horizontal = 8.dp),
                ) {
                    Box(Modifier.size(10.dp).background(cpkColor(element), CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text(element, style = SwiftyDataText.small, color = if (on) SwiftyColors.Gold else SwiftyColors.Bone)
                }
            }
        }
    }
}

/** The same colour the scene paints the element with (ElementStyle), as a Compose colour. */
private fun cpkColor(element: String): Color {
    val rgb = ElementStyle.colorOf(element)
    return Color((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF)
}
