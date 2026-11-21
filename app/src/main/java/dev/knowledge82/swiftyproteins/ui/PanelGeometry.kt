package dev.knowledge82.swiftyproteins.ui

/**
 * Where the rivets of a cogitator panel go and how far its content must stay from the top and the bottom, in dp.
 * Pure, tested (document 21, §17: a fix after the first look on the phone).
 *
 * The bug it fixes: the padding used to be `contentPadding` on every side PLUS 6 dp on top when the panel has a tab
 * label. Two problems: (1) the content sat LOWER than the middle (more room above than below), and (2) a panel with
 * a small padding (the search box: 6 dp) put its content over the rivets of the bottom corners.
 *
 * The rule now: the same room above and below, and never less than what clears the rivets (and the tab label, which
 * hangs 11 dp into the panel). The sides keep the requested padding: the rivets live in the top and bottom bands.
 */
object PanelGeometry {

    /** Rivet radius. */
    const val RIVET_RADIUS = 2.5f

    /** Distance from each side to the centre of a corner rivet: a little more for a bigger corner cut. */
    fun rivetInset(cut: Float): Float = 8f + cut * 0.3f

    /** How far into the panel the tab label hangs (it is moved up 8 dp; it is about 19 dp tall). */
    const val LABEL_REACH = 11f

    /** Air between a rivet (or the label) and the content. */
    const val GAP = 1.5f

    /** The padding above AND below the content: the requested one, or more if the rivets or the label need it. */
    fun verticalPadding(contentPadding: Float, cut: Float, hasLabel: Boolean): Float {
        val rivets = rivetInset(cut) + RIVET_RADIUS + GAP
        val label = if (hasLabel) LABEL_REACH + GAP else 0f
        return maxOf(contentPadding, rivets, label)
    }
}
