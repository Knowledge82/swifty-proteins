package dev.knowledge82.swiftyproteins.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FrameDiagnosisTest {

    private val ms = 1_000_000L

    @Test
    fun aSummaryOncePerSecondWhileFramesComeAndNothingInBetween() {
        val d = FrameDiagnosis()
        for (i in 0 until 90) assertNull(d.onFrame(i * 11 * ms, 0f, 0f, 30f))   // 0..979 ms
        assertEquals("frames=92 sceneRecompositions=0 contentRecompositions=0 cameraMoves=0", (90..91).map { d.onFrame(it * 11 * ms, 0f, 0f, 30f) }.last())
    }

    @Test
    fun recompositionsAndCameraMovesAreCountedSeparately() {
        val d = FrameDiagnosis()
        d.onFrame(0, 0f, 0f, 30f)
        d.onSceneRecomposed(); d.onContentRecomposed(); d.onContentRecomposed()
        d.onFrame(100 * ms, 0f, 0f, 29f)                // moved
        d.onFrame(200 * ms, 0f, 0f, 29f + 1e-5f)        // a numerical crumb: standing still
        assertEquals("frames=4 sceneRecompositions=1 contentRecompositions=2 cameraMoves=2", d.onFrame(1_000 * ms, 1f, 0f, 29f))
    }

    @Test
    fun eachWindowStartsFromZero() {
        val d = FrameDiagnosis()
        d.onSceneRecomposed()
        d.onFrame(0, 0f, 0f, 0f)
        d.onFrame(1_000 * ms, 0f, 0f, 0f)
        assertEquals("frames=1 sceneRecompositions=0 contentRecompositions=0 cameraMoves=0", d.onFrame(2_000 * ms, 0f, 0f, 0f))
    }
}
