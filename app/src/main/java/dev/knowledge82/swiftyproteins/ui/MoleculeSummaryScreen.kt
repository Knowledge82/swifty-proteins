package dev.knowledge82.swiftyproteins.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.knowledge82.swiftyproteins.molecule.Molecule
import java.util.Locale

/**
 * TEMPORARY screen: it shows the numbers of the molecule that the parser produced, as text.
 * It proves that the whole chain (list -> download -> parse) works on the device with real data.
 * The next step replaces it with the 3D scene that draws the same [Molecule].
 */
@Composable
fun MoleculeSummaryScreen(
    molecule: Molecule,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The system Back button returns to the list, exactly like the button on screen.
    BackHandler(onBack = onBack)

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = "Ligand ${molecule.id}",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
        )
        Text(
            text = "${molecule.atoms.size} atoms, ${molecule.bonds.size} bonds",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Button(onClick = onBack, modifier = Modifier.padding(16.dp)) {
            Text("Back to the list")
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(molecule.atoms) { index, atom ->
                Text(
                    // Locale.US: always "1.234", never "1,234" (the device language must not change the text).
                    text = String.format(
                        Locale.US, "%3d  %-5s %-2s %8.3f %8.3f %8.3f",
                        index, atom.name, atom.element, atom.x, atom.y, atom.z,
                    ),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                )
            }
        }
    }
}
