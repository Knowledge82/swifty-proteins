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
import dev.knowledge82.swiftyproteins.scene.BondPose
import dev.knowledge82.swiftyproteins.scene.ElementStyle
import dev.knowledge82.swiftyproteins.scene.Representation
import dev.knowledge82.swiftyproteins.scene.SceneFraming
import dev.knowledge82.swiftyproteins.scene.ScenePick
import io.github.sceneview.SceneScope
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

// Node names are how a TAP is traced back to what was drawn ("atom 12", "bond 3"): see ScenePick.

// Mesh detail of a ball (how many bands it is cut into). The spike used 32; with up to 256 atoms (and more than half of
// them hydrogens) a lighter ball is used, and the smaller hydrogens need even less. ESTIMATED, not measured on the A33.
// Space-filling balls are 4 times bigger on screen: there the facets would show, so they get more bands.
private const val BALL_DETAIL = 20
private const val SMALL_BALL_DETAIL = 12
private const val BIG_BALL_DETAIL = 24

// The halos (document 07 and 15). Selection: phosphor green; atoms of the highlighted element: brass gold, fainter.
private const val SELECTION_ALPHA = 0.35f
private const val ELEMENT_ALPHA = 0.28f
// A bond's halo: a translucent tube around the stick, this many times thicker (and never thinner than an atom's halo).
private const val BOND_HALO_SCALE = 2.2f
// The ruler's line between two measured atoms: thin, phosphor green, more opaque than a halo (it is a line, not a glow).
private const val RULER_LINE_RADIUS = 0.06f
private const val RULER_LINE_ALPHA = 0.7f

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
 * The 3D view of a molecule, in one of four models (bonus VII.1), coloured by element, with a camera that orbits the
 * whole molecule or one atom (bonus VII.3).
 *
 * It decides NOTHING by itself: colours, radii, where each stick goes, how far the camera is and what a tap hit come
 * from the pure classes in `scene/` (tested on the JVM). This function only turns those answers into SceneView nodes.
 *
 * @param representation   the model; changing it redraws the same molecule (nothing is downloaded or parsed again)
 * @param showHydrogens    when `false`, hydrogens and the bonds that touch them are not drawn
 * @param selection        the highlighted atom or bond (already checked by the screen: it exists and is drawn), or `null`
 * @param highlightElement every drawn atom of this element gets a gold halo, or `null`
 * @param focus            index of the atom the camera orbits around, or `null` for the whole molecule
 * @param measured         atoms of the ruler (bonus VII.3, document 16), in tap order: halos and a line between each pair
 * @param onTap            what a single tap hit: an atom, a bond, or `null` (empty space)
 * @param onDoubleTap      the same for a double tap
 * @param onFrame          called once per frame that really reached the screen (the FPS counter)
 */
@Composable
fun MoleculeScene(
    molecule: Molecule,
    representation: Representation,
    showHydrogens: Boolean,
    selection: ScenePick?,
    highlightElement: String?,
    focus: Int?,
    measured: List<Int>,
    onTap: (ScenePick?) -> Unit,
    onDoubleTap: (ScenePick?) -> Unit,
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
    // chlorine...). Instead a translucent shell is drawn AROUND it. UNLIT: a glow, not an object, so the lights do not
    // shade it; alpha < 1 makes SceneView pick its transparent material.
    val selectionHalo = remember(materialLoader) { materialLoader.createUnlitColorInstance(Color(0.26f, 0.88f, 0.48f, SELECTION_ALPHA)) }
    val elementHalo = remember(materialLoader) { materialLoader.createUnlitColorInstance(Color(0.88f, 0.71f, 0.36f, ELEMENT_ALPHA)) }
    val rulerLine = remember(materialLoader) { materialLoader.createUnlitColorInstance(Color(0.26f, 0.88f, 0.48f, RULER_LINE_ALPHA)) }

    // All of this depends only on the molecule (and on the hydrogen switch): computed once, not at every recomposition.
    val wholeDistance = remember(molecule) { SceneFraming.cameraDistance(SceneFraming.radiusOf(molecule)) }
    val poses = remember(molecule) {
        molecule.bonds.map { BondGeometry.poseOf(molecule.atoms[it.from], molecule.atoms[it.to]) }
    }
    val halves = remember(molecule) {
        molecule.bonds.map { BondGeometry.halvesOf(molecule.atoms[it.from], molecule.atoms[it.to]) }
    }
    fun drawn(atom: Int) = showHydrogens || molecule.atoms[atom].element != "H"
    val drawnBonds = remember(molecule, showHydrogens) {
        molecule.bonds.indices.filter { k -> poses[k] != null && drawn(molecule.bonds[k].from) && drawn(molecule.bonds[k].to) }
    }
    // Atoms with at least one DRAWN bond: in sticks and wireframe a lone atom is drawn bigger (Representation).
    val bonded = remember(molecule, showHydrogens) {
        BooleanArray(molecule.atoms.size).also { b -> for (k in drawnBonds) { b[molecule.bonds[k].from] = true; b[molecule.bonds[k].to] = true } }
    }

    // THE CAMERA. Its orbit (the point it turns around, how far, the zoom limits) comes from SceneFraming. A new
    // orbit (a double tap changed the focus) makes SceneView build a new manipulator, and it GLIDES there (~0.6 s).
    // Double-tap zoom, on by default in SceneView, is switched off: here a double tap means "orbit around this".
    val orbit = remember(molecule, focus) {
        SceneFraming.orbit(focus?.let { molecule.atoms.getOrNull(it) }, wholeDistance)
    }
    val target = Position(orbit.x, orbit.y, orbit.z)

    // The gesture listener is created once and remembers the FIRST lambdas it receives; these always call the latest.
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)

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
            orbitRadius = orbit.distance,
            targetPosition = target,
            // The default manipulator limits the zoom as a fraction of the starting distance; SceneFraming gives the
            // fractions that keep the limits in angstrom (document 07).
            creator = {
                createDefaultCameraManipulator(orbitRadius = orbit.distance, targetPosition = target).apply {
                    minZoomDistanceFactor = orbit.minZoomFactor
                    maxZoomDistanceFactor = orbit.maxZoomFactor
                    isDoubleTapZoomEnabled = false
                }
            },
        ),
        onGestureListener = rememberOnGestureListener(
            // A single tap is reported only once Android is sure it is not the first half of a double tap.
            onSingleTapConfirmed = { _, node -> currentOnTap(ScenePick.of(node?.name)) },
            onDoubleTap = { _, node -> currentOnDoubleTap(ScenePick.of(node?.name)) },
        ),
    ) {
        // The third light (SceneView only has parameters for two): the rim, declared like any other node.
        LightNode(type = LightManager.Type.DIRECTIONAL, intensity = RIM_LIGHT_LUX, direction = RIM_LIGHT_DIRECTION)

        for ((i, atom) in molecule.atoms.withIndex()) {
            if (drawn(i)) {
                val radius = representation.atomRadius(atom.element, bonded[i])
                // key: each atom keeps its identity when the list of nodes changes (hydrogens hidden or shown); the
                // model and the radius are part of it because the TOUCH shape is only set when the node is created
                // (`apply`): a node reused with a new radius would keep answering taps with the old size.
                key(i, representation, bonded[i]) {
                    val detail = when {
                        representation == Representation.SPACE_FILLING -> BIG_BALL_DETAIL
                        radius < 0.3f -> SMALL_BALL_DETAIL
                        else -> BALL_DETAIL
                    }
                    SphereNode(
                        radius = radius, stacks = detail, slices = detail,
                        materialInstance = materialOf(atom.element),
                        position = Position(atom.x, atom.y, atom.z),
                        apply = {
                            this.name = ScenePick.atomName(i)
                            this.collisionShape = Sphere(max(radius, MIN_TOUCH_RADIUS))
                        },
                    )
                }
            }
        }

        // HALOS. None is touchable: they are bigger than what they surround, and a tap on them must reach the atom (or
        // the empty space) behind, not count as "a tap on nothing" that would clear the selection.
        // Gold: every drawn atom of the highlighted element (bonus VII.3).
        if (highlightElement != null) {
            for ((i, atom) in molecule.atoms.withIndex()) {
                if (atom.element == highlightElement && drawn(i) && (selection as? ScenePick.Atom)?.index != i) {
                    key("element", i, representation, bonded[i]) {
                        SphereNode(
                            radius = representation.haloRadius(atom.element, bonded[i]),
                            stacks = BALL_DETAIL, slices = BALL_DETAIL,
                            materialInstance = elementHalo,
                            position = Position(atom.x, atom.y, atom.z),
                            apply = { this.isTouchable = false },
                        )
                    }
                }
            }
        }
        // Green: the selected atom...
        (selection as? ScenePick.Atom)?.index?.let { i ->
            val atom = molecule.atoms[i]
            key("selected atom", i, representation) {
                SphereNode(
                    radius = representation.haloRadius(atom.element, bonded[i]),
                    stacks = BALL_DETAIL, slices = BALL_DETAIL,
                    materialInstance = selectionHalo,
                    position = Position(atom.x, atom.y, atom.z),
                    apply = { this.isTouchable = false },
                )
            }
        }
        // ...or the selected bond: a translucent tube along it.
        (selection as? ScenePick.Bond)?.index?.let { k ->
            val pose = poses[k]
            if (pose != null) {
                key("selected bond", k, representation) {
                    Node(position = Position(pose.x, pose.y, pose.z), rotation = Rotation(y = pose.spinDegrees)) {
                        CylinderNode(
                            radius = max((representation.bondRadius ?: 0f) * BOND_HALO_SCALE, Representation.WIRE_RADIUS * 6),
                            height = pose.length,
                            rotation = Rotation(z = pose.tiltDegrees),
                            materialInstance = selectionHalo,
                            apply = { this.isTouchable = false },
                        )
                    }
                }
            }
        }

        // THE RULER: a green halo on each measured atom and a line from each one to the next (A-B, B-C, C-D).
        for ((n, i) in measured.withIndex()) {
            val atom = molecule.atoms[i]
            key("measured", n, i, representation) {
                SphereNode(
                    radius = representation.haloRadius(atom.element, bonded[i]),
                    stacks = BALL_DETAIL, slices = BALL_DETAIL,
                    materialInstance = selectionHalo,
                    position = Position(atom.x, atom.y, atom.z),
                    apply = { this.isTouchable = false },
                )
            }
            if (n > 0) {
                val pose = BondGeometry.poseOf(molecule.atoms[measured[n - 1]], atom) ?: continue
                key("ruler line", n, measured[n - 1], i) {
                    Node(position = Position(pose.x, pose.y, pose.z), rotation = Rotation(y = pose.spinDegrees)) {
                        CylinderNode(
                            radius = RULER_LINE_RADIUS,
                            height = pose.length,
                            rotation = Rotation(z = pose.tiltDegrees),
                            materialInstance = rulerLine,
                            apply = { this.isTouchable = false },
                        )
                    }
                }
            }
        }

        // BONDS (none in space-filling).
        val bondRadius = representation.bondRadius
        if (bondRadius != null) {
            for (k in drawnBonds) {
                val bond = molecule.bonds[k]
                if (representation.bondsInAtomColours) {
                    // Two halves, each in the colour of its atom: the colour of a stick says what it joins.
                    val (first, second) = halves[k] ?: continue
                    for ((half, pose) in listOf(first, second).withIndex()) {
                        val element = molecule.atoms[if (half == 0) bond.from else bond.to].element
                        key("half", k, half, representation) {
                            BondCylinder(pose, bondRadius, materialOf(element), ScenePick.bondName(k))
                        }
                    }
                } else {
                    val pose = poses[k] ?: continue
                    key("bond", k, representation) {
                        BondCylinder(pose, bondRadius, stick, ScenePick.bondName(k))
                    }
                }
            }
        }
    }
}

/**
 * One cylinder placed by a [BondPose]: the OUTER node at the midpoint, turned around the vertical axis; the INNER one
 * holds the cylinder, leaned (document 07). Named like the bond, so a tap on it is a tap on the bond.
 */
@Composable
private fun SceneScope.BondCylinder(pose: BondPose, radius: Float, material: MaterialInstance, name: String) {
    Node(position = Position(pose.x, pose.y, pose.z), rotation = Rotation(y = pose.spinDegrees)) {
        CylinderNode(
            radius = radius,
            height = pose.length,
            rotation = Rotation(z = pose.tiltDegrees),
            materialInstance = material,
            apply = { this.name = name },
        )
    }
}
