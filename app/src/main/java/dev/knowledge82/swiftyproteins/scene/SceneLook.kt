package dev.knowledge82.swiftyproteins.scene

import kotlin.math.pow

/**
 * How the 3D scene looks in each theme (bonus VII.2 "dark mode, including the 3D view", document 21). Pure, tested.
 *
 * Dark: the black of SceneView's own background, as always. Light: a parchment background, deeper halos (a pale gold
 * halo vanishes on parchment) and a weaker rim light (it exists to separate the balls from BLACK).
 *
 * @param backdrop   the background as 0xRRGGBB (what the eye should see), or null for SceneView's default black
 * @param selection  colour of the selection halo and the ruler (0xRRGGBB) and its opacity
 * @param element    colour of the halos of the highlighted element and its opacity
 */
data class SceneLook(
    val backdrop: Int?,
    val selection: Int,
    val selectionAlpha: Float,
    val element: Int,
    val elementAlpha: Float,
    val rulerAlpha: Float,
    val keyLux: Float,
    val fillLux: Float,
    val rimLux: Float,
) {
    companion object {
        val DARK = SceneLook(
            backdrop = null,
            selection = 0x43E07A, selectionAlpha = 0.35f,
            element = 0xE0B55C, elementAlpha = 0.28f,
            rulerAlpha = 0.7f,
            keyLux = 10_000f, fillLux = 3_000f, rimLux = 5_000f,
        )

        val LIGHT = SceneLook(
            backdrop = 0xD8CCAE,
            selection = 0x1B7A40, selectionAlpha = 0.42f,
            element = 0x9A5B00, elementAlpha = 0.38f,
            rulerAlpha = 0.85f,
            keyLux = 10_000f, fillLux = 3_500f, rimLux = 2_000f,
        )

        fun of(dark: Boolean): SceneLook = if (dark) DARK else LIGHT

        /**
         * The colour to give Filament's skybox so that the SCREEN shows [rgb].
         *
         * The skybox colour goes through the tone mapper SceneView uses (Khronos "PBR Neutral"), which subtracts a small
         * offset (0.04 in linear light) from every pixel whose darkest channel is 0.08 or more, and compresses the
         * highlights above 0.76. A parchment chosen below that knee only needs the offset added back:
         *
         *     skybox = linear(rgb) + 0.04       -> tone mapped: linear(rgb)   -> on screen: rgb
         *
         * NOT verified on the phone: if the skybox colour is also scaled by the camera exposure, the screen will show
         * something else, and §8 of document 21 says what to change.
         */
        fun skyboxColor(rgb: Int): FloatArray {
            val r = linear((rgb shr 16) and 0xFF); val g = linear((rgb shr 8) and 0xFF); val b = linear(rgb and 0xFF)
            return floatArrayOf((r + OFFSET).toFloat(), (g + OFFSET).toFloat(), (b + OFFSET).toFloat(), 1f)
        }

        /** The PBR Neutral tone mapper (Khronos, 2024), forward. Here to TEST that [skyboxColor] lands where it should. */
        fun pbrNeutral(c: DoubleArray): DoubleArray {
            val x = minOf(c[0], c[1], c[2])
            val offset = if (x < 0.08) x - 6.25 * x * x else OFFSET
            val d = DoubleArray(3) { c[it] - offset }
            val peak = maxOf(d[0], d[1], d[2])
            if (peak < START_COMPRESSION) return d
            val dd = 1 - START_COMPRESSION
            val newPeak = 1 - dd * dd / (peak + dd - START_COMPRESSION)
            val scaled = DoubleArray(3) { d[it] * newPeak / peak }
            val g = 1 - 1 / (DESATURATION * (peak - newPeak) + 1)
            return DoubleArray(3) { scaled[it] * (1 - g) + newPeak * g }
        }

        /** sRGB 0..255 -> linear light 0..1. */
        fun linear(channel: Int): Double {
            val c = channel / 255.0
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }

        private const val OFFSET = 0.04
        const val START_COMPRESSION = 0.8 - 0.04
        private const val DESATURATION = 0.15
    }
}
