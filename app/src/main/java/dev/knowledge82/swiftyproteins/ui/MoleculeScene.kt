package dev.knowledge82.swiftyproteins.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.fillMaxSize
import com.google.android.filament.MaterialInstance
import dev.knowledge82.swiftyproteins.molecule.Molecule
import dev.knowledge82.swiftyproteins.scene.BondGeometry
import dev.knowledge82.swiftyproteins.scene.ElementStyle
import dev.knowledge82.swiftyproteins.scene.SceneFraming
import io.github.sceneview.SceneView
import io.github.sceneview.collision.Sphere
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.createDefaultCameraManipulator
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberOnGestureListener
import kotlin.math.max

// Node names are how a TAP is traced back to what was drawn: "atom 12" is atoms[12]; "bond 3" is a stick.
private const val ATOM_PREFIX = "atom "
private const val BOND_PREFIX = "bond "

private const val BOND_RADIUS = 0.12f

// Mesh detail of a ball (how many bands it is cut into). The spike used 32; with up to 256 atoms (and more than half of
// them hydrogens) a lighter ball is used, and the smaller hydrogens need even less. ESTIMATED, not measured on the A33.
private const val BALL_DETAIL = 20
private const val HYDROGEN_BALL_DETAIL = 12

// A finger is much wider than a hydrogen (radius 0.25): the area that answers to a tap is never smaller than this.
private const val MIN_TOUCH_RADIUS = 0.35f

private fun colorOf(element: String): Color {
    val rgb = ElementStyle.colorOf(element)
    return Color((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF)
}

/**
 * The 3D view of a molecule: balls (atoms) and sticks (bonds), colored by element, with a camera that orbits.
 *
 * It decides NOTHING by itself: colors, radii, where each stick goes and how far the camera starts come from the
 * pure classes in `scene/` (tested on the JVM). This function only turns those numbers into SceneView nodes.
 *
 * @param showHydrogens when `false`, hydrogens and the sticks that touch them are not drawn (the numbering of the rest does not change)
 * @param selected      index in `molecule.atoms` of the highlighted atom, or `null`
 * @param onSelect      called with the tapped atom, or `null` when the tap hit a stick or empty space
 */
@Composable
fun MoleculeScene(
    molecule: Molecule,
    showHydrogens: Boolean,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val engine = rememberEngine()
    val materialLoader = rememberMaterialLoader(engine)

    // One material per ELEMENT, created the first time it is needed (a ligand has a handful of elements, not 256 colors).
    val materials = remember(materialLoader) { HashMap<String, MaterialInstance>() }
    fun materialOf(element: String): MaterialInstance =
        materials.getOrPut(element) { materialLoader.createColorInstance(colorOf(element)) }

    val stick = remember(materialLoader) { materialLoader.createColorInstance(Color(0.7f, 0.7f, 0.7f, 1f)) }
    // Selection highlight: yellow stands out against grey, red, blue and white alike.
    val highlight = remember(materialLoader) { materialLoader.createColorInstance(Color(1f, 0.9f, 0.1f, 1f)) }

    // Both depend only on the molecule: computed once, not at every recomposition (and every tap recomposes).
    val distance = remember(molecule) { SceneFraming.cameraDistance(SceneFraming.radiusOf(molecule)) }
    val poses = remember(molecule) {
        molecule.bonds.map { BondGeometry.poseOf(molecule.atoms[it.from], molecule.atoms[it.to]) }
    }

    // The gesture listener is created once and remembers the FIRST lambda it receives; this always calls the latest.
    val currentOnSelect by rememberUpdatedState(onSelect)

    SceneView(
        modifier = modifier.fillMaxSize(),
        engine = engine,
        cameraManipulator = rememberCameraManipulator(
            orbitRadius = distance,
            targetPosition = Position(0f, 0f, 0f),
            // The default manipulator limits the zoom as a fraction of the starting distance, which for a big ligand
            // means "never closer than 34 angstrom". Here the limits are in angstrom, the same for every molecule.
            creator = {
                createDefaultCameraManipulator(orbitRadius = distance, targetPosition = Position(0f, 0f, 0f)).apply {
                    minZoomDistanceFactor = SceneFraming.minZoomFactor(distance)
                    maxZoomDistanceFactor = SceneFraming.MAX_ZOOM_OUT_FACTOR
                }
            },
        ),
        onGestureListener = rememberOnGestureListener(
            onSingleTapConfirmed = { _, node ->
                // "atom 3" -> 3. A stick ("bond 2"), empty space or a node without name gives null: nothing selected.
                currentOnSelect(node?.name?.removePrefix(ATOM_PREFIX)?.toIntOrNull())
            },
        ),
    ) {
        for ((i, atom) in molecule.atoms.withIndex()) {
            if (showHydrogens || atom.element != "H") {
                // key(i): each atom keeps its own identity when the list of drawn nodes changes (hydrogens hidden or shown).
                key(i) {
                    val radius = ElementStyle.radiusOf(atom.element)
                    val detail = if (atom.element == "H") HYDROGEN_BALL_DETAIL else BALL_DETAIL
                    SphereNode(
                        radius = radius, stacks = detail, slices = detail,
                        materialInstance = if (selected == i) highlight else materialOf(atom.element),
                        position = Position(atom.x, atom.y, atom.z),
                        apply = {
                            this.name = "$ATOM_PREFIX$i"
                            this.collisionShape = Sphere(max(radius, MIN_TOUCH_RADIUS))
                        },
                    )
                }
            }
        }

        for ((k, bond) in molecule.bonds.withIndex()) {
            val pose = poses[k]
            val touchesHydrogen = molecule.atoms[bond.from].element == "H" || molecule.atoms[bond.to].element == "H"
            if (pose != null && (showHydrogens || !touchesHydrogen)) {
                key(-k - 1) {   // negative keys: they can never collide with the atoms' keys
                    // OUTER node: at the midpoint, turned around the vertical axis. INNER: the cylinder, leaned.
                    Node(
                        position = Position(pose.x, pose.y, pose.z),
                        rotation = Rotation(y = pose.spinDegrees),
                    ) {
                        CylinderNode(
                            radius = BOND_RADIUS,
                            height = pose.length,
                            rotation = Rotation(z = pose.tiltDegrees),
                            materialInstance = stick,
                            apply = { this.name = "$BOND_PREFIX$k" },
                        )
                    }
                }
            }
        }
    }
}
