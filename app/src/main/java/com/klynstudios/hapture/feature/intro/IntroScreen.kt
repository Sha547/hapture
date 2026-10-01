package com.klynstudios.hapture.feature.intro

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.klynstudios.hapture.core.haptics.HapticEffect
import com.klynstudios.hapture.core.haptics.HapticEngine
import com.klynstudios.hapture.core.haptics.HapticPreset
import com.klynstudios.hapture.core.haptics.HapticSupport
import com.klynstudios.hapture.core.model.MotionPreset
import com.klynstudios.hapture.core.physics.ParameterMapping
import com.klynstudios.hapture.data.ExperimentType
import com.klynstudios.hapture.export.SpringMath
import com.klynstudios.hapture.feature.common.icon
import com.klynstudios.hapture.feature.common.label
import com.klynstudios.hapture.feature.common.SpringSquare
import com.klynstudios.hapture.ui.design.AppIcon
import com.klynstudios.hapture.ui.design.LocalReduceMotion
import com.klynstudios.hapture.ui.design.LocalTokens
import com.klynstudios.hapture.ui.design.PrimaryButton
import com.klynstudios.hapture.ui.design.Stage
import com.klynstudios.hapture.ui.design.ValueSlider
import com.klynstudios.hapture.ui.design.canvasBackground
import com.klynstudios.hapture.ui.design.pressable
import com.klynstudios.hapture.ui.theme.MonoSmall
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

private const val PAGES = 5

/**
 * The first thing a new person sees: five short pages, each with something to touch rather than a
 * paragraph to read. Swipe or tap Next; Skip is always there. Home's "How Hapture works" opens it again.
 */
@Composable
fun IntroScreen(onDone: () -> Unit) {
    val t = LocalTokens.current
    val context = LocalContext.current
    val haptics = remember { HapticEngine(context) }
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState { PAGES }
    val last = pager.currentPage == PAGES - 1

    // A light tick each time a page settles, so paging feels like turning something physical.
    LaunchedEffect(pager) {
        var first = true
        snapshotFlow { pager.settledPage }.collect {
            if (!first) haptics.play(HapticEffect.TICK)
            first = false
        }
    }

    BackHandler {
        if (pager.currentPage > 0) scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } else onDone()
    }

    CompositionLocalProvider(LocalContentColor provides t.ink) {
        Column(
            Modifier
                .fillMaxSize()
                .canvasBackground(t)
                .systemBarsPadding()
                .testTag("intro"),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier.fillMaxWidth().widthIn(max = 640.dp).padding(horizontal = 24.dp).height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${pager.currentPage + 1} / $PAGES", style = MonoSmall, color = t.inkSoft)
                Spacer(Modifier.weight(1f))
                if (!last) {
                    Text(
                        "Skip",
                        style = MaterialTheme.typography.labelLarge,
                        color = t.inkSoft,
                        modifier = Modifier
                            .testTag("introSkip")
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onDone)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
            }

            HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
                IntroPage(page, pager, haptics)
            }

            Row(
                Modifier.fillMaxWidth().widthIn(max = 640.dp).padding(horizontal = 24.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PageDots(pager)
                Spacer(Modifier.weight(1f))
                PrimaryButton(
                    label = if (last) "Start tuning" else "Next",
                    modifier = Modifier.testTag("introNext"),
                    onClick = {
                        if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                    },
                )
            }
        }
    }
}

/** The current page's dot stretches into a short bar; the others stay round. */
@Composable
private fun PageDots(pager: PagerState) {
    val t = LocalTokens.current
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(PAGES) { i ->
            val on = i == pager.currentPage
            val width by animateDpAsState(if (on) 22.dp else 6.dp, spring(stiffness = Spring.StiffnessMediumLow), label = "dot")
            Box(Modifier.height(6.dp).width(width).background(if (on) t.ink else t.line, CircleShape))
        }
    }
}

@Composable
private fun IntroPage(page: Int, pager: PagerState, haptics: HapticEngine) {
    val t = LocalTokens.current
    val reduce = LocalReduceMotion.current
    // How far this page is from the centre: 0 when settled, 1 when a full page away. The words drift a
    // little faster than the page and fade, so the swipe has some depth to it.
    val offset = ((pager.currentPage - page) + pager.currentPageOffsetFraction).coerceIn(-1f, 1f)

    // Centred in the page when it fits, scrolling when it doesn't (small phones, large font sizes).
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val pageHeight = maxHeight
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxWidth().heightIn(min = pageHeight),
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(Modifier.height(12.dp))
            Column(
                Modifier.graphicsLayer {
                    if (!reduce) {
                        translationX = offset * size.width * 0.18f
                        alpha = 1f - offset.absoluteValue * 0.8f
                    }
                },
            ) {
                val (kicker, title, body) = WORDS[page]
                Text(kicker.uppercase(), style = MaterialTheme.typography.labelSmall, color = t.inkSoft)
                Spacer(Modifier.height(14.dp))
                // Balanced lines, so a title never leaves one short word dangling on its own.
                Text(
                    title,
                    style = MaterialTheme.typography.displayMedium.copy(fontSize = 32.sp, lineHeight = 36.sp, lineBreak = LineBreak.Heading),
                    color = t.ink,
                )
                Spacer(Modifier.height(14.dp))
                Text(body, style = MaterialTheme.typography.bodyLarge, color = t.inkSoft)
            }
            Spacer(Modifier.height(28.dp))
            when (page) {
                0 -> SpringDemo(MotionPreset.SNAPPY.stiffnessT, MotionPreset.SNAPPY.dampingT, haptics, hint = "Drag the square anywhere and let go.")
                1 -> TwoSlidersDemo(haptics)
                2 -> HapticsDemo(haptics)
                3 -> InteractionsDemo()
                else -> ExportsDemo()
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    }
}

private val WORDS = listOf(
    Triple(
        "Welcome to Hapture",
        "Feel motion before you ship it",
        "Tune how things move in an app with your own hands, then take the result with you as working code.",
    ),
    Triple(
        "The spring",
        "Two sliders shape every motion",
        "Speed is how quickly it snaps back. Bounce is how far it overshoots. Move them, then drag the square again.",
    ),
    Triple(
        "Haptics",
        "Feel it, too",
        "A motion can carry a tap you feel at the right moment. Try the three feels.",
    ),
    Triple(
        "What's inside",
        "13 interactions to tune",
        "From a simple drag to bottom sheets and Android's back swipe. Each one starts from sensible defaults and saves as you go.",
    ),
    Triple(
        "Take it with you",
        "Then take it anywhere",
        "Copy code for your platform or send it to Figma. Built-in checks spot motion that feels off and fix it in one tap.",
    ),
)

/** A square on the stage that follows your finger and springs home with the given slider positions. */
@Composable
private fun SpringDemo(stiffnessT: Float, dampingT: Float, haptics: HapticEngine, hint: String, height: Dp = 170.dp) {
    val t = LocalTokens.current
    SpringSquare(stiffnessT, dampingT, haptics, height = height, testTag = "introDemo")
    Spacer(Modifier.height(10.dp))
    Text(hint, style = MaterialTheme.typography.bodySmall, color = t.inkSoft)
}

/** The two real controls, renamed for a first-timer: Speed is stiffness, Bounce is damping turned around. */
@Composable
private fun TwoSlidersDemo(haptics: HapticEngine) {
    val t = LocalTokens.current
    var speedT by remember { mutableFloatStateOf(0.45f) }
    var bounceT by remember { mutableFloatStateOf(0.55f) }
    val dampingT = 1f - bounceT
    SpringDemo(speedT, dampingT, haptics, hint = "Drag it, change a slider, drag it again.", height = 150.dp)
    Spacer(Modifier.height(18.dp))
    ValueSlider("Speed", speedT, { speedT = it })
    ValueSlider("Bounce", bounceT, { bounceT = it })
    val k = ParameterMapping.stiffness(speedT)
    val z = ParameterMapping.dampingRatio(dampingT)
    Text(
        "Settles in ${SpringMath.settleMs(k, z)} ms, overshoots ${(SpringMath.overshoot(z) * 100).roundToInt()}%",
        style = MonoSmall,
        color = t.inkSoft,
    )
}

/** Three feels as big tiles you tap, plus the honest line about what this phone's motor can do. */
@Composable
private fun HapticsDemo(haptics: HapticEngine) {
    val t = LocalTokens.current
    val feels = listOf(HapticPreset.SOFT, HapticPreset.CRISP, HapticPreset.FIRM)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        feels.forEach { p ->
            val shape = RoundedCornerShape(t.radiusCard)
            Column(
                Modifier
                    .weight(1f)
                    .testTag("introFeel_${p.name.lowercase()}")
                    .pressable(onClick = { haptics.preview(p) }, scaleTo = 0.94f)
                    .clip(shape)
                    .background(t.surface)
                    .border(t.hairline, t.line, shape)
                    .padding(vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // One ring for soft, two for crisp, three for firm: how strong it is, at a glance.
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(feels.indexOf(p) + 1) { Box(Modifier.size(8.dp).border(1.5.dp, t.ink, CircleShape)) }
                }
                Spacer(Modifier.height(12.dp))
                Text(p.label, style = MaterialTheme.typography.titleMedium, color = t.ink)
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    Text("Tap a tile to feel it.", style = MaterialTheme.typography.bodySmall, color = t.inkSoft)
    val support = remember { haptics.support(HapticPreset.FIRM) }
    if (support != null && support != HapticSupport.EXACT) {
        Spacer(Modifier.height(6.dp))
        Text(support.note, style = MaterialTheme.typography.bodySmall, color = t.inkSoft)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InteractionsDemo() {
    val t = LocalTokens.current
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 3,
    ) {
        val types = ExperimentType.entries
        types.forEach { type ->
            val shape = RoundedCornerShape(10.dp)
            Column(
                Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(t.surface)
                    .border(t.hairline, t.line, shape)
                    .padding(vertical = 12.dp, horizontal = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AppIcon(type.icon, tint = t.ink, size = 20.dp)
                Spacer(Modifier.height(8.dp))
                Text(type.label, style = MaterialTheme.typography.bodySmall, color = t.ink, textAlign = TextAlign.Center, maxLines = 2)
            }
        }
        // Fill out a short last row so its tiles keep the same width as the rest instead of stretching.
        repeat((3 - types.size % 3) % 3) { Spacer(Modifier.weight(1f)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExportsDemo() {
    val t = LocalTokens.current
    val targets = listOf("Jetpack Compose", "SwiftUI", "Flutter", "React Native", "Web", "Lottie", "Figma", "Design tokens")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        targets.forEach { name ->
            Text(
                name,
                style = MaterialTheme.typography.labelMedium,
                color = t.ink,
                modifier = Modifier
                    .border(t.hairline, t.line, RoundedCornerShape(t.radiusControl))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
    Spacer(Modifier.height(22.dp))
    Text(
        "Everything stays on your phone. No account, no ads.",
        style = MaterialTheme.typography.bodySmall,
        color = t.inkSoft,
    )
}
