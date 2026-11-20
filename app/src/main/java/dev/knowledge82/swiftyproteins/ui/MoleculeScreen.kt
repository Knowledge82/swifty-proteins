package dev.knowledge82.swiftyproteins.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.knowledge82.swiftyproteins.molecule.Molecule
import dev.knowledge82.swiftyproteins.scene.ElementStyle
import java.util.Locale

/**
 * The screen of one molecule: the 3D scene full size, a bar on top (back, name, hydrogens on/off) and, when an atom is
 * tapped, a card at the bottom that says what it is.
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

        // Semi-transparent so the text is readable on any background and the molecule is still seen behind it.
        Surface(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) { Text("Back") }
                Text(
                    text = "${molecule.id} · ${molecule.atoms.size} atoms · ${molecule.bonds.size} bonds",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                if (hasHydrogens) {
                    FilterChip(
                        selected = showHydrogens,
                        onClick = { showHydrogens = !showHydrogens },
                        label = { Text("H") },
                    )
                }
            }
        }

        Card(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (atom == null) {
                    Text("Tap an atom to see what it is. Drag to rotate, pinch to zoom.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(
                        text = "${atom.name} · ${ElementStyle.nameOf(atom.element)} (${atom.element})",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        // Locale.US: always "1.234", never "1,234".
                        text = String.format(Locale.US, "x %.3f   y %.3f   z %.3f  (angstrom)", atom.x, atom.y, atom.z),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
