package dev.knowledge82.swiftyproteins.ui

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.knowledge82.swiftyproteins.R
import dev.knowledge82.swiftyproteins.ui.theme.SwiftyColors
import dev.knowledge82.swiftyproteins.ui.theme.SwiftyDataText
import dev.knowledge82.swiftyproteins.ui.theme.glowing

/**
 * The tutorial (bonus VII.2, document 21): one page at a time, NEXT / BACK / SKIP, or a swipe. The pages slide in the
 * direction of the move (or simply change, if the system asks for no animations).
 *
 * @param onDone called by SKIP and by BEGIN on the last page: the caller marks the tutorial as seen
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit, modifier: Modifier = Modifier) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    // Back on page 2 goes to page 1; on the first page it skips, like SKIP (there is nothing behind the tutorial).
    BackHandler { if (page > 0) page = Onboarding.previous(page) else onDone() }

    val reduce = rememberReduceMotion()
    val threshold = with(LocalDensity.current) { SWIPE_DP.dp.toPx() }
    var drag = 0f

    CogitatorBackground(modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { drag = 0f },
                        onDragEnd = { page = Onboarding.afterSwipe(page, drag, threshold) },
                        onHorizontalDrag = { _, amount -> drag += amount },
                    )
                },
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            HeaderPanel(subtitle = "NOVITIATE INSTRUCTION // ${Onboarding.counter(page)}", tab = "COGITATOR // TUTORIAL")

            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    val duration = Navigation.durationMillis(reduce)
                    val forward = targetState > initialState
                    (slideInHorizontally(tween(duration)) { w -> if (forward) w / 3 else -w / 3 } + fadeIn(tween(duration)))
                        .togetherWith(slideOutHorizontally(tween(duration)) { w -> if (forward) -w / 3 else w / 3 } + fadeOut(tween(duration)))
                },
                label = "tutorial page",
                modifier = Modifier.weight(1f),
            ) { shown ->
                TutorialPage(Onboarding.pages[shown])
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                SecondaryButton("Skip", onClick = onDone, modifier = Modifier.weight(1f))
                if (page > 0) SecondaryButton("Back", onClick = { page = Onboarding.previous(page) }, modifier = Modifier.weight(1f))
                PrimaryButton(
                    if (Onboarding.isLast(page)) "Begin" else "Next",
                    onClick = { if (Onboarding.isLast(page)) onDone() else page = Onboarding.next(page) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * One page. Tall: the picture above the text. Wide (landscape, document 21, §19): the picture on the left, the text
 * on the right. The text scrolls if it does not fit (a small phone in landscape, a big font).
 */
@Composable
private fun TutorialPage(p: Onboarding.Page) {
    CogitatorPanel(label = p.tab, modifier = Modifier.fillMaxSize(), fillHeight = true) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth > maxHeight
            // Read in THIS scope (not inside the Row): @LayoutScopeMarker forbids an outer scope's receiver there.
            val artSize = minOf(150.dp, maxHeight * 0.8f)
            val pageHeight = maxHeight
            if (wide) {
                Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    ArtImage(artOf(p.art), size = artSize)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { PageText(p) }
                }
            } else {
                // A scrolling column gives its content an UNLIMITED height, and "Center" then centres nothing; the
                // inner column is at least the page tall, so a page that fits stays centred and a longer one scrolls.
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    Column(
                        modifier = Modifier.fillMaxWidth().heightIn(min = pageHeight),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        ArtImage(artOf(p.art), size = artSize)
                        Spacer(Modifier.height(12.dp))
                        PageText(p)
                    }
                }
            }
        }
    }
}

@Composable
private fun PageText(p: Onboarding.Page) {
    Column {
        Text(
            p.title,
            style = MaterialTheme.typography.headlineMedium.glowing(SwiftyColors.Gold, 0.3f),
            color = SwiftyColors.Gold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().semantics { heading() },
        )
        Spacer(Modifier.height(10.dp))
        for (line in p.lines) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                Text("> ", style = SwiftyDataText.medium.glowing(SwiftyColors.Phosphor), color = SwiftyColors.Phosphor)
                Text(line, style = MaterialTheme.typography.bodyMedium, color = SwiftyColors.Bone)
            }
        }
    }
}

@DrawableRes
private fun artOf(art: Onboarding.Art): Int = when (art) {
    Onboarding.Art.SERVO_SKULL -> R.drawable.art_servo_skull
    Onboarding.Art.TECH_PRIEST -> R.drawable.art_tech_priest
    Onboarding.Art.SERVITOR -> R.drawable.art_servitor
    Onboarding.Art.EMBLEM -> R.drawable.art_emblem_hologram
}

/** How far a finger must travel sideways to turn the page. */
private const val SWIPE_DP = 64
