package dev.knowledge82.swiftyproteins.ui

import android.graphics.Bitmap
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
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import dev.knowledge82.swiftyproteins.ui.theme.SwiftyPalette

/**
 * Shows [target], and changes from one screen to the next like an old television (documents 23 and 26):
 *
 * - the COLD START: the tube switches ON over the boot screen, a dot that stretches into a line that opens into the
 *   picture ([CrtTransition]). [content] is told when the tube is fully on, so the boot sequence starts only then;
 * - a molecule that was just DOWNLOADED: a CUT, at once (its loading dialog was the transition) ([Navigation.change]);
 * - every other change: a CHANGE OF CHANNEL, a short burst of static with the screens swapped under it, and the bar
 *   of the vertical hold rolling down the new picture ([ChannelChange]).
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
 * @param content      draws one screen, and whether the tube is fully ON ([on]); while static covers it, the screen it
 *                     is given may be the OLD one
 */
@Composable
fun CrtSwitch(
    target: Screen,
    startOff: Boolean,
    reduceMotion: Boolean,
    content: @Composable (screen: Screen, on: Boolean) -> Unit,
) {
    // The screen on the tube. It lags behind [target] while the static rises: the old screen keeps drawing until the
    // static hides it, and it carries its own data (a molecule screen carries its molecule).
    var shown by remember { mutableStateOf(target) }
    // The switching on (cold start only): 1 = off, 0 = fully on.
    val cover = remember { mutableFloatStateOf(if (startOff && !reduceMotion) 1f else 0f) }
    // The change of channel: its progress 0..1, or NONE when no change is running.
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
                // A change in the middle of another goes on from the static already on screen, never from a picture.
                val running = channel.floatValue
                val from = if (running >= 0f) ChannelChange.restartAt(ChannelChange.frame(running).snow) else 0f
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

/** A change of channel from progress [from] to the end; [swap] runs once, the frame the static is complete. */
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

/** Everything over the screen: the switching on, or the static of a change of channel. Nothing at rest. */
@Composable
private fun TubeCurtain(cover: MutableFloatState, channel: MutableFloatState) {
    val snow = remember { snowBrush() }
    Canvas(Modifier.fillMaxSize()) {
        // Read HERE, in the draw phase: a new value redraws, it does not recompose.
        val c = cover.floatValue
        val p = channel.floatValue
        if (c > 0f) drawCrt(CrtTransition.frame(c, size.width, size.height, LINE_DP.dp.toPx()))
        if (p >= 0f) drawChannel(ChannelChange.frame(p), p, snow)
    }
}

/**
 * The static of a change of channel: the glass goes dark under it, the snow (a tile of random grains, shifted to a
 * new place every frame so it boils), scanlines, and after the swap the bright bar of the vertical hold rolling down.
 */
private fun DrawScope.drawChannel(f: ChannelChange.Frame, progress: Float, snow: ShaderBrush) {
    val w = size.width
    val h = size.height
    if (f.snow > 0f) {
        drawRect(GLASS, alpha = f.snow)
        // A different offset each frame, from the progress itself: no random generator to allocate per frame.
        val k = (progress * 9973f).toInt()
        val dx = ((k * 37) % SNOW_TILE).toFloat()
        val dy = ((k * 61) % SNOW_TILE).toFloat()
        val grain = SNOW_GRAIN_DP.dp.toPx()
        scale(grain, pivot = Offset.Zero) {
            translate(-dx, -dy) {
                drawRect(snow, topLeft = Offset.Zero, size = Size(w / grain + 2 * SNOW_TILE, h / grain + 2 * SNOW_TILE), alpha = f.snow * SNOW_ALPHA)
            }
        }
    }
    if (f.scan > 0f) {
        val step = 3.dp.toPx()
        var y = 0f
        while (y < h) {
            drawLine(GLASS, Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx(), alpha = f.scan)
            y += step
        }
    }
    val roll = f.roll
    if (roll != null) {
        // The vertical hold catching the new picture: a soft bright bar that rolls down once and fades.
        val band = ROLL_DP.dp.toPx()
        val y = roll * (h + band) - band
        val light = BEAM.copy(alpha = ROLL_ALPHA * (1f - roll))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, light, Color.Transparent), startY = y, endY = y + band), Offset(0f, y), Size(w, band))
    }
}

/**
 * The snow: a square tile of random grains in the tube's green-grey, made once. Seeded, so it is always the same
 * tile (nothing random hides in a test or a screenshot); it looks random because it moves every frame.
 */
private fun snowBrush(): ShaderBrush {
    val rnd = java.util.Random(SNOW_SEED)
    val pixels = IntArray(SNOW_TILE * SNOW_TILE) {
        val v = rnd.nextInt(256)
        val g = v
        val rb = v * 85 / 100   // a touch of phosphor green
        (0xFF shl 24) or (rb shl 16) or (g shl 8) or rb
    }
    val bitmap = Bitmap.createBitmap(pixels, SNOW_TILE, SNOW_TILE, Bitmap.Config.ARGB_8888)
    return ShaderBrush(ImageShader(bitmap.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
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
// The static: a 128-pixel tile, each grain 2 dp, almost opaque; the rolling bar.
private const val SNOW_TILE = 128
private const val SNOW_GRAIN_DP = 2
private const val SNOW_ALPHA = 0.9f
private const val SNOW_SEED = 40_000L
private const val ROLL_DP = 90
private const val ROLL_ALPHA = 0.35f
