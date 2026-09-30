package com.klynstudios.hapture.feature.compare

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.klynstudios.hapture.core.compare.SpringCompare
import com.klynstudios.hapture.core.haptics.HapticEffect
import com.klynstudios.hapture.core.haptics.HapticEngine
import com.klynstudios.hapture.core.model.MaterialMotion
import com.klynstudios.hapture.core.model.MaterialSpring
import com.klynstudios.hapture.core.model.MotionPreset
import com.klynstudios.hapture.core.spec.SpringSpec
import com.klynstudios.hapture.data.CompareStore
import com.klynstudios.hapture.feature.common.LocalMotionTokens
import com.klynstudios.hapture.feature.common.LocalSaveMotionToken
import com.klynstudios.hapture.ui.design.AppScreen
import com.klynstudios.hapture.ui.design.Chip
import com.klynstudios.hapture.ui.design.LocalTokens
import com.klynstudios.hapture.ui.design.PrimaryButton
import com.klynstudios.hapture.ui.design.SectionLabel
import com.klynstudios.hapture.ui.design.Stage
import com.klynstudios.hapture.ui.design.TextLink
import com.klynstudios.hapture.ui.design.TopBar
import com.klynstudios.hapture.ui.design.ValueSlider
import com.klynstudios.hapture.ui.theme.MonoSmall
import kotlinx.coroutines.launch

private data class Named(val label: String, val spring: SpringSpec)

/**
 * Two springs, side by side. Race mode releases both objects at once over one
 * shared curve, with a Blend slider that slides between them; Blind mode hides
 * which preset is which so you learn to tell them by feel alone.
 */
@Composable
fun CompareScreen(store: CompareStore, onBack: () -> Unit, seedName: String? = null, seed: SpringSpec? = null) {
    val t = LocalTokens.current
    var blind by remember { mutableStateOf(false) }

    AppScreen(scrollable = true) {
        TopBar("Compare", onBack = onBack)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Race", selected = !blind, onClick = { blind = false })
            Chip("Blind", selected = blind, onClick = { blind = true }, modifier = Modifier.testTag("modeBlind"))
        }
        Spacer(Modifier.height(20.dp))
        if (blind) BlindMode(store) else RaceMode(seed?.let { Named(seedName?.ifBlank { null } ?: "This spring", it) })
        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun options(seed: Named?): List<Named> {
    val tokens = LocalMotionTokens.current
    return listOfNotNull(seed) +
        MotionPreset.entries.map { Named(it.displayName, SpringCompare.fromPreset(it)) } +
        MaterialSpring.entries.map { Named("M3 ${it.label}", it.spring) } +
        tokens.map { Named(it.name, SpringSpec(it.stiffness, it.dampingRatio)) }
}

@Composable
private fun Picker(title: String, selected: Named, all: List<Named>, onPick: (Named) -> Unit) {
    SectionLabel(title, trailing = "${selected.label}  k ${selected.spring.stiffness.toInt()}  \u03b6 ${"%.2f".format(selected.spring.dampingRatio)}")
    Spacer(Modifier.height(10.dp))
    // Opens scrolled to the chosen one, so a pick further along the row (a Material spring, a token) is in view.
    val state = rememberLazyListState(initialFirstVisibleItemIndex = all.indexOfFirst { it.label == selected.label }.coerceAtLeast(0))
    LazyRow(state = state, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(all.size) { i -> Chip(all[i].label, selected = all[i].label == selected.label, onClick = { onPick(all[i]) }) }
    }
    Spacer(Modifier.height(18.dp))
}

@Composable
private fun RaceMode(seed: Named?) {
    val t = LocalTokens.current
    val context = LocalContext.current
    val haptics = remember { HapticEngine(context) }
    val saveToken = LocalSaveMotionToken.current
    val all = options(seed)

    // From an editor: its spring against the nearest Material 3 one, the comparison an Android developer wants first.
    var a by remember { mutableStateOf(seed ?: all.first { it.label == "Snappy" }) }
    var b by remember {
        mutableStateOf(
            seed?.let { s -> all.first { it.label == "M3 ${MaterialMotion.nearest(s.spring).nearest.label}" } }
                ?: all.first { it.label == "Bouncy" }
        )
    }
    var mixT by remember { mutableFloatStateOf(0.5f) }
    val mix = SpringCompare.blend(a.spring, b.spring, mixT)
    val springs = listOf(a.spring, b.spring, mix)

    var raceKey by remember { mutableIntStateOf(0) }
    var elapsedMs by remember { mutableFloatStateOf(-1f) } // -1 = not started; objects rest at the start line
    val span = SpringCompare.spanMs(springs)

    // Runs once per tap of Race and ends when the slowest spring has settled, so the screen is idle otherwise.
    LaunchedEffect(raceKey) {
        if (raceKey == 0) return@LaunchedEffect
        val ticks = SpringCompare.settleTicks(springs.map { it.settleMs })
        var fired = 0
        haptics.play(HapticEffect.CLICK)
        val start = withFrameNanos { it }
        while (true) {
            val ms = withFrameNanos { (it - start) / 1_000_000f }
            elapsedMs = ms
            while (fired < ticks.size && ms >= ticks[fired]) { haptics.play(HapticEffect.TICK); fired++ }
            if (ms >= span) break
        }
    }

    Picker("A", a, all) { a = it }
    Picker("B", b, all) { b = it }

    val pos = if (elapsedMs < 0f) listOf(0f, 0f, 0f) else SpringCompare.positions(springs, elapsedMs / 1000f)
    Stage(height = 168.dp) {
        Canvas(Modifier.fillMaxWidth().height(168.dp)) {
            val left = size.width * 0.1f
            // The finish line sits far enough in that the springiest dot's overshoot still lands inside the stage.
            val peak = springs.maxOf { 1f + it.overshootPercent / 100f }
            val travel = (size.width - left - 16.dp.toPx()) / peak
            val lanes = listOf(0.2f, 0.5f, 0.8f)
            val colors = listOf(t.ink, t.inkSoft, t.inkFaint)
            drawLine(t.line, Offset(left + travel, 12.dp.toPx()), Offset(left + travel, size.height - 12.dp.toPx()), 1.5f)
            lanes.forEachIndexed { i, ly ->
                drawCircle(colors[i], radius = 11.dp.toPx(), center = Offset(left + travel * pos[i], size.height * ly))
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    PrimaryButton("Race", onClick = { raceKey++ }, modifier = Modifier.testTag("raceButton"))

    Spacer(Modifier.height(28.dp))
    SectionLabel("Curves", trailing = "${a.spring.settleMs} / ${b.spring.settleMs} ms")
    Spacer(Modifier.height(10.dp))
    CompareCurves(springs, elapsedMs)

    Spacer(Modifier.height(28.dp))
    SectionLabel("Blend", trailing = "A ${((1f - mixT) * 100).toInt()}  B ${(mixT * 100).toInt()}")
    ValueSlider("Mix", mixT, { mixT = it })
    Text(
        "k ${mix.stiffness.toInt()}   z ${"%.2f".format(mix.dampingRatio)}   settles ${mix.settleMs} ms   overshoot ${mix.overshootPercent.toInt()}%",
        style = MonoSmall, color = t.inkSoft,
    )
    Spacer(Modifier.height(6.dp))
    var naming by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    TextLink("Save the mix as a token", onClick = { naming = true })
    if (naming) {
        AlertDialog(
            onDismissRequest = { naming = false },
            containerColor = t.surface, titleContentColor = t.ink, textContentColor = t.ink,
            title = { Text("Name this spring", style = MaterialTheme.typography.titleLarge) },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, placeholder = { Text("e.g. between") }) },
            confirmButton = {
                TextButton(onClick = { saveToken(name, mix.stiffness, mix.dampingRatio); naming = false }) {
                    Text("Save", color = t.ink, style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = { TextButton(onClick = { naming = false }) { Text("Cancel", color = t.inkSoft, style = MaterialTheme.typography.labelLarge) } },
        )
    }
}

/** A, B and the mix on one shared time axis; a vertical marker follows the race. */
@Composable
private fun CompareCurves(springs: List<SpringSpec>, elapsedMs: Float) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.radiusCard)
    val curves = SpringCompare.curves(springs)
    val span = SpringCompare.spanMs(springs)
    val colors = listOf(t.ink, t.inkSoft, t.inkFaint)
    Canvas(
        Modifier.fillMaxWidth().height(132.dp).clip(shape).background(t.surface).border(t.hairline, t.line, shape),
    ) {
        val padX = 16.dp.toPx()
        val padY = 16.dp.toPx()
        val w = size.width - 2 * padX
        val h = size.height - 2 * padY
        val top = maxOf(1.05f, curves.maxOf { it.max() })
        fun y(v: Float) = padY + h * (1f - v / top)
        drawLine(t.line, Offset(padX, y(1f)), Offset(padX + w, y(1f)), 1.5f)
        curves.forEachIndexed { ci, c ->
            val path = Path()
            c.forEachIndexed { i, v ->
                val x = padX + w * i / (c.size - 1)
                if (i == 0) path.moveTo(x, y(v)) else path.lineTo(x, y(v))
            }
            drawPath(path, colors[ci], style = Stroke(if (ci == 2) 1.5.dp.toPx() else 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        if (elapsedMs in 0f..span.toFloat()) {
            val x = padX + w * elapsedMs / span
            drawLine(t.ink.copy(alpha = 0.35f), Offset(x, padY / 2), Offset(x, size.height - padY / 2), 1.5f)
        }
    }
}

@Composable
private fun BlindMode(store: CompareStore) {
    val t = LocalTokens.current
    val context = LocalContext.current
    val haptics = remember { HapticEngine(context) }
    var round by remember { mutableStateOf(SpringCompare.newRound()) }
    var active by remember { mutableStateOf(0) } // 0 = A, 1 = B
    var streak by remember { mutableIntStateOf(0) }
    var best by remember { mutableIntStateOf(store.bestStreak()) }
    var guessed by remember { mutableStateOf<MotionPreset?>(null) }

    Text(
        "Two springs, unnamed. Drag the dot and let go on each to feel them, then say which preset A is.",
        style = MaterialTheme.typography.bodyMedium, color = t.inkSoft,
    )
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip("A", selected = active == 0, onClick = { active = 0 })
        Chip("B", selected = active == 1, onClick = { active = 1 })
    }
    Spacer(Modifier.height(12.dp))
    FeelStage(if (active == 0) round.springA() else round.springB(), haptics)

    Spacer(Modifier.height(24.dp))
    SectionLabel("A is…", trailing = "streak $streak  best $best")
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(round.a, round.b).sortedBy { it.displayName }.forEach { p ->
            Chip(p.displayName, selected = guessed == p, modifier = Modifier.testTag("guessChip"), onClick = {
                if (guessed == null) {
                    guessed = p
                    val ok = round.isCorrect(p)
                    streak = SpringCompare.nextStreak(streak, ok)
                    best = store.record(streak)
                    haptics.play(if (ok) HapticEffect.SUCCESS else HapticEffect.HEAVY)
                }
            })
        }
    }
    guessed?.let { g ->
        Spacer(Modifier.height(14.dp))
        Text(
            if (round.isCorrect(g)) "Correct. A was ${round.a.displayName}, B was ${round.b.displayName}."
            else "Not quite. A was ${round.a.displayName}, B was ${round.b.displayName}.",
            style = MaterialTheme.typography.bodyMedium, color = t.ink,
        )
        Spacer(Modifier.height(14.dp))
        PrimaryButton("Next round", onClick = { round = SpringCompare.newRound(); guessed = null; active = 0 })
    }
}

/** A draggable dot that springs home on release with the given spring, buzzing when it lands. */
@Composable
private fun FeelStage(spring: SpringSpec, haptics: HapticEngine) {
    val t = LocalTokens.current
    val scope = rememberCoroutineScope()
    val x = remember { Animatable(0f) }
    var maxPx by remember { mutableFloatStateOf(1f) }
    Stage(height = 168.dp) {
        Box(
            Modifier
                .fillMaxWidth().height(168.dp)
                .pointerInput(spring) {
                    maxPx = size.width * 0.38f
                    detectDragGestures(
                        onDragStart = { scope.launch { x.stop() }; haptics.play(HapticEffect.SOFT) },
                        onDragEnd = {
                            scope.launch {
                                x.animateTo(0f, spring(dampingRatio = spring.dampingRatio, stiffness = spring.stiffness, visibilityThreshold = 0.5f))
                                haptics.play(HapticEffect.TICK)
                            }
                        },
                        onDrag = { change, delta ->
                            change.consume()
                            scope.launch { x.snapTo((x.value + delta.x).coerceIn(-maxPx, maxPx)) }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .androidxOffset(x.value)
                    .background(t.ink, CircleShape),
            )
        }
    }
}

private fun Modifier.androidxOffset(px: Float): Modifier =
    this.then(Modifier.graphicsLayer { translationX = px })
