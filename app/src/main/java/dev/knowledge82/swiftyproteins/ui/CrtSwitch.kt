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
import androidx.compose.ui.graphics.drawscope.drawCircle
import androidx.compose.ui.graphics.drawscope.drawLine
import androidx.compose.ui.graphics.drawscope.drawRect
import androidx.compose.ui.graphics.verticalGradient
import androidx.compose.ui.unit.dp
import dev.knowledge82.swiftyproteins.ui.theme.SwiftyPalette

/**
 * Shows [target] and changes screens like a cathode-ray tube (document 23): the old screen switches OFF into a dot,
 * the screens are swapped while the tube is dark, and the new one switches ON.
 *
 * WHY A CURTAIN AND NOT A TRANSFORM. The obvious way would be to squeeze the screen itself (graphicsLayer scaleY).
 * The molecule screen holds a SurfaceView (Filament draws into its own window surface): Compose's transforms and
 * alpha do not reach that surface reliably, and the 3D picture would stay full size while its panels squeeze. So
 * nothing is transformed: an opaque curtain is DRAWN OVER the screen, leaving a window that shrinks and grows. Our
 * panels already prove that Compose draws over the SurfaceView, so the curtain covers the 3D scene too.
 *
 * FRAMES. The cover is read only inside the Canvas (draw phase): during a change only the curtain is redrawn, nothing
 * is recomposed. At rest (cover 0) the Canvas draws nothing and asks for no frames: the molecule screen goes IDLE as
 * before (document 21, §18).
 *
 * @param target       the screen the app wants now ([Navigation.screenOf])
 * @param startOff     true on a cold start: the app opens with the tube switching on
 * @param reduceMotion the user asked the system for no animations: screens change at once
 * @param content      draws one screen; the screen it is given may be the OLD one, while it switches off
 */
@Composable
fun CrtSwitch(
    target: Screen,
    startOff: Boolean,
    reduceMotion: Boolean,
    content: @Composable (Screen) -> Unit,
) {
    // The screen on the tube. It lags behind [target] while the old one switches off: the old screen must keep
    // drawing until the curtain hides it, and it carries its own data (a molecule screen carries its molecule).
    var shown by remember { mutableStateOf(target) }
    val cover = remember { mutableFloatStateOf(if (startOff && !reduceMotion) 1f else 0f) }

    // A new screen (by key) restarts this. If it arrives in the middle of a change, the cover continues from where it
    // was: no jump back to a full picture.
    LaunchedEffect(target.key, reduceMotion) {
        if (reduceMotion) {
            shown = target
            cover.floatValue = 0f
            return@LaunchedEffect
        }
        if (shown.key != target.key) {
            animateCover(cover) { start, elapsed -> CrtTransition.closing(start, elapsed) }
            shown = target          // swapped in the dark: the dot is all that is visible
        }
        animateCover(cover) { start, elapsed -> CrtTransition.opening(start, elapsed) }
    }

    // Same key, newer data (the same screen with a fresher state): draw the newer one, without any change of screen.
    val drawn = if (shown.key == target.key) target else shown
    Box(Modifier.fillMaxSize()) {
        // key: a different screen is a different piece of the tree, with its own remembered state.
        key(drawn.key) { content(drawn) }
        CrtCurtain(cover)
        // While the tube changes, taps go nowhere: a tap on the old screen as it fades must not open something else.
        val busy by remember { derivedStateOf { cover.floatValue > 0f } }
        if (busy) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = null) { },
            )
        }
    }
}

/** Moves [cover] by the rule [step] (start, elapsed ms -> new cover), one value per frame, until it reaches 0 or 1. */
private suspend fun animateCover(cover: MutableFloatState, step: (Float, Float) -> Float) {
    val start = cover.floatValue
    val t0 = withFrameNanos { it }
    while (true) {
        val now = withFrameNanos { it }
        val next = step(start, (now - t0) / 1_000_000f)
        cover.floatValue = next
        if (next <= 0f || next >= 1f) return
    }
}

/** The curtain: black around the window, the window washed with light and crossed by scanlines, a halo, the dot. */
@Composable
private fun CrtCurtain(cover: MutableFloatState) {
    Canvas(Modifier.fillMaxSize()) {
        val c = cover.floatValue        // read HERE, in the draw phase: a new value redraws, it does not recompose
        if (c <= 0f) return@Canvas
        drawCrt(CrtTransition.frame(c, size.width, size.height, LINE_DP.dp.toPx()))
    }
}

private fun DrawScope.drawCrt(f: CrtTransition.Frame) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h / 2f
    val l = cx - f.halfWidth
    val r = cx + f.halfWidth
    val t = cy - f.halfHeight
    val b = cy + f.halfHeight

    // The dead glass around the window. A tube is black when off, whatever the app's theme.
    drawRect(GLASS, Offset(0f, 0f), Size(w, t))
    drawRect(GLASS, Offset(0f, b), Size(w, h - b))
    drawRect(GLASS, Offset(0f, t), Size(l, b - t))
    drawRect(GLASS, Offset(r, t), Size(w - r, b - t))

    if (r > l) {
        // The squeezed picture gets brighter, until the window is one glowing line.
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
    // The dot: what is left of the beam when the line has shrunk away.
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
private const val WASH = 0.85f          // how white the line gets at most (the picture never fully disappears)
private const val LINE_DP = 2           // the line a switched-off tube keeps
private const val HALO_DP = 28
private const val HALO_ALPHA = 0.45f
private const val DOT_DP = 10
