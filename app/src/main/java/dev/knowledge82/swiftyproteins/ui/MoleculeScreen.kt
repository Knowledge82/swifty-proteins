package dev.knowledge82.swiftyproteins.ui

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import dev.knowledge82.swiftyproteins.R
import dev.knowledge82.swiftyproteins.molecule.Atom
import dev.knowledge82.swiftyproteins.molecule.Molecule
import dev.knowledge82.swiftyproteins.scene.ElementLegend
import dev.knowledge82.swiftyproteins.scene.ElementStyle
import dev.knowledge82.swiftyproteins.share.CaptureException
import dev.knowledge82.swiftyproteins.share.CaptureFailure
import dev.knowledge82.swiftyproteins.share.MoleculeSharer
import dev.knowledge82.swiftyproteins.share.ShareStatus
import dev.knowledge82.swiftyproteins.ui.theme.SwiftyColors
import dev.knowledge82.swiftyproteins.ui.theme.SwiftyDataText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The screen of one molecule (design screens 15, 16 and 18): the 3D scene full size, a bar on top (back, name,
 * hydrogens on/off), the colour key and a card at the bottom that says what the tapped atom is.
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

    Box(modifier = modifier.fillMaxSize()) {
        MoleculeScene(
            molecule = molecule,
            showHydrogens = showHydrogens,
            selected = if (atom == null) null else selected,
            onSelect = { selected = it },
            modifier = Modifier.fillMaxSize(),
        )

        TopBar(
            molecule = molecule,
            hasHydrogens = hasHydrogens,
            showHydrogens = showHydrogens,
            onToggleHydrogens = { showHydrogens = !showHydrogens },
            onBack = onBack,
            shareEnabled = share.canStart,
            onShare = onShare,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp)) {
            share.label?.let { text ->
                ShareStatusLine(text = text, isError = share is ShareStatus.Failed)
                Spacer(Modifier.height(10.dp))
            }
            if (legend.isNotEmpty()) {
                SectionLabel("Elements / CPK")
                Spacer(Modifier.height(6.dp))
                ElementKey(legend)
                Spacer(Modifier.height(10.dp))
            }
            AtomCard(atom = atom, hydrogensHidden = hasHydrogens && !showHydrogens)
        }
    }
}

/** Semi-opaque iron bar (96 %, no blur: blur cannot reach the GPU surface below) with a brass seam under it. */
@Composable
private fun TopBar(
    molecule: Molecule,
    hasHydrogens: Boolean,
    showHydrogens: Boolean,
    onToggleHydrogens: () -> Unit,
    onBack: () -> Unit,
    shareEnabled: Boolean,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Surface(modifier = Modifier.fillMaxWidth(), color = SwiftyColors.IronOverlay) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconPlateButton(icon = R.drawable.ic_back, contentDescription = "Back to the list", onClick = onBack)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    SectionLabel("Ligand ${molecule.id}", color = SwiftyColors.Gold)
                    Text(
                        text = "${molecule.id} · ${molecule.atoms.size} atoms · ${molecule.bonds.size} bonds",
                        style = MaterialTheme.typography.titleMedium,
                        color = SwiftyColors.Bone,
                        maxLines = 1,
                    )
                }
                if (hasHydrogens) {
                    Spacer(Modifier.width(8.dp))
                    HydrogenChip(on = showHydrogens, onToggle = onToggleHydrogens)
                }
                Spacer(Modifier.width(8.dp))
                IconPlateButton(
                    icon = R.drawable.ic_share,
                    contentDescription = "Share an image of the molecule",
                    onClick = onShare,
                    enabled = shareEnabled,
                )
            }
        }
        Canvas(Modifier.fillMaxWidth().height(1.dp)) {
            drawLine(SwiftyColors.Brass, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
        }
    }
}

/** "● C   ● N   ● O   ● H": the CPK colour of each element that is drawn. The letter is always written. */
@Composable
private fun ElementKey(elements: List<String>) {
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

/** The bottom card: help, "hydrogens hidden", or the selected atom with its coordinates. */
@Composable
private fun AtomCard(atom: Atom?, hydrogensHidden: Boolean) {
    SwiftyPlate(modifier = Modifier.fillMaxWidth(), cut = 12, fill = SwiftyColors.IronOverlay, border = SwiftyColors.Rivet) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (atom == null) {
                SectionLabel("Inspection", color = SwiftyColors.Gold)
                Spacer(Modifier.height(8.dp))
                if (hydrogensHidden) {
                    Text("Hydrogens hidden", style = MaterialTheme.typography.titleMedium, color = SwiftyColors.Bone)
                    Text("Tap H to show hydrogen atoms again.", style = MaterialTheme.typography.bodySmall, color = SwiftyColors.Ash)
                } else {
                    Text("Tap an atom to inspect it", style = MaterialTheme.typography.titleMedium, color = SwiftyColors.Bone)
                    Text("Drag to rotate, pinch to zoom, tap an atom.", style = MaterialTheme.typography.bodySmall, color = SwiftyColors.Ash)
                }
            } else {
                SectionLabel("Selected atom", color = SwiftyColors.Gold)
                Spacer(Modifier.height(4.dp))
                Text(AtomText.title(atom), style = MaterialTheme.typography.titleLarge, color = SwiftyColors.Bone)
                SeamDivider()
                // Machine data: green monospace. Three columns so the numbers do not jump when they change.
                Row(modifier = Modifier.fillMaxWidth()) {
                    for ((axis, value) in listOf("X" to atom.x, "Y" to atom.y, "Z" to atom.z)) {
                        Text(AtomText.coordinate(axis, value), style = SwiftyDataText.small, color = SwiftyColors.Phosphor, modifier = Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("Tap another atom to inspect it.", style = MaterialTheme.typography.bodySmall, color = SwiftyColors.Ash)
            }
        }
    }
}

/** The same colour the scene paints the element with (ElementStyle), as a Compose colour. */
private fun cpkColor(element: String): Color {
    val rgb = ElementStyle.colorOf(element)
    return Color((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF)
}

/** "Preparing image…" / "Image ready" / the reason of a failure: a small plate above the legend. */
@Composable
private fun ShareStatusLine(text: String, isError: Boolean) {
    SwiftyPlate(cut = 6, fill = SwiftyColors.IronOverlay, border = if (isError) SwiftyColors.Error else SwiftyColors.Brass) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = if (isError) SwiftyColors.Error else SwiftyColors.Bone,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}
