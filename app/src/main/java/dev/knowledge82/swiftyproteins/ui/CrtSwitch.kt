package dev.knowledge82.swiftyproteins.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import dev.knowledge82.swiftyproteins.ui.theme.SwiftyPalette

/**
 * Shows [target], and changes from one screen to the next like an old television (documents 23 and 26):
 *
 * - the COLD START: the tube switches ON over the boot screen, a dot that stretches into a line that opens into the
 *   picture ([CrtTransition]). [content] is told when the tube is fully on, so the boot sequence starts only then;
 * - a molecule that was just DOWNLOADED: a CUT, at once (its loading dialog was the transition) ([Navigation.change]);
 * - every other change: a FLASH of the tube, pale phosphor green, with the screens swapped while it is fully lit
 *   ([ChannelChange]; it was a burst of television static at first, document 26, §3.3).
 *
 * WHY A CURTAIN AND NOT A TRANSFORM. The molecule screen holds a SurfaceView (Filament draws into its own window
 * surface): Compose's transforms and alpha do not reach it reliably. So nothing is transformed: everything is DRAWN
 * OVER the screen, as our panels already are over the 3D scene.
 *
 * FRAMES. The curtain's values are read only inside the Canvas (draw phase): during a change only the curtain is
 * redrawn, nothing is recomposed. At rest the Canvas draws nothing and asks for no frames: IDLE stays IDLE.
 *
 * @param target       the screen the app wants now ([Navigation.screenOf])
 * @param startOff     true on a cold start: the app opens with the tube switching on
 * @param reduceMotion the user asked the system for no animations: screens change at once
 * @param content      draws one screen, and whether the tube is fully ON ([on]); while the flash rises, the screen
 *                     it is given may be the OLD one
 */
@Composable
fun CrtSwitch(
    target: Screen,
    startOff: Boolean,
    reduceMotion: Boolean,
    content: @Composable (screen: Screen, on: Boolean) -> Unit,
) {
    // The screen on the tube. It lags behind [target] while the light rises: the old screen keeps drawing until the
    // flash hides it, and it carries its own data (a molecule screen carries its molecule).
    var shown by remember { mutableStateOf(target) }
    // The switching on (cold start only): 1 = off, 0 = fully on.
    val cover = remember { mutableFloatStateOf(if (startOff && !reduceMotion) 1f else 0f) }
    // The flash of a change of screen: its progress 0..1, or NONE when none is running.
    val channel = remember { mutableFloatStateOf(NONE) }

    LaunchedEffect(target.key, reduceMotion) {
        if (reduceMotion) {
            shown = target
            cover.floatValue = 0f
            channel.floatValue = NONE
            return@LaunchedEffect
        }
        if (shown.key == target.key) {
            // The cold start, or a change that came back to the screen already on the tube: finish what is running.
            if (cover.floatValue > 0f) switchOn(cover)
            if (channel.floatValue >= 0f) changeChannel(channel, from = channel.floatValue) { }
            return@LaunchedEffect
        }
        cover.floatValue = 0f   // a change during the switching on (it should not happen): the tube is simply on
        when (Navigation.change(shown, target)) {
            Navigation.Change.CUT -> {
                channel.floatValue = NONE
                shown = target
            }
            Navigation.Change.CHANNEL -> {
                // A change in the middle of another goes on from the light already on screen, never from a picture.
                val running = channel.floatValue
                val from = if (running >= 0f) ChannelChange.restartAt(ChannelChange.frame(running).flash) else 0f
                changeChannel(channel, from) { shown = target }
            }
        }
    }

    // Same key, newer data (the same screen with a fresher state): draw the newer one, without any change of screen.
    val drawn = if (shown.key == target.key) target else shown
    val on by remember { derivedStateOf { cover.floatValue <= 0f } }
    Box(Modifier.fillMaxSize()) {
        // key: a different screen is a different piece of the tree, with its own remembered state.
        key(drawn.key) { content(drawn, on) }
        TubeCurtain(cover, channel)
        // While the tube changes, taps go nowhere: a tap on the old screen as it goes must not open something else.
        val busy by remember { derivedStateOf { cover.floatValue > 0f || channel.floatValue >= 0f } }
        if (busy) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = null) { },
            )
        }
    }
}

private const val NONE = -1f

/** The tube switching on, one value per frame, until it is fully on. */
private suspend fun switchOn(cover: MutableFloatState) {
    val start = cover.floatValue
    val t0 = withFrameNanos { it }
    while (cover.floatValue > 0f) {
        val now = withFrameNanos { it }
        cover.floatValue = CrtTransition.opening(start, (now - t0) / 1_000_000f)
    }
}

/** A flash from progress [from] to the end; [swap] runs once, the frame the light is complete. */
private suspend fun changeChannel(channel: MutableFloatState, from: Float, swap: () -> Unit) {
    var swapped = ChannelChange.swapped(from)
    if (swapped) swap()
    channel.floatValue = from
    val t0 = withFrameNanos { it }
    while (true) {
        val now = withFrameNanos { it }
        val p = ChannelChange.progress(from, (now - t0) / 1_000_000f)
        if (!swapped && ChannelChange.swapped(p)) { swap(); swapped = true }
        if (p >= 1f) break
        channel.floatValue = p
    }
    if (!swapped) swap()
    channel.floatValue = NONE
}

/** Everything over the screen: the switching on, or the flash of a change of screen. Nothing at rest. */
@Composable
private fun TubeCurtain(cover: MutableFloatState, channel: MutableFloatState) {
    Canvas(Modifier.fillMaxSize()) {
        // Read HERE, in the draw phase: a new value redraws, it does not recompose.
        val c = cover.floatValue
        val p = channel.floatValue
        if (c > 0f) drawCrt(CrtTransition.frame(c, size.width, size.height, LINE_DP.dp.toPx()))
        if (p >= 0f) drawFlash(ChannelChange.frame(p))
    }
}

/**
 * The flash of a change of screen: the whole glass lit in pale phosphor green, brightest in the middle (a radial
 * gradient, as a tube glows), with faint scanlines over it. Both colours are opaque: at the swap nothing shows through.
 */
private fun DrawScope.drawFlash(f: ChannelChange.Frame) {
    if (f.flash <= 0f) return
    val w = size.width
    val h = size.height
    drawRect(
        Brush.radialGradient(listOf(FLASH_CORE, FLASH_EDGE), center = Offset(w / 2f, h / 2f), radius = maxOf(w, h) * 0.75f),
        alpha = f.flash,
    )
    if (f.scan > 0f) {
        val step = 3.dp.toPx()
        var y = 0f
        while (y < h) {
            drawLine(SCANLINE, Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx(), alpha = f.scan)
            y += step
        }
    }
}

/** The tube switching on (the cold start): black around a window that opens from a dot. */
private fun DrawScope.drawCrt(f: CrtTransition.Frame) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h / 2f
    val l = cx - f.halfWidth
    val r = cx + f.halfWidth
    val t = cy - f.halfHeight
    val b = cy + f.halfHeight

    // The dead glass around the window. A tube is black while off, whatever the app's theme.
    drawRect(GLASS, Offset(0f, 0f), Size(w, t))
    drawRect(GLASS, Offset(0f, b), Size(w, h - b))
    drawRect(GLASS, Offset(0f, t), Size(l, b - t))
    drawRect(GLASS, Offset(r, t), Size(w - r, b - t))

    if (r > l) {
        // The thin picture is brighter (the same beam on fewer lines): the line glows as it opens.
        if (f.glow > 0f) drawRect(BEAM, Offset(l, t), Size(r - l, b - t), alpha = f.glow * WASH)
        // Scanlines: dark lines every 3 dp across the window.
        if (f.scan > 0f) {
            val step = 3.dp.toPx()
            var y = t
            while (y < b) {
                drawLine(GLASS, Offset(l, y), Offset(r, y), strokeWidth = 1.dp.toPx(), alpha = f.scan)
                y += step
            }
        }
        // The halo above and below the window: phosphor light bleeding into the glass.
        val halo = HALO_DP.dp.toPx() * f.glow
        if (halo > 1f) {
            val glow = PHOSPHOR.copy(alpha = HALO_ALPHA * f.glow)
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, glow), startY = t - halo, endY = t), Offset(l, t - halo), Size(r - l, halo))
            drawRect(Brush.verticalGradient(listOf(glow, Color.Transparent), startY = b, endY = b + halo), Offset(l, b), Size(r - l, halo))
        }
    }
    // The dot the tube starts from, before it stretches into the line.
    if (f.dot > 0f) {
        val radius = DOT_DP.dp.toPx()
        drawCircle(
            Brush.radialGradient(listOf(BEAM, PHOSPHOR.copy(alpha = 0.6f), Color.Transparent), center = Offset(cx, cy), radius = radius),
            radius = radius,
            center = Offset(cx, cy),
            alpha = f.dot,
        )
    }
}

// The look of the tube: the dark theme's black glass and green phosphor, in both themes (it is a tube, not the page).
private val GLASS = SwiftyPalette.Dark.abyss
private val PHOSPHOR = SwiftyPalette.Dark.phosphor
private val BEAM = Color(0xFFF2FFF0)   // the beam's core: white, a touch of green
private const val WASH = 0.85f          // how white the line gets at most
private const val LINE_DP = 2           // the line a tube shows just before it opens
private const val HALO_DP = 28
private const val HALO_ALPHA = 0.45f
private const val DOT_DP = 10
// The flash: pale phosphor green, a little whiter in the middle (both opaque).
private val FLASH_CORE = Color(0xFFEFFFF1)
private val FLASH_EDGE = Color(0xFFB9F5C6)
private val SCANLINE = SwiftyPalette.Dark.phosphorDim   // darker green lines across the light
