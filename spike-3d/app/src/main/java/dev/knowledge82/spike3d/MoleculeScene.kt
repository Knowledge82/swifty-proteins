package dev.knowledge82.spike3d

import android.util.Log
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.github.sceneview.SceneView
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Size
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberOnGestureListener
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

// Atoms bonded to the central one. The central atom is atom 0, these are atoms 1..7.
private val tips = listOf(
    Position(1.5f, 0f, 0f),
    Position(0f, 1.5f, 0f),
    Position(0f, 0f, 1.5f),
    Position(1f, 1f, 1f),
    Position(1f, 1f, 0f),
    Position(-1f, -1.2f, 0.8f),
    Position(0f, -1.5f, 0f),
)

private const val ATOM_PREFIX = "atom "

// Debug only: draws a translucent green cube around every atom, about the size of the box
// SceneView uses to decide what a tap hits. Set to false (or delete the cubes) when done.
private const val DEBUG_HITBOX = false
private const val ATOM_RADIUS = 0.4f

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
    // Selection highlight: yellow stands out against both gray and red.
    val highlight = remember(materialLoader) {
        materialLoader.createColorInstance(Color(1f, 0.9f, 0.1f, 1f))
    }

    // Translucent unlit green (alpha < 1 makes SceneView pick its transparent material).
    val hitbox = remember(materialLoader) {
        materialLoader.createUnlitColorInstance(Color(0f, 1f, 0.2f, 0.25f))
    }

    // Index of the selected atom, or null when nothing is selected.
    var selected by remember { mutableStateOf<Int?>(null) }

    SceneView(
        modifier = modifier.fillMaxSize(),
        engine = engine,
        cameraManipulator = rememberCameraManipulator(
            orbitRadius = 9.5f,
            targetPosition = Position(0f, 0f, 0f),
        ),
        onGestureListener = rememberOnGestureListener(
            onSingleTapConfirmed = { _, node ->
                // Node names look like "atom 3" or "bond 2". Anything else (a bond, empty
                // space, a node without a name) gives null and clears the selection.
                selected = node?.name?.removePrefix(ATOM_PREFIX)?.toIntOrNull()
                Log.d("Spike3D", "tap -> ${node?.name} -> selected=$selected")
            },
        ),
    ) {
        // Central atom (atom 0).
        SphereNode(
            radius = ATOM_RADIUS, stacks = 32, slices = 32,
            materialInstance = if (selected == 0) highlight else gray,
            apply = { this.name = "${ATOM_PREFIX}0" },
        )
        if (DEBUG_HITBOX) {
            CubeNode(
                size = Size(2f * ATOM_RADIUS),
                materialInstance = hitbox,
                apply = { isHittable = false; isTouchable = false },
            )
        }

        for ((i, tip) in tips.withIndex()) {
            val id = i + 1
            val length = sqrt(tip.x * tip.x + tip.y * tip.y + tip.z * tip.z)
            val a = bondAngles(tip.x, tip.y, tip.z)

            SphereNode(
                radius = ATOM_RADIUS, stacks = 32, slices = 32,
                materialInstance = if (selected == id) highlight else red,
                position = tip,
                apply = { this.name = "$ATOM_PREFIX$id" },
            )
            if (DEBUG_HITBOX) {
                CubeNode(
                    size = Size(2f * ATOM_RADIUS),
                    materialInstance = hitbox,
                    position = tip,
                    apply = { isHittable = false; isTouchable = false },
                )
            }

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
                    apply = { this.name = "bond $id" },
                )
            }
        }
    }
}
