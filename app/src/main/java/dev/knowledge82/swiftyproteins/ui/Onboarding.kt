package dev.knowledge82.swiftyproteins.ui

/**
 * The first-run tutorial (bonus VII.2 "onboarding", document 21): four pages, shown once after the boot screen and
 * again from the settings. Pure, tested: the pages, the page arithmetic and the swipe rule.
 */
object Onboarding {

    enum class Art { SERVO_SKULL, TECH_PRIEST, SERVITOR, EMBLEM }

    data class Page(val tab: String, val title: String, val lines: List<String>, val art: Art)

    val pages = listOf(
        Page(
            tab = "RITE I // THE INDEX",
            title = "The Data-Vault Index",
            lines = listOf(
                "Search the ligands by their ID.",
                "Tap a line to load its 3D pattern. The phone keeps a copy, so it opens again without a connection.",
                "The star keeps a favourite; the star by the search box shows only those.",
            ),
            art = Art.SERVO_SKULL,
        ),
        Page(
            tab = "RITE II // THE AUSPEX",
            title = "Look around the molecule",
            lines = listOf(
                "Drag to turn it, pinch to zoom.",
                "Tap an atom for its name and position, or a stick for its bond.",
                "Double tap an atom to orbit around it; double tap the void to see everything again.",
            ),
            art = Art.TECH_PRIEST,
        ),
        Page(
            tab = "RITE III // THE KEYS",
            title = "The cogitator keys",
            lines = listOf(
                "MODEL… switches between ball-and-stick, space-filling, sticks and wireframe.",
                "H hides the hydrogens. LABELS writes each element on its atom.",
                "RULER measures distances, angles and torsions between the atoms you tap.",
            ),
            art = Art.SERVITOR,
        ),
        Page(
            tab = "RITE IV // THE SHARING",
            title = "Share the pattern",
            lines = listOf(
                "SHR shares a picture, saves it to the gallery, or exports .cif, .mol and .glb files.",
                "The cog on the index holds the theme, the atom colours, the sounds and this tutorial.",
            ),
            art = Art.EMBLEM,
        ),
    )

    val last: Int get() = pages.lastIndex

    fun isLast(page: Int): Boolean = page >= last

    fun next(page: Int): Int = (page + 1).coerceAtMost(last)

    fun previous(page: Int): Int = (page - 1).coerceAtLeast(0)

    /** "2 / 4": the position in words, for the eye and for TalkBack. */
    fun counter(page: Int): String = "${page.coerceIn(0, last) + 1} / ${pages.size}"

    /**
     * The page after a horizontal swipe of [dragPx] (negative: the finger went LEFT, which brings the next page, as in
     * every pager). A swipe shorter than [thresholdPx] changes nothing: a hesitant finger is not a decision.
     */
    fun afterSwipe(page: Int, dragPx: Float, thresholdPx: Float): Int = when {
        dragPx <= -thresholdPx -> next(page)
        dragPx >= thresholdPx -> previous(page)
        else -> page
    }
}
