package dev.knowledge82.swiftyproteins.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.knowledge82.swiftyproteins.R

// Two families (SIL Open Font License 1.1, licences in assets/licenses/fonts):
// - Pirata One (blackletter): titles only, 20 sp and up. Reserved Font Name "Pirata": shipped UNMODIFIED.
// - Share Tech Mono (terminal): everything else. Reserved Font Name "Share": shipped UNMODIFIED.
// Silkscreen (pixel font) was tried and REMOVED: its block letters read as an 8-bit console, not a cogitator
// (document 08c, section 17). Share Tech Mono has ONE weight (Regular), so no style asks for Bold: a synthetic
// bold of a monospace font looks smeared. Emphasis comes from size, colour and the phosphor glow instead.
private val PirataOne = FontFamily(Font(R.font.pirata_one_regular, FontWeight.Normal))
private val ShareTechMono = FontFamily(Font(R.font.share_tech_mono_regular, FontWeight.Normal))

private fun style(family: FontFamily, size: Int, lineHeight: Int, tracking: Float = 0f) =
    TextStyle(fontFamily = family, fontSize = size.sp, lineHeight = lineHeight.sp, letterSpacing = tracking.sp)

/** The type scale (design v2 with Share Tech Mono, document 08c). */
val SwiftyTypography = Typography(
    displayLarge = style(PirataOne, 32, 40),          // "Pattern 001"
    headlineMedium = style(PirataOne, 26, 34),        // "Data-Vault Index", app name
    titleLarge = style(PirataOne, 22, 30),            // dialog flavour title
    titleMedium = style(ShareTechMono, 18, 24, 0.5f), // atom title, emphasised lines
    bodyLarge = style(ShareTechMono, 16, 23),
    bodyMedium = style(ShareTechMono, 15, 22),        // functional sentences
    bodySmall = style(ShareTechMono, 13, 18),         // short data, hints
    labelLarge = style(ShareTechMono, 17, 22, 1.5f),  // buttons (spaced capitals)
    labelMedium = style(ShareTechMono, 15, 21, 0.5f), // status lines
    labelSmall = style(ShareTechMono, 12, 16, 0.8f),  // tab labels, captions
)

/** Console data styles (ids, coordinates): Material 3 has no slot for them. */
object SwiftyDataText {
    val large = style(ShareTechMono, 19, 26, 0.5f)    // list ids, keys
    val medium = style(ShareTechMono, 15, 22)         // console lines, coordinates
    val small = style(ShareTechMono, 13, 18)          // [INSPECT], key letters
}

/**
 * The CRT phosphor glow: the same colour, blurred, behind the letters (a text shadow with no offset). It is what
 * turns "green text" into "a lit tube". Kept faint (45 %) so the letters stay sharp.
 */
fun TextStyle.glowing(color: Color, strength: Float = 0.45f): TextStyle =
    copy(shadow = Shadow(color = color.copy(alpha = strength), offset = Offset.Zero, blurRadius = 10f))
