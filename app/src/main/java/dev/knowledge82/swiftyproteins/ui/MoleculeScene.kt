package dev.knowledge82.swiftyproteins.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.fillMaxSize
import com.google.android.filament.LightManager
import com.google.android.filament.MaterialInstance
import dev.knowledge82.swiftyproteins.molecule.Molecule
import dev.knowledge82.swiftyproteins.scene.BondGeometry
import dev.knowledge82.swiftyproteins.scene.ElementStyle
import dev.knowledge82.swiftyproteins.scene.SceneFraming
import io.github.sceneview.SceneView
import io.github.sceneview.collision.Sphere
import io.github.sceneview.math.Direction
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.createDefaultCameraManipulator
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberFillLightNode
import io.github.sceneview.rememberMainLightNode
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

// The selection shell: 1.5 times the atom, 35 % opaque (the atom inside stays visible with its real colour).
private const val HALO_SCALE = 1.5f
private const val HALO_ALPHA = 0.35f

// A finger is much wider than a hydrogen (radius 0.25): the area that answers to a tap is never smaller than this.
private const val MIN_TOUCH_RADIUS = 0.35f

// LIGHTING (subject VI.4: "lighting should highlight the 3D structure ... consider multiple light sources").
// Three directional lights + the image-based ambient light (IBL) SceneView adds by default. A Direction is where the
// light TRAVELS, in world space; the camera starts in front (+z), slightly above. Values in lux, like SceneView's own
// defaults (main 10 000, fill 3 000, IBL 10 000). The lights stay put while the camera orbits, as in a real room.
// KEY: the main light, from upper-front-left: lights the faces the user sees first and gives each ball its shading.
private val KEY_LIGHT_DIRECTION = Direction(0.45f, -0.65f, -0.6f)
private const val KEY_LIGHT_LUX = 10_000f
// FILL: weaker, from the front-right and a little below: lifts the dark side, so no ball turns into a flat black disc.
private val FILL_LIGHT_DIRECTION = Direction(-0.6f, 0.15f, -0.8f)
private const val FILL_LIGHT_LUX = 3_000f
// RIM: from behind and above, toward the camera: a bright edge on the outline separates the balls from the black.
private val RIM_LIGHT_DIRECTION = Direction(0f, -0.4f, 0.9f)
private const val RIM_LIGHT_LUX = 5_000f

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
 * @param onFrame       called once per frame that really reached the screen (the FPS counter); not called while the
 *                      scene is idle (SceneView draws on demand)
 */
@Composable
fun MoleculeScene(
    molecule: Molecule,
    showHydrogens: Boolean,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    onFrame: (Long) -> Unit = {},
) {
    val engine = rememberEngine()
    val materialLoader = rememberMaterialLoader(engine)

    // One material per ELEMENT, created the first time it is needed (a ligand has a handful of elements, not 256 colors).
    val materials = remember(materialLoader) { HashMap<String, MaterialInstance>() }
    fun materialOf(element: String): MaterialInstance =
        materials.getOrPut(element) { materialLoader.createColorInstance(colorOf(element)) }

    val stick = remember(materialLoader) { materialLoader.createColorInstance(Color(0.7f, 0.7f, 0.7f, 1f)) }
    // SELECTION: the atom keeps its CPK colour (recolouring it would lie about the element: yellow is sulfur, green is
    // chlorine...). Instead a translucent phosphor-green shell is drawn AROUND it. UNLIT: a glow, not an object, so the
    // lights do not shade it; alpha < 1 makes SceneView pick its transparent material.
    val halo = remember(materialLoader) { materialLoader.createUnlitColorInstance(Color(0.26f, 0.88f, 0.48f, HALO_ALPHA)) }

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
        // Written out (SceneView would add a main and a fill light anyway) so the lighting is OURS and visible here.
        mainLightNode = rememberMainLightNode(engine) {
            lightDirection = KEY_LIGHT_DIRECTION
            intensity = KEY_LIGHT_LUX
        },
        fillLightNode = rememberFillLightNode(engine) {
            lightDirection = FILL_LIGHT_DIRECTION
            intensity = FILL_LIGHT_LUX
        },
        onFrame = onFrame,
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
        // The third light (SceneView only has parameters for two): the rim, declared like any other node.
        LightNode(type = LightManager.Type.DIRECTIONAL, intensity = RIM_LIGHT_LUX, direction = RIM_LIGHT_DIRECTION)

        for ((i, atom) in molecule.atoms.withIndex()) {
            if (showHydrogens || atom.element != "H") {
                // key(i): each atom keeps its own identity when the list of drawn nodes changes (hydrogens hidden or shown).
                key(i) {
                    val radius = ElementStyle.radiusOf(atom.element)
                    val detail = if (atom.element == "H") HYDROGEN_BALL_DETAIL else BALL_DETAIL
                    SphereNode(
                        radius = radius, stacks = detail, slices = detail,
                        materialInstance = materialOf(atom.element),
                        position = Position(atom.x, atom.y, atom.z),
                        apply = {
                            this.name = "$ATOM_PREFIX$i"
                            this.collisionShape = Sphere(max(radius, MIN_TOUCH_RADIUS))
                        },
                    )
                }
            }
        }

        // The halo of the selected atom (same rules as the atom: a hidden hydrogen has no halo).
        val chosen = selected?.let { molecule.atoms.getOrNull(it) }?.takeIf { showHydrogens || it.element != "H" }
        if (chosen != null) {
            key(Int.MIN_VALUE) {   // a key no atom (>= 0) or bond (< 0, but > MIN_VALUE) can have
                SphereNode(
                    radius = ElementStyle.radiusOf(chosen.element) * HALO_SCALE,
                    stacks = BALL_DETAIL, slices = BALL_DETAIL,
                    materialInstance = halo,
                    position = Position(chosen.x, chosen.y, chosen.z),
                    // NOT touchable: it is bigger than the atom, and a tap on it must reach the atom (or the empty
                    // space) behind, not count as "a tap on nothing" that would clear the selection.
                    apply = { this.isTouchable = false },
                )
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
