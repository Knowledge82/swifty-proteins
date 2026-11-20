package dev.knowledge82.swiftyproteins.ui

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.knowledge82.swiftyproteins.R
import dev.knowledge82.swiftyproteins.boot.BootSequence
import dev.knowledge82.swiftyproteins.ui.theme.SwiftyColors
import dev.knowledge82.swiftyproteins.ui.theme.SwiftyDataText

/**
 * The cogitator boot screen (design v2, screen 02): the app's launch screen, shown ONCE per start for
 * [BootSequence.DURATION_MILLIS] (over the 1 s of the evaluation sheet), then [onFinished].
 *
 * The timing rule is pure ([BootSequence], tested); this function only reads the clock every frame and draws.
 *
 * @param catalogSize number of ligands for the "DATA-VAULT ... 1,243" line, or null if the list is unreadable
 */
@Composable
fun BootScreen(catalogSize: Int?, onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val reduceMotion = rememberReduceMotion()
    val lines = remember(catalogSize) { BootSequence.lines(catalogSize) }
    var elapsed by remember { mutableLongStateOf(0L) }
    val finish by rememberUpdatedState(onFinished)

    // One clock reading per frame (withFrameMillis waits for the next frame: no busy loop). uptimeMillis is
    // monotonic, so the screen always ends.
    LaunchedEffect(Unit) {
        val start = SystemClock.uptimeMillis()
        while (!BootSequence.isDone(elapsed)) {
            withFrameMillis { }
            elapsed = SystemClock.uptimeMillis() - start
        }
        finish()
    }

    val shown = BootSequence.visibleLines(elapsed, lines.size, reduceMotion)
    CogitatorBackground(modifier) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HeaderPanel(subtitle = "LITANY OF IGNITION // 0xF7")
            CogitatorPanel(modifier = Modifier.fillMaxWidth().weight(1f), fillHeight = true) {
                CenteredColumn(Modifier.fillMaxSize()) {
                    ArtImage(R.drawable.art_skull_cog_art, size = 200.dp)
                }
            }
            CogitatorPanel(label = "BOOT TERMINAL", modifier = Modifier.fillMaxWidth()) {
                for ((i, line) in lines.withIndex()) {
                    // Every line keeps its place (invisible until its time): the panel does not jump while typing.
                    val visible = i < shown
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(line, style = SwiftyDataText.medium, color = if (visible) SwiftyColors.Phosphor else SwiftyColors.Console)
                        if (visible && i == shown - 1 && !reduceMotion) BlinkingCursor()
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    BootSequence.progressBar(BootSequence.progressBlocks(elapsed)),
                    style = SwiftyDataText.medium,
                    color = SwiftyColors.Gold,
                )
            }
            StatusFooter(line = "BOOT SEQUENCE // INITIALIZED", detail = "01001011 :: FORGE-NET ACTIVE")
        }
    }
}
