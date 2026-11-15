package dev.knowledge82.spike3d

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.github.sceneview.SceneView
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.sqrt

private const val RAD_TO_DEG = (180.0 / PI).toFloat()

// Two angles (degrees) that orient a cylinder, vertical by default (axis Y), along a direction:
//   1) tilt: lean it away from the vertical, rotating around Z
//   2) spin: turn the whole thing around the vertical axis Y
// They are applied by two NESTED nodes, so the order of the rotations is fixed by the nesting.
private class BondAngles(val tilt: Float, val spin: Float)

private fun bondAngles(dx: Float, dy: Float, dz: Float): BondAngles {
    val len = sqrt(dx * dx + dy * dy + dz * dz)
    val cosTilt = (dy / len).coerceIn(-1f, 1f)   // protects acos from 1.0000001
    return BondAngles(
        tilt = acos(cosTilt) * RAD_TO_DEG,
        spin = atan2(dz, -dx) * RAD_TO_DEG,
    )
}

// Atoms bonded to the central one (at the origin).
// First three: on the axes (already worked). The rest: diagonals, the real test.
private val tips = listOf(
    Position(1.5f, 0f, 0f),
    Position(0f, 1.5f, 0f),
    Position(0f, 0f, 1.5f),
    Position(1f, 1f, 1f),
    Position(1f, 1f, 0f),          // sign test: the stick must go up and to the RIGHT
    Position(-1f, -1.2f, 0.8f),
    Position(0f, -1.5f, 0f),       // edge case: direction exactly -Y
)

@Composable
fun MoleculeScene(modifier: Modifier = Modifier) {
    val engine = rememberEngine()
    val materialLoader = rememberMaterialLoader(engine)

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
        cameraManipulator = rememberCameraManipulator(
            orbitRadius = 9.5f,
            targetPosition = Position(0f, 0f, 0f),
        ),
    ) {
        // Central atom.
        SphereNode(
            radius = 0.4f, stacks = 32, slices = 32,
            materialInstance = gray,
        )

        for (tip in tips) {
            val length = sqrt(tip.x * tip.x + tip.y * tip.y + tip.z * tip.z)
            val a = bondAngles(tip.x, tip.y, tip.z)

            SphereNode(
                radius = 0.4f, stacks = 32, slices = 32,
                materialInstance = red,
                position = tip,
            )

            // Pivot node at the midpoint of the bond: it turns around Y by `spin`.
            Node(
                position = Position(tip.x / 2f, tip.y / 2f, tip.z / 2f),
                rotation = Rotation(y = a.spin),
            ) {
                // Child cylinder: centered on the pivot, leaned by `tilt` around Z.
                CylinderNode(
                    radius = 0.12f,
                    height = length,
                    rotation = Rotation(z = a.tilt),
                    materialInstance = stick,
                )
            }
        }
    }
}
