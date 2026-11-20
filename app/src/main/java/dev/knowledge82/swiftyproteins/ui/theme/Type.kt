package dev.knowledge82.swiftyproteins.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.knowledge82.swiftyproteins.R

// The two families chosen by the team (SIL Open Font License 1.1, licences in assets/licenses/fonts).
// Pirata One (blackletter): titles only, 20 sp and up; it declares the Reserved Font Name "Pirata", so the file is
// shipped UNMODIFIED. Silkscreen (pixel font): everything else. It looks all-caps and is wide; it has NO typographic
// minus U+2212, so negative numbers use the ASCII "-" (AtomText, tested).
private val PirataOne = FontFamily(Font(R.font.pirata_one_regular, FontWeight.Normal))
private val Silkscreen = FontFamily(
    Font(R.font.silkscreen_regular, FontWeight.Normal),
    Font(R.font.silkscreen_bold, FontWeight.Bold),
)

private fun style(family: FontFamily, size: Int, lineHeight: Int, weight: FontWeight = FontWeight.Normal, tracking: Float = 0f) =
    TextStyle(fontFamily = family, fontSize = size.sp, lineHeight = lineHeight.sp, fontWeight = weight, letterSpacing = tracking.sp)

/** The type scale of design v2 (document 08c). */
val SwiftyTypography = Typography(
    displayLarge = style(PirataOne, 32, 40),       // "Pattern 001", boot
    headlineMedium = style(PirataOne, 26, 34),     // "Data-Vault Index", app name
    titleLarge = style(PirataOne, 22, 30),         // dialog flavour title
    titleMedium = style(Silkscreen, 16, 24, FontWeight.Bold),   // atom title, list ids
    bodyLarge = style(Silkscreen, 14, 23),         // functional sentences
    bodyMedium = style(Silkscreen, 14, 23),
    bodySmall = style(Silkscreen, 11, 17),         // short data only
    labelLarge = style(Silkscreen, 16, 23, FontWeight.Bold),    // buttons
    labelMedium = style(Silkscreen, 14, 23, FontWeight.Bold),   // status lines
    labelSmall = style(Silkscreen, 11, 17),        // tab labels, captions
)

/** Console data styles (ids, coordinates): Material 3 has no slot for them. */
object SwiftyDataText {
    val large = style(Silkscreen, 16, 24, FontWeight.Bold)
    val medium = style(Silkscreen, 14, 23)
    val small = style(Silkscreen, 11, 17)
}
