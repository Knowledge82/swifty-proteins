package dev.knowledge82.swiftyproteins.ui

import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.knowledge82.swiftyproteins.R
import dev.knowledge82.swiftyproteins.ui.theme.SwiftyColors
import dev.knowledge82.swiftyproteins.ui.theme.SwiftyDataText
import dev.knowledge82.swiftyproteins.ui.theme.glowing

/*
 * The building blocks of the cogitator look (design v2, document 08c).
 *
 * Every frame is DRAWN IN CODE (paths and lines in drawBehind), not a bitmap: sharp at any screen density and any
 * size. The kit's 9-patch bezel was looked at and not used (document 08c, section 4). Panels are built on
 * Material's Surface, which also STOPS TOUCHES: a tap on a panel over the 3D scene does not rotate the molecule.
 */

// ---------------------------------------------------------------------------------------------------------------
// Motion

/**
 * `true` when the user switched animations off in the system settings (Developer options, or Accessibility
 * "Remove animations": the animator duration scale is 0). Decorative movement (skull bobbing, cursor, cog) stops.
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Frames

/** A chamfered rectangle (corners cut at 45 degrees by [cut] pixels), inset by [inset] pixels. */
private fun chamfer(width: Float, height: Float, cut: Float, inset: Float = 0f): Path = Path().apply {
    val l = inset; val t = inset; val r = width - inset; val b = height - inset
    val c = (cut - inset * 0.4f).coerceAtLeast(0f)
    moveTo(l + c, t); lineTo(r - c, t); lineTo(r, t + c); lineTo(r, b - c)
    lineTo(r - c, b); lineTo(l + c, b); lineTo(l, b - c); lineTo(l, t + c); close()
}

/** Light from above on the bronze: a highlight at the top, the plain bronze, a shadow at the bottom. */
private val MetalHighlight = Color(0xFFD6AA60)
private val MetalShadow = Color(0xFF4A361B)

/**
 * The frame of a cogitator panel (refined against the "8-bit" look, document 08c section 17):
 * - the glass: fill, the CRT texture, and a soft radial GLOW in the middle (a lit tube is brighter at the centre);
 * - very faint scanlines (5 %: felt, not seen);
 * - a bronze edge painted with a vertical GRADIENT (lit from above) instead of one flat colour, and a thin inner line;
 * - rivets with a small highlight.
 * Smooth gradients and soft light are what separate "aged metal and glass" from flat 8-bit blocks.
 */
private fun DrawScope.cogitatorFrame(cut: Dp, fill: Color, texture: ShaderBrush?, scanlines: Boolean, accent: Color) {
    val c = cut.toPx()
    val outer = chamfer(size.width, size.height, c)
    drawPath(outer, fill)
    if (texture != null) drawPath(outer, texture, alpha = 0.55f)
    drawPath(
        outer,
        Brush.radialGradient(
            colors = listOf(SwiftyColors.Phosphor.copy(alpha = 0.07f), Color.Transparent),
            center = Offset(size.width / 2, size.height / 2),
            radius = maxOf(size.width, size.height) * 0.7f,
        ),
    )
    if (scanlines) {
        // CRT scanlines: a 1 px line every 3 dp, barely visible. ONLY inside panels, never over the 3D scene.
        val step = 3.dp.toPx()
        var y = c * 0.5f
        while (y < size.height - c * 0.5f) {
            drawLine(SwiftyColors.PhosphorDim.copy(alpha = 0.05f), Offset(c * 0.3f, y), Offset(size.width - c * 0.3f, y), strokeWidth = 1f)
            y += step
        }
    }
    drawPath(
        outer,
        Brush.verticalGradient(listOf(MetalHighlight, SwiftyColors.Bronze, MetalShadow)),
        style = Stroke(width = 2.5.dp.toPx()),
    )
    drawPath(chamfer(size.width, size.height, c, inset = 4.dp.toPx()), accent.copy(alpha = 0.6f), style = Stroke(width = 1.dp.toPx()))
    val r = 2.5.dp.toPx()
    val d = 8.dp.toPx() + c * 0.3f
    for ((x, y) in listOf(d to d, size.width - d to d, d to size.height - d, size.width - d to size.height - d)) {
        drawCircle(SwiftyColors.Bronze, radius = r, center = Offset(x, y))
        drawCircle(MetalHighlight, radius = r * 0.45f, center = Offset(x - r * 0.3f, y - r * 0.3f))
    }
}

/** The CRT glass texture as a tiling brush (128 px tile from the kit). */
@Composable
private fun rememberCrtBrush(): ShaderBrush {
    val tile = ImageBitmap.imageResource(R.drawable.tex_crt)
    return remember(tile) { ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated)) }
}

/**
 * A cogitator panel: framed console glass with an optional tab label on the top edge ("AUSPEX QUERY").
 *
 * @param label     the tab text, or null for no tab
 * @param accent    the colour of the inner line (brass; error dialogs use optic red)
 * @param fill      the glass colour (over the 3D scene: [SwiftyColors.ConsoleOverlay])
 * @param scanlines faint CRT lines inside
 * @param fillHeight take all the height the panel is given (when the caller sizes it, e.g. with `weight(1f)`), so
 *                   that a child can in turn use `weight(1f)` (the list inside the archive panel)
 */
@Composable
fun CogitatorPanel(
    modifier: Modifier = Modifier,
    label: String? = null,
    cut: Dp = 9.dp,
    accent: Color = SwiftyColors.Brass,
    fill: Color = SwiftyColors.Console,
    scanlines: Boolean = true,
    contentPadding: Dp = 16.dp,
    fillHeight: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val texture = rememberCrtBrush()
    val size = if (fillHeight) Modifier.fillMaxSize() else Modifier.fillMaxWidth()
    Box(modifier = modifier) {
        // A transparent Surface: no drawing of its own, but it is a touch target (taps do not go through).
        Surface(color = Color.Transparent, modifier = size) {
            Column(
                modifier = size
                    .drawBehind { cogitatorFrame(cut, fill, texture, scanlines, accent) }
                    .padding(
                        start = contentPadding,
                        end = contentPadding,
                        top = contentPadding + if (label != null) 6.dp else 0.dp,
                        bottom = contentPadding,
                    ),
                content = content,
            )
        }
        if (label != null) {
            TabLabel(label, modifier = Modifier.align(Alignment.TopCenter).offset(y = (-8).dp))
        }
    }
}

/** The small bronze plate with a console label that sits on a frame edge. */
@Composable
fun TabLabel(text: String, modifier: Modifier = Modifier, ink: Color = SwiftyColors.Gold) {
    Surface(
        modifier = modifier,
        shape = CutCornerShape(3.dp),
        color = SwiftyColors.Plate,
        border = BorderStroke(1.dp, SwiftyColors.Bronze),
    ) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = ink,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 1.dp),
        )
    }
}

/**
 * The screen background: abyss with the iron texture, and the side rails of the cogitator cabinet (bronze lines
 * with rivets). Not used behind the 3D scene (the scene has its own plain dark background).
 */
@Composable
fun CogitatorBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val iron = ImageBitmap.imageResource(R.drawable.tex_iron)
    val brush = remember(iron) { ShaderBrush(ImageShader(iron, TileMode.Repeated, TileMode.Repeated)) }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SwiftyColors.Abyss)
            .background(brush, alpha = 0.5f)
            .drawBehind {
                val x1 = 5.dp.toPx(); val x2 = size.width - 5.dp.toPx()
                for (x in listOf(x1, x2)) {
                    drawLine(SwiftyColors.Bronze, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.5.dp.toPx())
                    var y = 24.dp.toPx()
                    while (y < size.height) {
                        drawCircle(SwiftyColors.Brass, radius = 2.dp.toPx(), center = Offset(x, y))
                        drawCircle(SwiftyColors.Abyss, radius = 0.9.dp.toPx(), center = Offset(x, y))
                        y += 64.dp.toPx()
                    }
                }
            },
        content = content,
    )
}

/**
 * The header of a screen: tab "COGITATOR // MK.VII", the skull-cog emblem, the app name in blackletter and a console
 * subtitle ("DATA-VAULT INDEX // PRIMARY ARCHIVE").
 */
@Composable
fun HeaderPanel(subtitle: String, modifier: Modifier = Modifier, tab: String = "COGITATOR // MK.VII") {
    CogitatorPanel(modifier = modifier.fillMaxWidth(), label = tab, contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.emblem), contentDescription = null, modifier = Modifier.size(44.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Swifty Proteins", style = MaterialTheme.typography.headlineMedium.glowing(SwiftyColors.Gold, 0.3f), color = SwiftyColors.Gold, maxLines = 1)
                Text(subtitle, style = MaterialTheme.typography.labelSmall.glowing(SwiftyColors.Phosphor), color = SwiftyColors.Phosphor, maxLines = 1)
            }
        }
    }
}

/** The status plate at the bottom of a screen: a green lamp, a status line and a dim console line. */
@Composable
fun StatusFooter(line: String, detail: String, modifier: Modifier = Modifier) {
    CogitatorPanel(modifier = modifier.fillMaxWidth(), contentPadding = 10.dp, scanlines = false) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // The status lamp: a green dot with a soft halo (radial gradient), like a lit indicator.
            Box(Modifier.size(16.dp).background(Brush.radialGradient(listOf(SwiftyColors.Phosphor, SwiftyColors.Phosphor.copy(alpha = 0.25f), Color.Transparent)), CircleShape))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(line, style = MaterialTheme.typography.labelSmall.glowing(SwiftyColors.Phosphor), color = SwiftyColors.Phosphor, maxLines = 1)
                Text(detail, style = MaterialTheme.typography.labelSmall, color = SwiftyColors.Ash, maxLines = 1)
            }
        }
    }
}

/** A blackletter title on a framed plate ("Data-Vault Index"), with an optional console line under it. */
@Composable
fun TitlePlate(title: String, subtitle: String?, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = CutCornerShape(10.dp),
            color = SwiftyColors.Iron,
            border = BorderStroke(1.5.dp, SwiftyColors.Brass),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium.glowing(SwiftyColors.Gold, 0.3f),
                color = SwiftyColors.Gold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp),
            )
        }
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.labelSmall.glowing(SwiftyColors.Phosphor), color = SwiftyColors.Phosphor)
        }
    }
}

/**
 * Targeting brackets in the four corners of the 3D viewport (design screens 16-22). Only the corners are drawn:
 * the centre stays clear, and nothing is drawn OVER the molecule.
 */
@Composable
fun CornerBrackets(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        val len = 44.dp.toPx(); val inner = 18.dp.toPx(); val gap = 6.dp.toPx(); val w = 1.5.dp.toPx()
        val corners = listOf(Offset(0f, 0f) to Offset(1f, 1f), Offset(size.width, 0f) to Offset(-1f, 1f),
            Offset(0f, size.height) to Offset(1f, -1f), Offset(size.width, size.height) to Offset(-1f, -1f))
        for ((p, dir) in corners) {
            drawLine(SwiftyColors.Brass, p, Offset(p.x + dir.x * len, p.y), strokeWidth = w)
            drawLine(SwiftyColors.Brass, p, Offset(p.x, p.y + dir.y * len), strokeWidth = w)
            val q = Offset(p.x + dir.x * gap, p.y + dir.y * gap)
            drawLine(SwiftyColors.Bronze, q, Offset(q.x + dir.x * inner, q.y), strokeWidth = 1.dp.toPx())
            drawLine(SwiftyColors.Bronze, q, Offset(q.x, q.y + dir.y * inner), strokeWidth = 1.dp.toPx())
        }
    }
}

/** A thin dim rule between rows. */
@Composable
fun ConsoleRule(modifier: Modifier = Modifier, color: Color = SwiftyColors.PhosphorDim) {
    Canvas(modifier.fillMaxWidth().height(1.dp)) {
        drawLine(color, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Controls (every one at least 48 dp)

/** The main action: mars-red FILL, gold edge, bone text (6.6:1). Never red text. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    ConsoleButton(text, onClick, modifier, enabled, fill = SwiftyColors.MarsRed, edge = SwiftyColors.Gold, ink = SwiftyColors.Bone)
}

/** The other action: console glass, brass edge, phosphor text. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    ConsoleButton(text, onClick, modifier, enabled, fill = SwiftyColors.Console, edge = SwiftyColors.Brass, ink = SwiftyColors.Phosphor)
}

@Composable
private fun ConsoleButton(text: String, onClick: () -> Unit, modifier: Modifier, enabled: Boolean, fill: Color, edge: Color, ink: Color) {
    val shownInk = if (enabled) ink else SwiftyColors.Disabled
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 52.dp),
        shape = CutCornerShape(7.dp),
        color = if (enabled) fill else SwiftyColors.Iron,
        contentColor = shownInk,
        border = BorderStroke(1.5.dp, if (enabled) edge else SwiftyColors.Bronze),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(text.uppercase(), style = MaterialTheme.typography.labelLarge, color = shownInk, textAlign = TextAlign.Center)
        }
    }
}

/**
 * A square console key (52 dp): "H" for hydrogens, "SHR" for share. [active] draws it lit (phosphor edge and text);
 * the state is ALSO announced to TalkBack ([stateDescription]), never only by colour.
 */
@Composable
fun ConsoleKey(
    text: String,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
    stateDescription: String? = null,
) {
    val ink = when {
        !enabled -> SwiftyColors.Disabled
        active -> SwiftyColors.Phosphor
        else -> SwiftyColors.Gold
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .sizeIn(minWidth = 52.dp, minHeight = 52.dp)
            .semantics {
                this.contentDescription = contentDescription
                if (stateDescription != null) this.stateDescription = stateDescription
            },
        shape = CutCornerShape(6.dp),
        color = SwiftyColors.Console,
        contentColor = ink,
        border = BorderStroke(if (active) 2.dp else 1.5.dp, if (active) SwiftyColors.Phosphor else SwiftyColors.Brass),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp)) {
            Text(text, style = if (active) SwiftyDataText.large.glowing(ink) else SwiftyDataText.large, color = ink)
        }
    }
}

/** The "<" back key of the molecule screen: 48 dp, gold chevron. */
@Composable
fun BackKey(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier.size(48.dp)) {
        Icon(painterResource(R.drawable.ic_back), contentDescription = "Back to the list", tint = SwiftyColors.Gold, modifier = Modifier.size(24.dp))
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Art

/** One of the painted illustrations (servo-skull, servitor...), scaled to fit [size]. Decorative: no description. */
@Composable
fun ArtImage(@DrawableRes art: Int, modifier: Modifier = Modifier, size: Dp = 150.dp) {
    Image(
        painter = painterResource(art),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        // The paintings are ~424 px and get scaled: the high-quality filter keeps them smooth instead of blocky.
        filterQuality = FilterQuality.High,
        modifier = modifier.size(size),
    )
}

/**
 * An illustration that floats up and down by 3 dp every 1.4 s (the servo-skull of the loading dialog). Only the
 * position moves (graphicsLayer translation: no new drawing of the image). Still if motion is reduced.
 */
@Composable
fun BobbingArt(@DrawableRes art: Int, modifier: Modifier = Modifier, size: Dp = 150.dp) {
    val reduce = rememberReduceMotion()
    val bob = rememberInfiniteTransition(label = "bob")
    val offset by bob.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1400, easing = LinearEasing), RepeatMode.Reverse),
        label = "bob offset",
    )
    ArtImage(art, modifier.graphicsLayer { translationY = if (reduce) 0f else offset * density }, size)
}

/** A console text cursor "_" that blinks every 0.7 s (steady if motion is reduced). */
@Composable
fun BlinkingCursor(color: Color = SwiftyColors.Phosphor) {
    val reduce = rememberReduceMotion()
    val blink = rememberInfiniteTransition(label = "cursor")
    val alpha by blink.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 700, easing = LinearEasing), RepeatMode.Reverse),
        label = "cursor alpha",
    )
    Text("_", style = SwiftyDataText.medium.glowing(color), color = color.copy(alpha = if (reduce) 1f else if (alpha > 0.5f) 1f else 0f))
}

/** Centred column helper for dialogs and empty states. */
@Composable
fun CenteredColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        content = content,
    )
}
