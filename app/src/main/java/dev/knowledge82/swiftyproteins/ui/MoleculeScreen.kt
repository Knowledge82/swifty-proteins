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
import dev.knowledge82.swiftyproteins.molecule.Atom
import dev.knowledge82.swiftyproteins.molecule.Molecule
import dev.knowledge82.swiftyproteins.scene.ElementLegend
import dev.knowledge82.swiftyproteins.scene.ElementStyle
import dev.knowledge82.swiftyproteins.scene.FpsMeter
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
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The system Back button returns to the list, exactly like the button on screen.
    BackHandler(onBack = onBack)

    // rememberSaveable: the selection and the switch survive a rotation of the phone.
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    var showHydrogens by rememberSaveable { mutableStateOf(true) }
    val hasHydrogens = remember(molecule) { molecule.atoms.any { it.element == "H" } }
    val legend = remember(molecule, showHydrogens) { ElementLegend.of(molecule, showHydrogens) }

    // Share: the state of the button and the job. The job lives in the screen's coroutine scope, so leaving the
    // screen cancels a capture in progress (nothing is left half done in the background).
    var share by remember { mutableStateOf<ShareStatus>(ShareStatus.Idle) }

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

    // What is REALLY highlighted: a valid index, and not a hydrogen that is currently hidden.
    val atom = selected
        ?.takeIf { it in molecule.atoms.indices }
        ?.let { molecule.atoms[it] }
        ?.takeIf { showHydrogens || it.element != "H" }

    // The subject (VI.4): the atom information disappears when the user taps ANYWHERE else. A tap on the empty scene or
    // on another atom already does it (MoleculeScene); this makes a tap on a PANEL do it too. Only on the panels: the
    // middle of the screen must keep sending every touch to the molecule. The keys inside the panels (H, SHR, back)
    // keep their own action: they take the tap first. No ripple; TalkBack announces the action.
    val deselect = Modifier.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        enabled = atom != null,
        onClickLabel = "Hide the atom information",
    ) { selected = null }

    Box(modifier = modifier.fillMaxSize()) {
        MoleculeScene(
            molecule = molecule,
            showHydrogens = showHydrogens,
            selected = if (atom == null) null else selected,
            onSelect = { selected = it },
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
                onToggleHydrogens = { showHydrogens = !showHydrogens },
                onBack = onBack,
                share = share,
                onShare = onShare,
                fps = fps,
            )
            CornerBrackets(Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 14.dp))
            share.label?.let { text ->
                ShareStatusPanel(text = text, status = share, modifier = deselect)
                Spacer(Modifier.height(14.dp))
            }
            ReadoutPanel(atom = atom, hydrogensHidden = hasHydrogens && !showHydrogens, modifier = deselect)
            if (legend.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                ElementKey(legend, modifier = deselect)
            }
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
) {
    CogitatorPanel(label = "AUSPEX // RUNNING", fill = SwiftyColors.ConsoleOverlay, contentPadding = 8.dp, modifier = modifier.fillMaxWidth()) {
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

/** "AUSPEX READOUT": help, "hydrogens hidden", or the selected atom with X / Y / Z in two columns. */
@Composable
private fun ReadoutPanel(atom: Atom?, hydrogensHidden: Boolean, modifier: Modifier) {
    CogitatorPanel(label = "AUSPEX READOUT", fill = SwiftyColors.ConsoleOverlay, modifier = modifier.fillMaxWidth()) {
        if (atom == null) {
            Text(
                if (hydrogensHidden) "Hydrogens hidden" else "No atom selected",
                style = MaterialTheme.typography.titleMedium,
                color = SwiftyColors.Bone,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (hydrogensHidden) "Tap an atom to inspect it." else "Drag to rotate. Pinch to zoom. Tap an atom.",
                style = MaterialTheme.typography.bodySmall.glowing(SwiftyColors.Phosphor),
                color = SwiftyColors.Phosphor,
            )
        } else {
            Text(AtomText.title(atom), style = MaterialTheme.typography.titleMedium, color = SwiftyColors.Gold)
            Spacer(Modifier.height(8.dp))
            for ((axis, value) in listOf("X" to atom.x, "Y" to atom.y, "Z" to atom.z)) {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(axis, style = SwiftyDataText.medium.glowing(SwiftyColors.Phosphor), color = SwiftyColors.Phosphor, modifier = Modifier.weight(1f))
                    Text(AtomText.value(value), style = SwiftyDataText.medium, color = SwiftyColors.Bone)
                }
            }
        }
    }
}

/** "ELEMENTS / CPK KEY": the colour of each element that is drawn. The letter is always written. */
@Composable
private fun ElementKey(elements: List<String>, modifier: Modifier) {
    CogitatorPanel(fill = SwiftyColors.ConsoleOverlay, contentPadding = 10.dp, scanlines = false, modifier = modifier.fillMaxWidth()) {
        Text("Elements / CPK key", style = MaterialTheme.typography.labelSmall, color = SwiftyColors.Ash)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            for (element in elements) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(cpkColor(element), CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text(element, style = SwiftyDataText.small, color = SwiftyColors.Bone)
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
