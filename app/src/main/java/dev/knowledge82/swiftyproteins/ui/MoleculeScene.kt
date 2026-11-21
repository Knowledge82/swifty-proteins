package dev.knowledge82.swiftyproteins.ui

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.unit.dp
import com.google.android.filament.LightManager
import com.google.android.filament.MaterialInstance
import dev.knowledge82.swiftyproteins.molecule.Molecule
import android.util.Log
import androidx.compose.runtime.SideEffect
import dev.knowledge82.swiftyproteins.scene.Assembly
import dev.knowledge82.swiftyproteins.scene.FrameDiagnosis
import dev.knowledge82.swiftyproteins.scene.AtomColors
import dev.knowledge82.swiftyproteins.scene.SceneLook
import dev.knowledge82.swiftyproteins.scene.Turntable
import io.github.sceneview.utils.SurfaceMirrorer
import dev.knowledge82.swiftyproteins.scene.AtomLabels
import dev.knowledge82.swiftyproteins.scene.MeshDetail
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
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.node.Node as NodeImpl
import io.github.sceneview.createEnvironment
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironment
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberFillLightNode
import io.github.sceneview.rememberMainLightNode
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberOnGestureListener
import kotlin.math.max

// Node names are how a TAP is traced back to what was drawn ("atom 12", "bond 3"): see ScenePick.

// Mesh detail (how many bands a ball is cut into, how many sides a stick has): by molecule size, MeshDetail (document 20).

// The halos (document 07 and 15): colours and opacities come from the SceneLook of the theme (document 21).
// A bond's halo: a translucent tube around the stick, this many times thicker (and never thinner than an atom's halo).
private const val BOND_HALO_SCALE = 2.2f
// The ruler's line between two measured atoms: thin, the selection colour, more opaque than a halo (a line, not a glow).
private const val RULER_LINE_RADIUS = 0.06f

// A finger is much wider than a hydrogen (radius 0.25): the area that answers to a tap is never smaller than this.
private const val MIN_TOUCH_RADIUS = 0.35f

// LIGHTING (subject VI.4: "lighting should highlight the 3D structure ... consider multiple light sources").
// Three directional lights + the image-based ambient light (IBL) SceneView adds by default. A Direction is where the
// light TRAVELS, in world space; the camera starts in front (+z), slightly above. Intensities in lux come from the
// SceneLook of the theme (dark: main 10 000, fill 3 000, rim 5 000, like SceneView's own defaults; light: less rim).
// The lights stay put while the camera orbits, as in a real room.
// KEY: the main light, from upper-front-left: lights the faces the user sees first and gives each ball its shading.
private val KEY_LIGHT_DIRECTION = Direction(0.45f, -0.65f, -0.6f)
// FILL: weaker, from the front-right and a little below: lifts the dark side, so no ball turns into a flat black disc.
private val FILL_LIGHT_DIRECTION = Direction(-0.6f, 0.15f, -0.8f)
// RIM: from behind and above, toward the camera: a bright edge on the outline separates the balls from the background.
private val RIM_LIGHT_DIRECTION = Direction(0f, -0.4f, 0.9f)

// The names of the watched parameters, in the order of `watched` in MoleculeScene.
private val WATCHED = listOf(
    "molecule", "representation", "showHydrogens", "selection", "highlightElement", "focus", "measured", "showLabels",
    "onTap", "onDoubleTap", "modifier", "onFrame", "onAssembly", "look", "atomColors", "contentPadding", "visibleAspect", "built",
    "spinSince",
)

private fun rgbColor(rgb: Int, alpha: Float = 1f): Color =
    Color(((rgb shr 16) and 0xFF) / 255f, ((rgb shr 8) and 0xFF) / 255f, (rgb and 0xFF) / 255f, alpha)

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
 * @param showLabels       the element symbol on every drawn atom (bonus VII.3, document 19)
 * @param onTap            what a single tap hit: an atom, a bond, or `null` (empty space)
 * @param onDoubleTap      the same for a double tap
 * @param onFrame          called once per frame that really reached the screen (the FPS counter)
 * @param onAssembly       how many pieces (atoms, then bonds) are already in the scene, of how many (document 20)
 * @param look             background, halos and lights of the theme (document 21)
 * @param atomColors       the colour scheme of the atoms (document 21)
 * @param contentPadding   the parts of the view covered by panels: the camera aims at what is left (document 21, §18)
 * @param visibleAspect    width / height of that visible window, rounded ([SceneFraming.aspectKey]): how far the
 *                         camera must stand for the whole molecule to fit in it
 * @param surfaceMirrorer  SceneView's recorder draws the scene a second time into the video encoder through it
 * @param turning          true while a video is recorded: the molecule makes ONE full turn ([Turntable], document 22)
 * @param onTurnProgress   how far the turn is, in milliseconds (the REC line)
 * @param onTurnDone       the turn is complete: the screen stops the recording
 * @param spinSince        the comparison view's SPIN (document 24): the molecule turns without end, its angle counted
 *                         from this moment (System.nanoTime, the clock of the frames). Two scenes given the same moment
 *                         turn together, frame by frame. Null: no spin
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
    showLabels: Boolean,
    onTap: (ScenePick?) -> Unit,
    onDoubleTap: (ScenePick?) -> Unit,
    modifier: Modifier = Modifier,
    onFrame: (Long) -> Unit = {},
    onAssembly: (built: Int, total: Int) -> Unit = { _, _ -> },
    look: SceneLook = SceneLook.DARK,
    atomColors: AtomColors = AtomColors.JMOL,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    visibleAspect: Float = SceneFraming.TALL_PHONE_ASPECT,
    surfaceMirrorer: SurfaceMirrorer? = null,
    turning: Boolean = false,
    onTurnProgress: (Long) -> Unit = {},
    onTurnDone: () -> Unit = {},
    spinSince: Long? = null,
) {
    // LEVEL OF DETAIL (bonus VII.4): from the size of the MOLECULE, so hiding hydrogens never rebuilds every mesh.
    val atomCount = molecule.atoms.size
    val ballDetail = MeshDetail.ball(atomCount)
    val sides = MeshDetail.cylinderSides(atomCount)

    // ASSEMBLY (bonus VII.4): the nodes are created a batch per frame, atoms first, then bonds; the batch adapts to how
    // long the frames take. A new molecule or a new model starts again from zero (every node is new then anyway).
    val total = Assembly.total(molecule.atoms.size, molecule.bonds.size)
    var built by remember(molecule, representation) { mutableIntStateOf(0) }
    LaunchedEffect(molecule, representation) {
        var batch = Assembly.FIRST_BATCH
        var last = withFrameNanos { it }
        while (built < total) {
            built = Assembly.advance(built, batch, total)
            val now = withFrameNanos { it }
            batch = Assembly.nextBatch(batch, (now - last) / 1_000_000f)
            last = now
        }
    }
    val currentOnAssembly by rememberUpdatedState(onAssembly)
    LaunchedEffect(built, total) { currentOnAssembly(built, total) }

    val engine = rememberEngine()
    val materialLoader = rememberMaterialLoader(engine)
    // Our own camera node (SceneView would make one anyway): the labels need to know where the camera is.
    val cameraNode = rememberCameraNode(engine)
    val context = LocalContext.current

    // One material per ELEMENT, created the first time it is needed (a ligand has a handful of elements, not 256 colors).
    // Keyed by the scheme too: another scheme is another set of materials.
    val materials = remember(materialLoader, atomColors) { HashMap<String, MaterialInstance>() }
    fun materialOf(element: String): MaterialInstance =
        materials.getOrPut(element) { materialLoader.createColorInstance(rgbColor(ElementStyle.colorOf(element, atomColors))) }

    val stick = remember(materialLoader) { materialLoader.createColorInstance(Color(0.7f, 0.7f, 0.7f, 1f)) }
    // SELECTION: the atom keeps its CPK colour (recolouring it would lie about the element: yellow is sulfur, green is
    // chlorine...). Instead a translucent shell is drawn AROUND it. UNLIT: a glow, not an object, so the lights do not
    // shade it; alpha < 1 makes SceneView pick its transparent material.
    val selectionHalo = remember(materialLoader, look) { materialLoader.createUnlitColorInstance(rgbColor(look.selection, look.selectionAlpha)) }
    val elementHalo = remember(materialLoader, look) { materialLoader.createUnlitColorInstance(rgbColor(look.element, look.elementAlpha)) }
    val rulerLine = remember(materialLoader, look) { materialLoader.createUnlitColorInstance(rgbColor(look.selection, look.rulerAlpha)) }

    // THE BACKGROUND (document 21): SceneView's own black in the dark theme; in the light one, its skybox painted
    // parchment (SceneLook.skyboxColor compensates the tone mapper). Keyed by the backdrop: a new theme, a new one.
    val environmentLoader = rememberEnvironmentLoader(engine)
    val environment = rememberEnvironment(environmentLoader, key = look.backdrop) {
        createEnvironment(environmentLoader).also { env ->
            look.backdrop?.let { env.skybox?.setColor(SceneLook.skyboxColor(it)) }
        }
    }

    // All of this depends only on the molecule (and on the hydrogen switch): computed once, not at every recomposition.
    val wholeDistance = remember(molecule, visibleAspect) { SceneFraming.cameraDistance(SceneFraming.radiusOf(molecule), visibleAspect) }
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

    // LABELS (document 19). One small picture per ELEMENT, shared by all its atoms (256 labels of TextNode would be
    // 256 pictures of 512 x 128 pixels: 64 MB). Each label node is kept here to be moved toward the camera.
    val labelPictures = remember { HashMap<String, Bitmap>() }
    fun labelOf(element: String): Bitmap = labelPictures.getOrPut(element) { LabelPicture.draw(context, element) }
    val labelNodes = remember { HashMap<Int, NodeImpl>() }
    val drawnBonded by rememberUpdatedState(bonded)
    val currentRepresentation by rememberUpdatedState(representation)
    val currentMolecule by rememberUpdatedState(molecule)
    // Called by SceneView AFTER each presented frame: an observer, it does not keep the scene drawing. A label is
    // moved only if the camera moved; with the camera still nothing changes and the scene goes idle (document 12).
    // The turntable's node (document 22), kept here when SceneView creates it. A plain holder, not state: it is set once.
    val spinNode = remember { arrayOfNulls<NodeImpl>(1) }
    val currentOnTurnProgress by rememberUpdatedState(onTurnProgress)
    val currentOnTurnDone by rememberUpdatedState(onTurnDone)
    LaunchedEffect(turning, spinSince) {
        // SceneView creates the node on the first composition of its content: if it is not there yet, wait for it.
        var found = spinNode[0]
        while (found == null) { withFrameNanos { }; found = spinNode[0] }
        val node: NodeImpl = found
        if (turning) {
            // A frame loop of our own: each frame writes the node's rotation, which also asks SceneView for that frame.
            val start = withFrameNanos { it }
            while (true) {
                val elapsed = withFrameNanos { it } - start
                node.rotation = Rotation(y = Turntable.angle(elapsed))
                currentOnTurnProgress(elapsed / 1_000_000L)
                if (Turntable.isDone(elapsed)) break
            }
            currentOnTurnDone()
            return@LaunchedEffect
        }
        val since = spinSince
        if (since == null) {
            node.rotation = Rotation(0f, 0f, 0f)
            return@LaunchedEffect
        }
        // The endless spin: the angle comes from the FRAME's time, the same for every scene in that frame.
        while (true) {
            val now = withFrameNanos { it }
            node.rotation = Rotation(y = Turntable.loopAngle(now - since))
        }
    }

    val currentOnFrame by rememberUpdatedState(onFrame)
    // WHY frames are drawn (document 21, §19): a line a second in Logcat, tag SwiftyFrames, while the scene draws.
    val diagnosis = remember { FrameDiagnosis() }
    // Which parameter made this function run again (document 21, §20): named in the Logcat line.
    val watched = listOf(
        molecule, representation, showHydrogens, selection, highlightElement, focus, measured, showLabels,
        onTap, onDoubleTap, modifier, onFrame, onAssembly, look, atomColors, contentPadding, visibleAspect, built,
        spinSince,
    )
    val previousWatched = remember { arrayOfNulls<Any?>(WATCHED.size) }
    val firstWatch = remember { booleanArrayOf(true) }
    SideEffect {
        diagnosis.onSceneRecomposed(FrameDiagnosis.changes(WATCHED, previousWatched, watched, firstWatch[0]))
        firstWatch[0] = false
    }
    val afterFrame: (Long) -> Unit = remember {
        { frameTime ->
            currentOnFrame(frameTime)
            val eye = cameraNode.worldPosition
            diagnosis.onFrame(frameTime, eye.x, eye.y, eye.z)?.let { Log.d(FrameDiagnosis.LOG_TAG, it) }
            if (labelNodes.isNotEmpty()) {
                // The camera in the MOLECULE's own coordinates: the same as the world's at rest, turned while the
                // turntable turns (document 22). Asked of the node itself, so no sign convention can be got wrong.
                val world = cameraNode.worldPosition
                val cam = spinNode[0]?.getLocalPosition(world) ?: world
                for ((i, node) in labelNodes) {
                    val atom = currentMolecule.atoms.getOrNull(i) ?: continue
                    val r = currentRepresentation.atomRadius(atom.element, drawnBonded.getOrElse(i) { false })
                    val (x, y, z) = AtomLabels.position(atom.x, atom.y, atom.z, r, cam.x, cam.y, cam.z)
                    val p = node.position
                    if (AtomLabels.needsMove(p.x, p.y, p.z, x, y, z)) node.position = Position(x, y, z)
                }
            }
        }
    }

    // The gesture listener is created once and remembers the FIRST lambdas it receives; these always call the latest.
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)

    // THE LIGHTS, set ONCE per look (document 21, §19). SceneView re-applies a light's `apply` block on EVERY
    // recomposition, and every light setter asks for a frame even when the value did not change: lights written inside
    // that block made any recomposition of this function draw frames. Created empty, set here when the look changes.
    val keyLight = rememberMainLightNode(engine)
    val fillLight = rememberFillLightNode(engine)
    LaunchedEffect(keyLight, fillLight, look) {
        keyLight.lightDirection = KEY_LIGHT_DIRECTION
        keyLight.intensity = look.keyLux
        fillLight.lightDirection = FILL_LIGHT_DIRECTION
        fillLight.intensity = look.fillLux
    }

    SceneView(
        modifier = modifier.fillMaxSize(),
        engine = engine,
        // Written out (SceneView would add a main and a fill light anyway) so the lighting is OURS and visible here.
        mainLightNode = keyLight,
        fillLightNode = fillLight,
        environmentLoader = environmentLoader,
        environment = environment,
        // OFF. SceneView's default (true) moves all the content ONCE, on the first frame that has any, so that the centre
        // of its bounding box lands on the origin. Our parser already centres every molecule (CifExtractor), and the
        // scene is built a batch per frame (document 20): the first frame holds only the first atoms, so SceneView
        // centred THOSE and the whole molecule stayed off-centre (seen on the A33). It also moved the content away from
        // the coordinates the camera, the labels and the double tap use. Document 21, §17.
        autoCenterContent = false,
        // The window between the panels: the molecule is centred THERE, not on the whole screen, while the scene still
        // draws under the panels too. Taps follow (SceneView maps them through the same projection). Document 21, §18.
        contentPadding = contentPadding,
        surfaceMirrorer = surfaceMirrorer,
        cameraNode = cameraNode,
        onFrame = afterFrame,
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
        SideEffect { diagnosis.onContentRecomposed() }
        // The third light (SceneView only has parameters for two): the rim. In its own function: with the same lux,
        // Compose SKIPS it on a recomposition, so its setters (which ask for a frame each) do not run again.
        RimLight(look.rimLux)

        // THE TURNTABLE (bonus VII.5, document 22): everything of the molecule hangs from this node. At rest its rotation
        // is zero and it changes nothing; while a video is recorded it turns once around the vertical axis.
        Node(apply = { spinNode[0] = this }) {
            for ((i, atom) in molecule.atoms.withIndex()) {
                if (drawn(i) && Assembly.hasAtom(i, built)) {
                    val radius = representation.atomRadius(atom.element, bonded[i])
                    // key: each atom keeps its identity when the list of nodes changes (hydrogens hidden or shown); the
                    // model and the radius are part of it because the TOUCH shape is only set when the node is created
                    // (`apply`): a node reused with a new radius would keep answering taps with the old size.
                    key(i, representation, bonded[i]) {
                        val detail = when {
                            representation == Representation.SPACE_FILLING -> MeshDetail.bigBall(atomCount)
                            radius < 0.3f -> MeshDetail.smallBall(atomCount)
                            else -> ballDetail
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
                    if (atom.element == highlightElement && drawn(i) && Assembly.hasAtom(i, built) && (selection as? ScenePick.Atom)?.index != i) {
                        key("element", i, representation, bonded[i]) {
                            SphereNode(
                                radius = representation.haloRadius(atom.element, bonded[i]),
                                stacks = ballDetail, slices = ballDetail,
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
                        stacks = ballDetail, slices = ballDetail,
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
                                sideCount = sides,
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
                        stacks = ballDetail, slices = ballDetail,
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
                                sideCount = sides,
                                rotation = Rotation(z = pose.tiltDegrees),
                                materialInstance = rulerLine,
                                apply = { this.isTouchable = false },
                            )
                        }
                    }
                }
            }

            // LABELS: the element symbol, facing the camera, on the visible face of each drawn atom.
            if (showLabels) {
                for ((i, atom) in molecule.atoms.withIndex()) {
                    if (!drawn(i) || !Assembly.hasAtom(i, built)) continue
                    val r = representation.atomRadius(atom.element, bonded[i])
                    key("label", i, representation, bonded[i]) {
                        // Which node THIS group made. When the model changes, Compose builds the NEW label group first
                        // (it registers itself) and disposes of the OLD one after: the old one must not remove the new.
                        val mine = remember { arrayOfNulls<NodeImpl>(1) }
                        BillboardNode(
                            bitmap = labelOf(atom.element),
                            widthMeters = AtomLabels.width(r),
                            heightMeters = AtomLabels.height(r),
                            position = Position(atom.x, atom.y, atom.z),   // moved toward the camera after the first frame
                            cameraPositionProvider = { cameraNode.worldPosition },
                            apply = {
                                this.isTouchable = false   // a tap on a label must reach its atom
                                labelNodes[i] = this
                                mine[0] = this
                            },
                        )
                        DisposableEffect(mine) { onDispose { if (labelNodes[i] === mine[0]) labelNodes.remove(i) } }
                    }
                }
            }

            // BONDS (none in space-filling).
            val bondRadius = representation.bondRadius
            if (bondRadius != null) {
                for (k in drawnBonds) {
                    if (!Assembly.hasBond(k, atomCount, built)) continue
                    val bond = molecule.bonds[k]
                    if (representation.bondsInAtomColours) {
                        // Two halves, each in the colour of its atom: the colour of a stick says what it joins.
                        val (first, second) = halves[k] ?: continue
                        for ((half, pose) in listOf(first, second).withIndex()) {
                            val element = molecule.atoms[if (half == 0) bond.from else bond.to].element
                            key("half", k, half, representation) {
                                BondCylinder(pose, bondRadius, materialOf(element), ScenePick.bondName(k), sides)
                            }
                        }
                    } else {
                        val pose = poses[k] ?: continue
                        key("bond", k, representation) {
                            BondCylinder(pose, bondRadius, stick, ScenePick.bondName(k), sides)
                        }
                    }
                }
            }
        }
    }
}

/** The rim light (see the lights above): its own restartable, skippable function. */
@Composable
private fun SceneScope.RimLight(lux: Float) {
    LightNode(type = LightManager.Type.DIRECTIONAL, intensity = lux, direction = RIM_LIGHT_DIRECTION)
}

/**
 * One cylinder placed by a [BondPose]: the OUTER node at the midpoint, turned around the vertical axis; the INNER one
 * holds the cylinder, leaned (document 07). Named like the bond, so a tap on it is a tap on the bond.
 */
@Composable
private fun SceneScope.BondCylinder(pose: BondPose, radius: Float, material: MaterialInstance, name: String, sides: Int) {
    Node(position = Position(pose.x, pose.y, pose.z), rotation = Rotation(y = pose.spinDegrees)) {
        CylinderNode(
            radius = radius,
            height = pose.length,
            sideCount = sides,
            rotation = Rotation(z = pose.tiltDegrees),
            materialInstance = material,
            apply = { this.name = name },
        )
    }
}
