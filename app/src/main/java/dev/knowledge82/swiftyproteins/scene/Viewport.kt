package dev.knowledge82.swiftyproteins.scene

/**
 * Which part of the 3D view the panels cover, so that the camera aims at the WINDOW left between them
 * (SceneView's `contentPadding`, document 21, §18). Pure, tested.
 *
 * Both rectangles in the same coordinates (pixels of the app window): the whole screen, and the visible window.
 */
object Viewport {

    /** The covered band on each side (pixels) and the window's shape, rounded ([SceneFraming.aspectKey]). */
    data class Cover(val left: Float, val top: Float, val right: Float, val bottom: Float, val aspectKey: Float)

    /** Null while either rectangle is unknown or empty (the first frame, before the layout): the scene waits. */
    fun cover(
        screenLeft: Float, screenTop: Float, screenRight: Float, screenBottom: Float,
        windowLeft: Float, windowTop: Float, windowRight: Float, windowBottom: Float,
    ): Cover? {
        val w = windowRight - windowLeft
        val h = windowBottom - windowTop
        if (w <= 0f || h <= 0f || screenRight <= screenLeft || screenBottom <= screenTop) return null
        return Cover(
            left = (windowLeft - screenLeft).coerceAtLeast(0f),
            top = (windowTop - screenTop).coerceAtLeast(0f),
            right = (screenRight - windowRight).coerceAtLeast(0f),
            bottom = (screenBottom - windowBottom).coerceAtLeast(0f),
            aspectKey = SceneFraming.aspectKey(w, h),
        )
    }
}
