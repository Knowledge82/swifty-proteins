package dev.knowledge82.swiftyproteins.ui.theme

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Chamfered corners (design v2): controls 7 dp, panels 9 dp, dialogs 11 dp. */
val SwiftyShapes = Shapes(
    extraSmall = CutCornerShape(4.dp),
    small = CutCornerShape(7.dp),
    medium = CutCornerShape(9.dp),
    large = CutCornerShape(11.dp),
    extraLarge = CutCornerShape(16.dp),
)
