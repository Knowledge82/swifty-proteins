package dev.knowledge82.spike3d

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.github.sceneview.SceneView
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation

// SceneView imports are NOT written by hand: put the cursor on each red name and press
// Alt+Enter, then pick the import Android Studio proposes. Names to import:
//   SceneView, rememberEngine, rememberMaterialLoader, rememberCameraManipulator, SphereNode
// If Studio offers several "Color" options, choose androidx.compose.ui.graphics.Color
// (already imported above).

@Composable
fun MoleculeScene(modifier: Modifier = Modifier) {
    // The engine lives as long as this composable: it is released automatically on leave.
    val engine = rememberEngine()
    val materialLoader = rememberMaterialLoader(engine)

    // One material for the whole lifetime of the screen (remember), not one per redraw.
    val gray = remember(materialLoader) {
        materialLoader.createColorInstance(Color(0.56f, 0.56f, 0.56f, 1f))
    }

    val red = remember(materialLoader) {
        materialLoader.createColorInstance(Color(1f, 0.05f, 0.05f, 1f))
    }
    val stick = remember(materialLoader) {
        materialLoader.createColorInstance(Color(0.7f, 0.7f, 0.7f, 1f))
    }

    SceneView(
        modifier = modifier.fillMaxSize(),
        engine = engine,
        cameraManipulator = rememberCameraManipulator(),
    ) {
        SphereNode(radius = 0.4f, stacks = 32, slices = 32, materialInstance = gray)
        SphereNode(radius = 0.4f, stacks = 32, slices = 32,
            position = Position(x = 1.5f), materialInstance = red)
        SphereNode(radius = 0.4f, stacks = 32, slices = 32,
            position = Position(y = 1.5f), materialInstance = red)
        SphereNode(radius = 0.4f, stacks = 32, slices = 32,
            position = Position(z = 1.5f), materialInstance = red)

        CylinderNode(radius = 0.12f, height = 1.5f,
            position = Position(x = 0.75f), rotation = Rotation(z = 90f),
            materialInstance = stick)
        CylinderNode(radius = 0.12f, height = 1.5f,
            position = Position(y = 0.75f), materialInstance = stick)
        CylinderNode(radius = 0.12f, height = 1.5f,
            position = Position(z = 0.75f), rotation = Rotation(x = 90f),
            materialInstance = stick)
    }
}
