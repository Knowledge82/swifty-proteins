package dev.knowledge82.swiftyproteins.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.knowledge82.swiftyproteins.R

// The three families (SIL Open Font License 1.1, files in res/font, licences in assets/licenses/fonts).
// Cinzel: rare ceremonial headings. Rajdhani: all the interface. Share Tech Mono: ids and coordinates.
// Cinzel comes as a VARIABLE font upstream; we ship two static cuts (400 and 700) because variable weights need
// API 26 and the app starts at API 24.
private val Cinzel = FontFamily(
    Font(R.font.cinzel_regular, FontWeight.Normal),
    Font(R.font.cinzel_bold, FontWeight.Bold),
)
private val Rajdhani = FontFamily(
    Font(R.font.rajdhani_medium, FontWeight.Medium),
    Font(R.font.rajdhani_semibold, FontWeight.SemiBold),
    Font(R.font.rajdhani_bold, FontWeight.Bold),
)
private val ShareTechMono = FontFamily(Font(R.font.share_tech_mono_regular, FontWeight.Normal))

private fun style(family: FontFamily, size: Int, lineHeight: Int, weight: FontWeight, tracking: Float) = TextStyle(
    fontFamily = family,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = tracking.sp,
)

/** The Material 3 text styles, from the design tokens (document 08b). Unlisted styles keep Material's defaults. */
val SwiftyTypography = Typography(
    displayLarge = style(Cinzel, 30, 38, FontWeight.Bold, 0.4f),
    headlineMedium = style(Cinzel, 24, 32, FontWeight.Bold, 0.25f),
    titleLarge = style(Rajdhani, 23, 29, FontWeight.Bold, 0.4f),
    titleMedium = style(Rajdhani, 19, 25, FontWeight.Bold, 0.35f),
    bodyLarge = style(Rajdhani, 18, 26, FontWeight.Medium, 0.15f),
    bodyMedium = style(Rajdhani, 16, 23, FontWeight.Medium, 0.1f),
    bodySmall = style(Rajdhani, 14, 20, FontWeight.SemiBold, 0.2f),
    labelLarge = style(Rajdhani, 16, 21, FontWeight.Bold, 0.5f),
    labelMedium = style(Rajdhani, 14, 20, FontWeight.Bold, 0.6f),
    labelSmall = style(Rajdhani, 12, 18, FontWeight.Bold, 0.8f),
)

/** Monospaced "machine data" styles. Material 3 has no slot for them, so they live here. */
object SwiftyDataText {
    val large = style(ShareTechMono, 19, 26, FontWeight.Normal, 0f)
    val medium = style(ShareTechMono, 15, 22, FontWeight.Normal, 0f)
    val small = style(ShareTechMono, 12, 18, FontWeight.Normal, 0f)
}
