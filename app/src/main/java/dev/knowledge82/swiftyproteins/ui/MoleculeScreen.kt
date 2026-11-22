package dev.knowledge82.swiftyproteins.ui

import dev.knowledge82.swiftyproteins.scene.SceneFraming
import dev.knowledge82.swiftyproteins.scene.Turntable
import kotlinx.coroutines.Job
import kotlinx.coroutines.CompletableDeferred
import io.github.sceneview.capture.rememberSceneRecorder
import io.github.sceneview.capture.VideoSettings
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.animation.core.animateDpAsState
import android.util.Log
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import dev.knowledge82.swiftyproteins.share.ExportFormat
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import dev.knowledge82.swiftyproteins.molecule.MolecularFormula
import dev.knowledge82.swiftyproteins.scene.Drawn
import dev.knowledge82.swiftyproteins.scene.Measurement
import dev.knowledge82.swiftyproteins.scene.Representation
import dev.knowledge82.swiftyproteins.scene.ScenePick
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.knowledge82.swiftyproteins.molecule.Molecule
import dev.knowledge82.swiftyproteins.scene.ElementLegend
import dev.knowledge82.swiftyproteins.scene.ElementStyle
import dev.knowledge82.swiftyproteins.net.Origin
import dev.knowledge82.swiftyproteins.scene.Assembly
import dev.knowledge82.swiftyproteins.scene.AtomColors
import dev.knowledge82.swiftyproteins.scene.SceneLook
import dev.knowledge82.swiftyproteins.scene.Viewport
import dev.knowledge82.swiftyproteins.scene.FpsMeter
import dev.knowledge82.swiftyproteins.sound.LocalSounds
import dev.knowledge82.swiftyproteins.sound.SoundCue
import dev.knowledge82.swiftyproteins.scene.FpsReading
import dev.knowledge82.swiftyproteins.scene.FpsText
import dev.knowledge82.swiftyproteins.share.CaptureException
import dev.knowledge82.swiftyproteins.share.CaptureFailure
import dev.knowledge82.swiftyproteins.share.MoleculeSharer
import dev.knowledge82.swiftyproteins.share.ShareStatus
import dev.knowledge82.swiftyproteins.ui.theme.SwiftyColors
import dev.knowledge82.swiftyproteins.ui.theme.SwiftyDataText
import dev.knowledge82.swiftyproteins.ui.theme.glowing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The screen of one molecule, the "Auspex Readout" (design v2, screens 16-22): the 3D scene full size, a panel on
 * top (back, pattern name, hydrogens, share), targeting brackets around the viewport, the share status, the readout of
 * the tapped atom and the CPK colour key.
 *
 * The LOGIC is the same as before the design (selection, hydrogen switch, Back). The Share button (document 09)
 * captures the 3D scene and opens Android's share sheet; a small status line says what it is doing.
 */
@Composable
fun MoleculeScreen(
    molecule: Molecule,
    origin: Origin,
    onBack: () -> Unit,
    startModel: Representation = Representation.BALL_AND_STICK,
    startWithHydrogens: Boolean = true,
    favorite: Boolean = false,
    atomColors: AtomColors = AtomColors.JMOL,
    onToggleFavorite: () -> Unit = {},
    onCompare: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // The system Back button returns to the list, exactly like the button on screen.
    BackHandler(onBack = onBack)

    // rememberSaveable: all of this survives a rotation of the phone. At most ONE of selectedAtom / selectedBond is set.
    var selectedAtom by rememberSaveable { mutableStateOf<Int?>(null) }
    var selectedBond by rememberSaveable { mutableStateOf<Int?>(null) }
    // The settings (document 16) give only the STARTING values: rememberSaveable reads them once, when the molecule opens.
    var showHydrogens by rememberSaveable { mutableStateOf(startWithHydrogens) }
    var representation by rememberSaveable { mutableStateOf(startModel) }   // bonus VII.1
    var highlightElement by rememberSaveable { mutableStateOf<String?>(null) }                  // bonus VII.3
    var focus by rememberSaveable { mutableStateOf<Int?>(null) }                                // bonus VII.3
    // The ruler (bonus VII.3, document 16): on or off, and the tapped atoms (an IntArray: a type a Bundle can hold).
    var ruler by rememberSaveable { mutableStateOf(false) }
    var showLabels by rememberSaveable { mutableStateOf(false) }   // bonus VII.3, document 19
    var measuredIds by rememberSaveable { mutableStateOf(IntArray(0)) }
    val hasHydrogens = remember(molecule) { molecule.atoms.any { it.element == "H" } }
    val legend = remember(molecule, showHydrogens) { ElementLegend.of(molecule, showHydrogens) }

    // Share: the state of the button and the job. The job lives in the screen's coroutine scope, so leaving the
    // screen cancels a capture in progress (nothing is left half done in the background).
    var share by remember { mutableStateOf<ShareStatus>(ShareStatus.Idle) }
    val sounds = LocalSounds.current
    // How much of the scene exists (bonus VII.4, document 20): (built, total). The scene builds a batch per frame.
    var assembly by remember(molecule) { mutableStateOf(0 to Assembly.total(molecule.atoms.size, molecule.bonds.size)) }
    val assembling = assembly.first < assembly.second

    // The frame counter, in EVERY build (a learning project: the number is part of what we show). The scene reports
    // each presented frame; the label is refreshed 4 times a second (not on every frame: no recomposition at 60 Hz).
    val fpsMeter = remember { FpsMeter() }
    var fps by remember { mutableStateOf<FpsReading>(FpsReading.Idle) }
    LaunchedEffect(fpsMeter) {
        while (true) {
            delay(FPS_REFRESH_MILLIS)
            fps = fpsMeter.reading(System.nanoTime())   // the same clock as the frame times
        }
    }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val view = LocalView.current
    // The export menu (document 18): SHARE opens it; each choice runs here.
    var exportMenu by remember { mutableStateOf(false) }

    // THE VIDEO (bonus VII.5, document 22). SceneView's recorder encodes what the scene draws (none of the panels) into
    // an MP4 while the molecule makes one full turn. While it records, the panels give way to a REC line and Cancel,
    // and the camera aims at the whole screen, so the molecule is in the middle of the video.
    val recorder = rememberSceneRecorder()
    var recordingMode by remember { mutableStateOf(false) }
    var turning by remember { mutableStateOf(false) }
    var turnMillis by remember { mutableLongStateOf(0L) }
    val turnDone = remember { arrayOfNulls<CompletableDeferred<Unit>>(1) }
    var videoJob by remember { mutableStateOf<Job?>(null) }
    fun startVideo(format: ExportFormat) {
        share = ShareStatus.Working(format)
        sounds.play(SoundCue.SHUTTER)
        selectedAtom = null   // a clean video: no halo of a selection (select() is declared further down)
        selectedBond = null
        focus = null          // the turn is around the molecule's centre: no orbit around one atom
        videoJob = scope.launch {
            val done = CompletableDeferred<Unit>()
            turnDone[0] = done
            recordingMode = true
            turnMillis = 0L
            try {
                delay(Turntable.SETTLE_MILLIS)   // the panels go and the camera re-aims before the first frame
                recorder.overlay = MoleculeSharer.videoCaption(context, molecule)
                val settings = VideoSettings(frameRate = Turntable.FRAME_RATE, maxDurationMillis = Turntable.MAX_RECORDING_MILLIS)
                if (!recorder.startRecording(recorder.newCaptureFile("mp4"), settings)) {
                    Log.w("SwiftyProteins", "video of ${molecule.id} did not start", recorder.lastError)
                    throw CaptureException(CaptureFailure.VIDEO_FAILED, recorder.lastError)
                }
                turning = true
                done.await()
                val recording = recorder.stopRecording() ?: throw CaptureException(CaptureFailure.VIDEO_FAILED, recorder.lastError)
                turning = false
                recordingMode = false
                MoleculeSharer.deliverVideo(context, molecule, recording.file, format)
                share = ShareStatus.Done(format)
            } catch (e: CancellationException) {
                recorder.stopRecording()?.file?.delete()   // cancelled: nothing of it stays on the phone
                throw e
            } catch (e: CaptureException) {
                share = ShareStatus.Failed(e.failure)
            } catch (e: Exception) {
                Log.w("SwiftyProteins", "video of ${molecule.id} failed", e)
                share = ShareStatus.Failed(CaptureFailure.VIDEO_FAILED)
            } finally {
                turning = false
                recordingMode = false
                recorder.overlay = null
                turnDone[0] = null
            }
        }
    }
    fun cancelVideo() {
        videoJob?.cancel()
        videoJob = null
        share = ShareStatus.Failed(CaptureFailure.VIDEO_CANCELLED)
    }
    // While recording, the system Back cancels the recording (registered AFTER the screen's own Back: it wins).
    BackHandler(enabled = recordingMode) { cancelVideo() }

    fun startExport(format: ExportFormat) {
        if (!share.canStart || assembling) return   // an image of half a molecule is not what the user asked for
        if (format.isVideo) { startVideo(format); return }
        share = ShareStatus.Working(format)
        sounds.play(SoundCue.SHUTTER)
        scope.launch {
            share = try {
                MoleculeSharer.export(context, view, molecule, format, representation, showHydrogens, atomColors)
                ShareStatus.Done(format)
            } catch (e: CancellationException) {
                throw e   // the screen went away: not a failure, just stop
            } catch (e: CaptureException) {
                Log.w("SwiftyProteins", "export ${format.name} of ${molecule.id} failed: ${e.failure}", e.cause)
                ShareStatus.Failed(e.failure)
            } catch (e: Exception) {
                Log.w("SwiftyProteins", "export ${format.name} of ${molecule.id} failed", e)
                ShareStatus.Failed(CaptureFailure.OTHER)
            }
        }
    }
    // Android 7-9 only: the gallery needs WRITE_EXTERNAL_STORAGE, asked the first time (the system remembers the answer).
    // Which gallery export asked for it (image or video): it runs once the answer comes.
    var waitingForPermission by remember { mutableStateOf<ExportFormat?>(null) }
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val format = waitingForPermission ?: ExportFormat.GALLERY
        waitingForPermission = null
        if (granted) startExport(format) else share = ShareStatus.Failed(CaptureFailure.NEEDS_PERMISSION)
    }
    fun choose(format: ExportFormat) {
        exportMenu = false
        val needsPermission = format.toGallery && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            waitingForPermission = format
            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            startExport(format)
        }
    }
    val onShare = { if (share.canStart && !assembling) exportMenu = true }
    if (exportMenu) ExportDialog(onChoose = ::choose, onDismiss = { exportMenu = false })
    // "Image ready" or an error stays on screen a few seconds, then the line goes away.
    LaunchedEffect(share) {
        if (share is ShareStatus.Done || share is ShareStatus.Failed) {
            delay(3_000)
            share = ShareStatus.Idle
        }
    }

    // What is REALLY highlighted: something that exists AND is drawn now (not a hidden hydrogen, not a bond in
    // space-filling). The remembered index stays: undoing the switch brings the highlight back.
    // With the ruler on, the ruler owns the taps and the card: no single selection is shown.
    val selection = if (ruler) null else Drawn.selection(molecule, selectedAtom, selectedBond, showHydrogens, representation)
    val measured = Measurement.visible(measuredIds.toList(), molecule, showHydrogens)
    fun select(pick: ScenePick?) {
        if (pick != null) sounds.play(SoundCue.SELECT)
        selectedAtom = (pick as? ScenePick.Atom)?.index
        selectedBond = (pick as? ScenePick.Bond)?.index
    }

    // The subject (VI.4): the atom information disappears when the user taps ANYWHERE else. A tap on the empty scene or
    // on another atom already does it (MoleculeScene); this makes a tap on a PANEL do it too. Only on the panels: the
    // middle of the screen must keep sending every touch to the molecule. The keys inside the panels (H, SHARE, back)
    // keep their own action: they take the tap first. No ripple; TalkBack announces the action.
    val deselect = Modifier.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        enabled = selection != null || measured.isNotEmpty(),
        onClickLabel = "Hide the atom information",
    ) { select(null); measuredIds = IntArray(0) }

    // THE LAYOUT (document 21, §18). The 3D view fills the whole screen and the panels lie over it, but the camera aims
    // at the WINDOW between them (SceneView's contentPadding): the molecule is centred where it can be seen. Portrait:
    // panels above and below the window. Landscape: the window on the left, the panels in a scrolling column on the
    // right (in landscape, a portrait-like column had no room left for the molecule at all).
    // The window is measured in the same coordinates as the whole screen; until it is known, the scene waits one frame.
    var screenBounds by remember { mutableStateOf<Rect?>(null) }
    var windowBounds by remember { mutableStateOf<Rect?>(null) }
    val density = LocalDensity.current
    // While a video is recorded the camera aims at the WHOLE screen (the video is the whole surface): no panels cover it.
    val cover = if (recordingMode) screenBounds?.let { Viewport.Cover(0f, 0f, 0f, 0f, SceneFraming.aspectKey(it.width, it.height)) }
    else coverOf(screenBounds, windowBounds)
    // A panel that changes height (a long name, the ruler) moves the window: the molecule GLIDES to the new centre.
    val coverLeft by animateDpAsState(with(density) { (cover?.left ?: 0f).toDp() }, label = "cover left")
    val coverTop by animateDpAsState(with(density) { (cover?.top ?: 0f).toDp() }, label = "cover top")
    val coverRight by animateDpAsState(with(density) { (cover?.right ?: 0f).toDp() }, label = "cover right")
    val coverBottom by animateDpAsState(with(density) { (cover?.bottom ?: 0f).toDp() }, label = "cover bottom")

    // The scene is OUTSIDE the BoxWithConstraints below (document 21, §20). BoxWithConstraints composes its content
    // during LAYOUT, and runs it again at every new measure of itself: the FPS text changing four times a second was
    // enough. With the scene inside, every one of those passes ran the scene again, and every run of the scene asked
    // SceneView for frames: no IDLE. Out here, only what the scene really depends on can run it again.
    Box(modifier = modifier.fillMaxSize().onGloballyPositioned { screenBounds = it.boundsInWindow() }) {
        if (cover != null) MoleculeScene(
            molecule = molecule,
            representation = representation,
            showHydrogens = showHydrogens,
            selection = selection,
            // VII.3: selecting an atom highlights every atom of its element. A choice made in the key wins.
            highlightElement = Drawn.highlight(molecule, highlightElement, selection),
            focus = focus,
            measured = if (ruler) measured else emptyList(),
            showLabels = showLabels,
            onTap = { pick ->
                if (!ruler) select(pick)
                else when (pick) {
                    is ScenePick.Atom -> { sounds.play(SoundCue.SELECT); measuredIds = Measurement.tap(measured, pick.index).toIntArray() }
                    null -> measuredIds = IntArray(0)   // a tap on empty space clears the ruler, as it clears a selection
                    is ScenePick.Bond -> Unit           // the ruler measures between atoms
                }
            },
            // Double tap on an atom: orbit around it (and select it). On empty space: back to the whole molecule.
            // On a bond: nothing (a bond has no single point that is "it").
            onDoubleTap = { pick ->
                when (pick) {
                    is ScenePick.Atom -> { focus = pick.index; select(pick) }
                    null -> focus = null
                    is ScenePick.Bond -> Unit
                }
            },
            modifier = Modifier.fillMaxSize(),
            onFrame = fpsMeter::onFrame,
            onAssembly = { built, total -> assembly = built to total },
            look = SceneLook.of(SwiftyColors.palette.isDark),
            atomColors = atomColors,
            contentPadding = PaddingValues(start = coverLeft, top = coverTop, end = coverRight, bottom = coverBottom),
            visibleAspect = cover.aspectKey,
            surfaceMirrorer = recorder.surfaceMirrorer,
            turning = turning,
            onTurnProgress = { turnMillis = it },
            onTurnDone = { turnDone[0]?.complete(Unit) },
        )

        if (recordingMode) {
            RecordingOverlay(label = Turntable.label(turnMillis), recording = turning, onCancel = ::cancelVideo)
        } else BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val landscape = maxWidth > maxHeight
            // Read HERE, in the BoxWithConstraints scope itself: inside the Row and the Column below, `maxWidth` would be
            // taken from an OUTER layout scope, which Compose forbids (@LayoutScopeMarker, a Kotlin @DslMarker).
            val sideWidth = minOf(maxWidth * 0.46f, SIDE_COLUMN_MAX)

            val topPanel = @Composable {
                TopPanel(
                    modifier = deselect,
                    molecule = molecule,
                    onBack = onBack,
                    share = share,
                    onShare = onShare,
                    // A LAMBDA, read inside the counter only: the FPS text changes 4 times a second, and reading it here
                    // recomposed the whole screen and the 3D scene with it, which asked SceneView for new frames, which
                    // changed the FPS... The scene never went IDLE (seen on the A33). Document 21, §18.
                    fps = { fps },
                    origin = origin,
                )
            }
            // The window: the brackets, and over its bottom edge the short-lived status lines (assembly, share). They lie
            // OVER the molecule instead of pushing the window: they come and go, the window must not jump with them.
            val window = @Composable { m: Modifier ->
                Box(m.onGloballyPositioned { windowBounds = it.boundsInWindow() }) {
                    CornerBrackets(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 14.dp))
                    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 18.dp)) {
                        Assembly.label(assembly.first, assembly.second)?.let { text ->
                            AssemblyPanel(text = text, fraction = assembly.first.toFloat() / assembly.second)
                            Spacer(Modifier.height(8.dp))
                        }
                        share.label?.let { text -> ShareStatusPanel(text = text, status = share, modifier = deselect) }
                    }
                }
            }
            val readout = @Composable {
                // The same height for every content (the molecule, an atom, a bond, the ruler) at the normal text size:
                // the window does not change at each tap. With a bigger font the panel grows, and the molecule glides.
                val stable = Modifier.heightIn(min = READOUT_MIN_HEIGHT)
                if (ruler) RulerPanel(molecule = molecule, measured = measured, modifier = deselect.then(stable))
                else ReadoutPanel(
                    molecule = molecule,
                    selection = selection,
                    hydrogensHidden = hasHydrogens && !showHydrogens,
                    favorite = favorite,
                    onToggleFavorite = onToggleFavorite,
                    // A comparison of half-built scenes would compare nothing: CMP waits for the assembly.
                    onCompare = if (assembling) null else onCompare,
                    modifier = deselect.then(stable),
                )
            }
            val key = @Composable {
                ElementKey(
                    elements = legend,
                    highlighted = highlightElement,
                    onToggle = { element -> highlightElement = if (highlightElement == element) null else element },
                    representation = representation,
                    atomColors = atomColors,
                    hasHydrogens = hasHydrogens,
                    showHydrogens = showHydrogens,
                    onToggleHydrogens = {
                        showHydrogens = !showHydrogens
                        // A highlighted element that disappears from the key stops being highlighted.
                        if (!showHydrogens && highlightElement == "H") highlightElement = null
                    },
                    labels = showLabels,
                    onToggleLabels = { showLabels = !showLabels },
                    onModel = { representation = it },
                    ruler = ruler,
                    onToggleRuler = {
                        ruler = !ruler
                        measuredIds = IntArray(0)
                        select(null)
                    },
                    modifier = deselect,
                )
            }

            if (landscape) {
                Row(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp)) {
                    window(Modifier.weight(1f).fillMaxHeight())
                    Spacer(Modifier.width(10.dp))
                    Column(
                        modifier = Modifier.width(sideWidth).fillMaxHeight().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        topPanel()
                        readout()
                        key()
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp)) {
                    topPanel()
                    window(Modifier.weight(1f).fillMaxWidth())
                    readout()
                    Spacer(Modifier.height(10.dp))
                    key()
                }
            }
        }
    }
}

/** The two measured rectangles into [Viewport.cover] (pure, tested). */
private fun coverOf(screen: Rect?, window: Rect?): Viewport.Cover? =
    if (screen == null || window == null) null
    else Viewport.cover(screen.left, screen.top, screen.right, screen.bottom, window.left, window.top, window.right, window.bottom)

// The readout panel's height for every content at the normal text size (two lines of name, the formula, two lines of
// help; or an atom's three coordinates; or the ruler's three lines). Document 21, §18.
private val READOUT_MIN_HEIGHT = 150.dp

// The side column in landscape: about half the screen, never wider than this.
private val SIDE_COLUMN_MAX = 440.dp

/**
 * "AUSPEX // LIVE": back, "Pattern 001", "87 ATOMS :: 90 BONDS" and the SHARE key. Only who the molecule is and what
 * to do with it: the keys that change how it is DRAWN (MODEL…, H, RULER, LABELS) are together in the key panel below.
 * (H lived here until the "SHR" key became SHARE, document 25: on a 360 dp phone the title had no room left for both.)
 */
@Composable
private fun TopPanel(
    modifier: Modifier,
    molecule: Molecule,
    onBack: () -> Unit,
    share: ShareStatus,
    onShare: () -> Unit,
    fps: () -> FpsReading,
    origin: Origin,
) {
    // The tab says where the molecule came from (document 13): downloaded now, or the phone's copy (offline too).
    val tab = when (origin) {
        Origin.NETWORK -> "AUSPEX // LIVE"
        Origin.ARCHIVE -> "AUSPEX // ARCHIVE"
        Origin.ARCHIVE_OFFLINE -> "AUSPEX // OFFLINE ARCHIVE"
    }
    CogitatorPanel(label = tab, fill = SwiftyColors.ConsoleOverlay, contentPadding = 8.dp, modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackKey(onClick = onBack)
            Column(modifier = Modifier.weight(1f)) {
                Text("Pattern ${molecule.id}", style = MaterialTheme.typography.displayLarge.glowing(SwiftyColors.Gold, 0.3f), color = SwiftyColors.Gold, maxLines = 1)
                Row {
                    Text(
                        "${molecule.atoms.size} ATOMS :: ${molecule.bonds.size} BONDS :: ",
                        style = MaterialTheme.typography.labelSmall.glowing(SwiftyColors.Phosphor),
                        color = SwiftyColors.Phosphor,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    FpsLabel(fps)
                }
            }
            // Room between the FPS number (right-aligned at the end of the column) and the key: it touched it (A33).
            Spacer(Modifier.width(12.dp))
            ConsoleKey(
                // SHARE in full (it was "SHR", as in the design: too cryptic, document 25). "..." while the capture is
                // being prepared (the key is off: no double taps).
                text = if (share is ShareStatus.Working) "..." else "SHARE",
                onClick = onShare,
                contentDescription = "Share or export the molecule",
                enabled = share.canStart,
            )
        }
    }
}

/**
 * "60 FPS" while something moves, "IDLE" when the scene is parked (it draws on demand). The level is in the WORDS too
 * (the number), never only in the colour. Its own composable, reading the value through a lambda: when the number
 * changes, ONLY this text is redrawn (document 21, §18). Also used by the comparison view, one per scene (document 24).
 */
@Composable
internal fun FpsLabel(fps: () -> FpsReading) {
    val reading = fps()
    val ink = fpsColor(FpsText.level(reading))
    // A FIXED width: "9 FPS", "75 FPS" and "IDLE" take the same room, so a new number never moves anything else.
    Text(
        FpsText.label(reading),
        style = MaterialTheme.typography.labelSmall.glowing(ink),
        color = ink,
        maxLines = 1,
        textAlign = TextAlign.End,
        modifier = Modifier.width(FPS_LABEL_WIDTH),
    )
}

/** Phosphor when smooth, amber when it drops under 55, optic red under 30 (the subject's "poor performance"). */
@Composable
@ReadOnlyComposable
private fun fpsColor(level: FpsText.Level): Color = when (level) {
    FpsText.Level.GOOD -> SwiftyColors.Phosphor
    FpsText.Level.FAIR -> SwiftyColors.Amber
    FpsText.Level.POOR -> SwiftyColors.OpticRed
    FpsText.Level.IDLE -> SwiftyColors.Ash
}

internal const val FPS_REFRESH_MILLIS = 250L

// Room for "120 FPS" in the small label style.
private val FPS_LABEL_WIDTH = 64.dp

/**
 * "PICT-CAPTURE // EXPORT" (documents 18 and 22): the eight things SHARE can do. Each row is a 64 dp button with what it does and
 * the format; Back or a tap outside closes it.
 */
@Composable
private fun ExportDialog(onChoose: (ExportFormat) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        CogitatorPanel(label = "PICT-CAPTURE // EXPORT", cut = 11.dp, modifier = Modifier.widthIn(max = 440.dp)) {
            // Eight choices: on a phone in landscape they do not fit, so they scroll (Cancel stays visible below).
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                for (format in ExportFormat.entries) {
                    Surface(
                        onClick = { onChoose(format) },
                        color = Color.Transparent,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.Center, modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                            Text(format.label, style = MaterialTheme.typography.titleMedium, color = SwiftyColors.Gold)
                            Text(format.detail, style = SwiftyDataText.small.glowing(SwiftyColors.Phosphor), color = SwiftyColors.Phosphor)
                        }
                    }
                    ConsoleRule()
                }
            }
            Spacer(Modifier.height(12.dp))
            SecondaryButton("Cancel", onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp))
        }
    }
}

/**
 * While the video is recorded (document 22): the panels are gone (the recorder captures only the scene, but the user
 * should see what the video will be), a REC line at the top, Cancel at the bottom. It covers the whole screen and
 * takes every touch: a finger turning the molecule would spoil the turn.
 */
@Composable
private fun RecordingOverlay(label: String, recording: Boolean, onCancel: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = null) { }
            .padding(horizontal = 24.dp, vertical = 18.dp),
    ) {
        CogitatorPanel(
            label = "VID-CAPTURE",
            accent = SwiftyColors.OpticRed,
            fill = SwiftyColors.ConsoleOverlay,
            contentPadding = 10.dp,
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                // The red dot: never the only sign (the words say REC too).
                Box(Modifier.size(12.dp).background(if (recording) SwiftyColors.OpticRed else SwiftyColors.Ash, CircleShape))
                Spacer(Modifier.width(10.dp))
                Text(
                    if (recording) label else "Preparing the turntable…",
                    style = MaterialTheme.typography.labelMedium.glowing(SwiftyColors.OpticRed),
                    color = if (recording) SwiftyColors.OpticRed else SwiftyColors.Ash,
                    maxLines = 1,
                )
            }
        }
        SecondaryButton("Cancel", onClick = onCancel, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth())
    }
}

/** "PICT-CAPTURE ACTIVE" / "SYSTEM MESSAGE": what the Share key is doing. */
/** "ASSEMBLY 312 / 511" and a bar while the scene is being built (document 20). It goes away when it is complete. */
@Composable
private fun AssemblyPanel(text: String, fraction: Float) {
    CogitatorPanel(
        label = "PATTERN ASSEMBLY",
        fill = SwiftyColors.ConsoleOverlay,
        contentPadding = 12.dp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium.glowing(SwiftyColors.Phosphor),
            color = SwiftyColors.Phosphor,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics { },   // the text above already says it
            color = SwiftyColors.Phosphor,
            trackColor = SwiftyColors.Ash.copy(alpha = 0.3f),
        )
    }
}

@Composable
private fun ShareStatusPanel(text: String, status: ShareStatus, modifier: Modifier) {
    val failed = status is ShareStatus.Failed
    CogitatorPanel(
        label = when (status) {
            is ShareStatus.Working -> "PICT-CAPTURE ACTIVE"
            is ShareStatus.Failed -> "PICT-CAPTURE FAILED"
            else -> "SYSTEM MESSAGE"
        },
        accent = if (failed) SwiftyColors.OpticRed else SwiftyColors.Brass,
        fill = SwiftyColors.ConsoleOverlay,
        contentPadding = 12.dp,
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium.glowing(if (failed) SwiftyColors.OpticRed else SwiftyColors.Phosphor),
            color = if (failed) SwiftyColors.OpticRed else SwiftyColors.Phosphor,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * "AUSPEX READOUT": what the user is looking at. Nothing selected: the MOLECULE (its name from the file and its
 * formula, bonus VII.5) and how to use the view. An atom: its name, element and X / Y / Z. A bond (bonus VII.3): its
 * two atoms, its order and its length.
 */
@Composable
private fun ReadoutPanel(
    molecule: Molecule,
    selection: ScenePick?,
    hydrogensHidden: Boolean,
    favorite: Boolean,
    onToggleFavorite: () -> Unit,
    onCompare: (() -> Unit)?,
    modifier: Modifier,
) {
    CogitatorPanel(label = "AUSPEX READOUT", fill = SwiftyColors.ConsoleOverlay, modifier = modifier.fillMaxWidth()) {
        when (selection) {
            null -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            molecule.name ?: "Ligand ${molecule.id}",
                            style = MaterialTheme.typography.titleMedium,
                            color = SwiftyColors.Bone,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(formulaText(molecule), style = SwiftyDataText.medium.glowing(SwiftyColors.Gold, 0.3f), color = SwiftyColors.Gold)
                    }
                    // CMP: set this molecule beside another one (bonus VII.5, document 24).
                    ConsoleKey(
                        text = "CMP",
                        onClick = { onCompare?.invoke() },
                        contentDescription = "Compare with another ligand",
                        enabled = onCompare != null,
                    )
                    Spacer(Modifier.width(4.dp))
                    // The favourite star of this ligand (bonus VII.5, document 16).
                    StarKey(
                        on = favorite,
                        onClick = onToggleFavorite,
                        description = "Favourite ${molecule.id}",
                        state = if (favorite) "favourite" else "not favourite",
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (hydrogensHidden) "Hydrogens hidden. Tap an atom or a bond." else "Drag to rotate. Pinch to zoom. Tap an atom or a bond. Double-tap to orbit it.",
                    style = MaterialTheme.typography.bodySmall.glowing(SwiftyColors.Phosphor),
                    color = SwiftyColors.Phosphor,
                )
            }
            is ScenePick.Atom -> {
                val atom = molecule.atoms[selection.index]
                Text(AtomText.title(atom), style = MaterialTheme.typography.titleMedium, color = SwiftyColors.Gold)
                Spacer(Modifier.height(8.dp))
                for ((axis, value) in listOf("X" to atom.x, "Y" to atom.y, "Z" to atom.z)) {
                    ReadoutRow(axis, AtomText.value(value))
                }
            }
            is ScenePick.Bond -> {
                Text(BondText.title(molecule, selection.index), style = MaterialTheme.typography.titleMedium, color = SwiftyColors.Gold)
                Spacer(Modifier.height(8.dp))
                ReadoutRow("Atoms", BondText.elements(molecule, selection.index))
                ReadoutRow("Length", BondText.length(molecule, selection.index))
            }
        }
    }
}

/** "AUSPEX // RULER": the measured atoms and the distance, angle or torsion (document 16). */
@Composable
private fun RulerPanel(molecule: Molecule, measured: List<Int>, modifier: Modifier) {
    CogitatorPanel(label = "AUSPEX // RULER", fill = SwiftyColors.ConsoleOverlay, modifier = modifier.fillMaxWidth()) {
        Text(MeasureText.title(molecule, measured), style = MaterialTheme.typography.titleMedium, color = SwiftyColors.Gold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(8.dp))
        for (line in MeasureText.lines(molecule, measured)) ReadoutRow(line.label, line.value)
    }
}

/** One line of the readout: a label on the left (phosphor), the value on the right. */
@Composable
private fun ReadoutRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = SwiftyDataText.medium.glowing(SwiftyColors.Phosphor), color = SwiftyColors.Phosphor, modifier = Modifier.weight(1f))
        Text(value, style = SwiftyDataText.medium, color = SwiftyColors.Bone)
    }
}

/** The formula of [molecule] with real subscripts (the shared [formulaText], document 15). */
private fun formulaText(molecule: Molecule): AnnotatedString = formulaText(MolecularFormula.of(molecule.atoms))

/**
 * "ELEMENTS / CPK KEY": the colour of each element that is drawn, and the MODEL key (bonus VII.1). Tapping an element
 * highlights every atom of it (bonus VII.3); tapping it again, or another one, changes that. The letter is always
 * written: the colour never carries the meaning alone.
 */
@Composable
private fun ElementKey(
    elements: List<String>,
    highlighted: String?,
    onToggle: (String) -> Unit,
    representation: Representation,
    atomColors: AtomColors,
    hasHydrogens: Boolean,
    showHydrogens: Boolean,
    onToggleHydrogens: () -> Unit,
    onModel: (Representation) -> Unit,
    labels: Boolean,
    onToggleLabels: () -> Unit,
    ruler: Boolean,
    onToggleRuler: () -> Unit,
    modifier: Modifier,
) {
    val sounds = LocalSounds.current
    CogitatorPanel(fill = SwiftyColors.ConsoleOverlay, contentPadding = 10.dp, scanlines = false, modifier = modifier.fillMaxWidth()) {
        Text("Elements / ${atomColors.label} · ${representation.label}", style = MaterialTheme.typography.labelSmall, color = SwiftyColors.Ash, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        // The keys that change how the molecule is drawn: the MODEL list (VII.1: "dropdown"), the RULER and the LABELS
        // (VII.3) share the width; H (hydrogens shown or hidden) is a narrow key at the end, only if there are any.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            var open by remember { mutableStateOf(false) }
            Box(modifier = Modifier.weight(1f)) {
                ConsoleKey(
                    text = "MODEL…",   // "…" = opens a choice (and ▾ is not in our font)
                    onClick = { open = true },
                    contentDescription = "Model: ${representation.label}. Choose another",
                    modifier = Modifier.fillMaxWidth(),
                )
                DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = SwiftyColors.Console) {
                    for (model in Representation.entries) {
                        val current = model == representation
                        DropdownMenuItem(
                            text = {
                                Text(
                                    (if (current) "> " else "  ") + model.label,
                                    style = SwiftyDataText.medium.glowing(if (current) SwiftyColors.Phosphor else SwiftyColors.Gold),
                                    color = if (current) SwiftyColors.Phosphor else SwiftyColors.Gold,
                                )
                            },
                            onClick = {
                                sounds.play(SoundCue.KEY)
                                open = false
                                onModel(model)
                            },
                            modifier = Modifier.semantics { if (current) stateDescription = "current" },
                        )
                    }
                }
            }
            ConsoleKey(
                text = "RULER",
                onClick = onToggleRuler,
                contentDescription = "Ruler: measure distances and angles",
                active = ruler,
                stateDescription = if (ruler) "on" else "off",
                modifier = Modifier.weight(1f),
            )
            ConsoleKey(
                text = "LABELS",
                onClick = onToggleLabels,
                contentDescription = "Element labels on the atoms",
                active = labels,
                stateDescription = if (labels) "on" else "off",
                modifier = Modifier.weight(1f),
            )
            if (hasHydrogens) {
                ConsoleKey(
                    text = "H",
                    onClick = onToggleHydrogens,
                    contentDescription = "Hydrogen atoms",
                    active = showHydrogens,
                    stateDescription = if (showHydrogens) "shown" else "hidden",
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            for (element in elements) {
                val on = element == highlighted
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clickable(onClickLabel = if (on) "Stop highlighting" else "Highlight every atom of this element") {
                            sounds.play(SoundCue.KEY)
                            onToggle(element)
                        }
                        .semantics {
                            contentDescription = ElementStyle.nameOf(element)
                            stateDescription = if (on) "highlighted" else "not highlighted"
                        }
                        .border(if (on) 1.5.dp else 0.dp, if (on) SwiftyColors.Gold else Color.Transparent, CutCornerShape(4.dp))
                        .padding(horizontal = 8.dp),
                ) {
                    // A thin bronze ring: a white hydrogen dot must stay visible on parchment too.
                    Box(Modifier.size(10.dp).background(cpkColor(element, atomColors), CircleShape).border(0.5.dp, SwiftyColors.Bronze, CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text(element, style = SwiftyDataText.small, color = if (on) SwiftyColors.Gold else SwiftyColors.Bone)
                }
            }
        }
    }
}

/** The same colour the scene paints the element with (ElementStyle, in the chosen scheme), as a Compose colour. */
private fun cpkColor(element: String, scheme: AtomColors): Color {
    val rgb = ElementStyle.colorOf(element, scheme)
    return Color((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF)
}
