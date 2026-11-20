package dev.knowledge82.swiftyproteins.ui

import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.knowledge82.swiftyproteins.R
import dev.knowledge82.swiftyproteins.ui.theme.SwiftyColors
import dev.knowledge82.swiftyproteins.ui.theme.SwiftyDataText

/*
 * The building blocks of the "Cult of the Machine" look (document 08b). Each one is a thin wrapper around a
 * Material 3 component: the BEHAVIOUR (clicks, accessibility, ripples) is Material's; only colours, shapes and
 * borders change. Every touch target is at least 48 dp.
 */

/** An iron plate: cut corners, 1 dp border, no shadow (the design replaces shadows by borders). */
@Composable
fun SwiftyPlate(
    modifier: Modifier = Modifier,
    cut: Int = 8,
    fill: Color = SwiftyColors.Plate,
    border: Color = SwiftyColors.Brass,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = CutCornerShape(cut.dp),
        color = fill,
        contentColor = SwiftyColors.Bone,
        border = BorderStroke(1.dp, border),
        content = content,
    )
}

/** The main action: martian-red FILL with bone text (6.9:1). Never red text. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
) {
    ActionButton(text, onClick, modifier, icon, fill = SwiftyColors.MartianRed, border = SwiftyColors.Blood, ink = SwiftyColors.Bone)
}

/** The other action: iron with a brass border and gold text. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
) {
    ActionButton(text, onClick, modifier, icon, fill = SwiftyColors.Iron, border = SwiftyColors.Brass, ink = SwiftyColors.Gold)
}

@Composable
private fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    @DrawableRes icon: Int?,
    fill: Color,
    border: Color,
    ink: Color,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        shape = CutCornerShape(6.dp),
        color = fill,
        contentColor = ink,
        border = BorderStroke(1.dp, border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                // contentDescription = null: the text next to it already says what the button does.
                Icon(painterResource(icon), contentDescription = null, tint = ink, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
            }
            Text(text.uppercase(), style = MaterialTheme.typography.labelLarge, color = ink)
        }
    }
}

/** A square 48 dp plate with an icon (the Back button of the molecule screen). */
@Composable
fun IconPlateButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    // Disabled (e.g. Share while an image is being prepared): ash icon, no clicks. The status line says why.
    val ink = if (enabled) SwiftyColors.Bone else SwiftyColors.Ash
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(48.dp),
        shape = CutCornerShape(5.dp),
        color = SwiftyColors.Plate,
        contentColor = ink,
        border = BorderStroke(1.dp, SwiftyColors.Rivet),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = contentDescription, tint = ink, modifier = Modifier.size(22.dp))
        }
    }
}

/**
 * The hydrogen switch: "H ✓" (gold border) or "H ×" (grey). The state is written AND drawn AND announced to
 * TalkBack (stateDescription), never shown by colour alone.
 */
@Composable
fun HydrogenChip(on: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val ink = if (on) SwiftyColors.Gold else SwiftyColors.Ash
    Surface(
        onClick = onToggle,
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .semantics {
                contentDescription = "Hydrogen atoms"
                stateDescription = if (on) "shown" else "hidden"
            },
        shape = CutCornerShape(5.dp),
        color = SwiftyColors.Plate,
        contentColor = ink,
        border = BorderStroke(1.dp, if (on) SwiftyColors.Gold else SwiftyColors.Rivet),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text("H", style = SwiftyDataText.large, color = ink)
            Spacer(Modifier.width(4.dp))
            Icon(
                painterResource(if (on) R.drawable.ic_check else R.drawable.ic_close),
                contentDescription = null,
                tint = ink,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/**
 * The loading cog. Turns once every 1.3 s; stands STILL if the user switched animations off in the system settings
 * (Developer options or Accessibility "Remove animations" set the animator scale to 0).
 */
@Composable
fun RotatingCog(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val animationsOff = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val turn = rememberInfiniteTransition(label = "cog")
    val angle by turn.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1300, easing = LinearEasing)),
        label = "cog angle",
    )
    Image(
        painter = painterResource(R.drawable.loading_cog),
        contentDescription = null,
        modifier = modifier.size(48.dp).rotate(if (animationsOff) 0f else angle),
    )
}

/** The emblem (gear + molecule) next to the app name: the top bar of the list. */
@Composable
fun BrandBar(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(painterResource(R.drawable.emblem), contentDescription = null, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(12.dp))
            Text("SWIFTY PROTEINS", style = MaterialTheme.typography.titleMedium, color = SwiftyColors.Bone)
        }
        // The brass seam that closes the bar.
        Canvas(Modifier.fillMaxWidth().height(1.dp)) {
            drawLine(SwiftyColors.Brass, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
        }
    }
}

/**
 * The ornament between blocks: a thin rivet-coloured line with a small brass bracket and a gold dot in the middle.
 * Drawn with a Canvas (vectors, no image), 12 dp tall.
 */
@Composable
fun SeamDivider(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(12.dp)) {
        val y = size.height / 2
        val cx = size.width / 2
        val stroke = 1.dp.toPx()
        val half = 36.dp.toPx()
        val rise = 5.dp.toPx()
        drawLine(SwiftyColors.Rivet, Offset(0f, y), Offset(cx - half, y), strokeWidth = stroke)
        drawLine(SwiftyColors.Rivet, Offset(cx + half, y), Offset(size.width, y), strokeWidth = stroke)
        val bracket = Path().apply {
            moveTo(cx - half, y)
            lineTo(cx - half + rise * 2, y - rise)
            lineTo(cx + half - rise * 2, y - rise)
            lineTo(cx + half, y)
        }
        drawPath(bracket, SwiftyColors.Brass, style = Stroke(width = stroke))
        drawCircle(SwiftyColors.Gold, radius = 1.5.dp.toPx(), center = Offset(cx, y + 1.dp.toPx()))
    }
}

/** Small caps label above a block ("INSPECTION", "ELEMENTS / CPK"). */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = SwiftyColors.Ash) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = color, modifier = modifier)
}

/** A round dark well with an icon: the head of every dialog. */
@Composable
fun IconWell(@DrawableRes icon: Int, tint: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.size(56.dp), shape = CircleShape, color = SwiftyColors.Iron) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
        }
    }
}

/** A modal panel: plate, 12 dp cut, brass border, a brass rule on top, content centred. */
@Composable
fun DialogPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    SwiftyPlate(modifier = modifier.fillMaxWidth(), cut = 12, fill = SwiftyColors.Plate, border = SwiftyColors.Rivet) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Canvas(Modifier.fillMaxWidth().height(1.dp)) {
                drawLine(SwiftyColors.Brass, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
            }
            Spacer(Modifier.height(20.dp))
            content()
        }
    }
}
