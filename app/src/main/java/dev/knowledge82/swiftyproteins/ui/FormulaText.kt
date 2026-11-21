package dev.knowledge82.swiftyproteins.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import dev.knowledge82.swiftyproteins.molecule.MolecularFormula

/**
 * A formula with real subscripts drawn in OUR font: each count is the same digits, lowered and at 75 % size. Not
 * Unicode subscripts (₁₀): neither of our fonts has those glyphs, and Android would take them from another font in the
 * middle of the formula (document 15). Used by the molecule screen and by the cells of the list (document 21).
 */
fun formulaText(parts: List<MolecularFormula.Part>): AnnotatedString = buildAnnotatedString {
    for (part in parts) {
        append(part.element)
        if (part.count > 1) {
            withStyle(SpanStyle(fontSize = 0.75.em, baselineShift = BaselineShift.Subscript)) { append(part.count.toString()) }
        }
    }
}
