package com.klynstudios.hapture.feature.editor

import com.klynstudios.hapture.core.spec.SpringSpec
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.klynstudios.hapture.ui.design.quietClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.klynstudios.hapture.core.haptics.HapticEngine
import com.klynstudios.hapture.core.haptics.HapticEvent
import com.klynstudios.hapture.core.haptics.HapticPreset
import com.klynstudios.hapture.core.model.MotionPreset
import com.klynstudios.hapture.core.physics.ParameterMapping
import com.klynstudios.hapture.core.spec.MotionSpec
import com.klynstudios.hapture.core.spec.TriggerKind
import com.klynstudios.hapture.core.spec.Chain
import com.klynstudios.hapture.core.spec.TriggerSpecs
import com.klynstudios.hapture.data.ExperimentEntity
import com.klynstudios.hapture.data.ExperimentRepository
import com.klynstudios.hapture.data.ExperimentType
import com.klynstudios.hapture.export.DesignSpec
import com.klynstudios.hapture.export.SpringMath
import com.klynstudios.hapture.export.platform.ComposeGenericGenerator
import com.klynstudios.hapture.feature.common.label
import com.klynstudios.hapture.ui.design.AppScreen
import com.klynstudios.hapture.ui.design.Chip
import com.klynstudios.hapture.ui.design.LocalTokens
import com.klynstudios.hapture.ui.design.Stage
import com.klynstudios.hapture.ui.design.TextLink
import com.klynstudios.hapture.ui.design.TopBar
import com.klynstudios.hapture.ui.design.reveal
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * One editor for every trigger-decided motion (toggle, button press, tab
 * indicator, staggered list, like burst). A tap or press sets a target and
 * the same tunable spring drives the change; the neutral [MotionSpec] built
 * by [TriggerSpecs] is what every export target is generated from.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TriggerScreen(
    experimentId: Long,
    kind: TriggerKind,
    repository: ExperimentRepository,
    onBack: () -> Unit,
) {
    val t = LocalTokens.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val haptics = remember { HapticEngine(context) }

    var stiffnessT by remember { mutableFloatStateOf(0.6f) }
    var dampingT by remember { mutableFloatStateOf(0.55f) }
    var amountT by remember { mutableFloatStateOf(0.5f) }
    var staggerT by remember { mutableFloatStateOf(0.4f) }
    var chainProperty by remember { mutableStateOf<String?>(null) }
    var chainAtT by remember { mutableFloatStateOf(0.5f) }
    var chainLagT by remember { mutableFloatStateOf(0.2f) }
    var chainStiffT by remember { mutableFloatStateOf(0.6f) }
    var chainDampT by remember { mutableFloatStateOf(0.55f) }
    var selectedPreset by remember { mutableStateOf<MotionPreset?>(null) }
    var hapticPreset by remember { mutableStateOf(HapticPreset.CRISP) }
    SideEffect { haptics.preset = hapticPreset }

    var loaded by remember { mutableStateOf<ExperimentEntity?>(null) }
    LaunchedEffect(experimentId) {
        repository.get(experimentId)?.let { e ->
            loaded = e
            stiffnessT = e.stiffnessT
            dampingT = e.dampingT
            amountT = e.triggerAmountT ?: 0.5f
            staggerT = e.triggerStaggerT ?: 0.4f
            hapticPreset = e.hapticPreset
            chainProperty = e.chainProperty
            chainAtT = e.chainAtT ?: 0.5f
            chainLagT = e.chainLagT ?: 0.2f
            chainStiffT = e.chainStiffnessT ?: 0.6f
            chainDampT = e.chainDampingT ?: 0.55f
        }
    }

    fun persist() {
        val e = loaded ?: return
        scope.launch {
            repository.save(
                e.copy(
                    stiffnessT = stiffnessT, dampingT = dampingT,
                    triggerAmountT = amountT, triggerStaggerT = staggerT,
                    hapticPreset = hapticPreset, updatedAt = System.currentTimeMillis(),
                    chainProperty = chainProperty, chainAtT = chainAtT, chainLagT = chainLagT,
                    chainStiffnessT = chainStiffT, chainDampingT = chainDampT,
                )
            )
        }
    }

    fun applyPreset(p: MotionPreset) {
        selectedPreset = p
        stiffnessT = p.stiffnessT
        dampingT = p.dampingT
        scope.launch { haptics.play(HapticEvent.Press) }
        persist()
    }

    fun applySpring(s: Float, d: Float) {
        stiffnessT = s
        dampingT = d
        selectedPreset = null
        persist()
    }

    fun applyHaptic(p: HapticPreset) {
        hapticPreset = p
        haptics.preset = p
        haptics.preview(p)
        persist()
    }

    fun springSpec() = spring<Float>(
        dampingRatio = ParameterMapping.dampingRatio(dampingT),
        stiffness = ParameterMapping.stiffness(stiffnessT),
    )

    val name = loaded?.name ?: kind.interaction
    val chain: Chain? = chainProperty?.let { Chain(it, chainAtT, chainLagT, chainStiffT, chainDampT) }
    val motion: () -> MotionSpec = {
        TriggerSpecs.build(kind, name, stiffnessT, dampingT, amountT, staggerT, hapticPreset.name.lowercase(), chain)
    }
    // Properties the leader already animates can't also be a follower's.
    val leaderProps = remember(kind) { TriggerSpecs.build(kind, "x", 0.5f, 0.5f, 0.5f, 0.5f, "off").transitions.map { it.property } }

    // The primary spring value (0 -> 1) each kind is built on.
    val p = remember { Animatable(0f) }
    val items = remember { List(TriggerSpecs.LIST_ITEMS) { Animatable(0f) } }
    var on by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }
    val burst = remember { Animatable(0f) }
    val follower = remember { Animatable(0f) }

    // The follower starts once the leader has done its chosen share of the move, after the extra lag, in either direction.
    LaunchedEffect(chainProperty, chainAtT, chainLagT, chainStiffT, chainDampT) {
        if (chainProperty == null) return@LaunchedEffect
        val at = TriggerSpecs.chainAtProgress(chainAtT)
        val lag = TriggerSpecs.chainLagMs(chainLagT).toLong()
        val followerSpring = spring<Float>(dampingRatio = ParameterMapping.dampingRatio(chainDampT), stiffness = ParameterMapping.stiffness(chainStiffT))
        var above = false
        snapshotFlow { (if (kind == TriggerKind.STAGGER_LIST) items[0].value else p.value) >= at }.collect { now ->
            if (now != above) {
                above = now
                launch { delay(lag); follower.animateTo(if (now) 1f else 0f, followerSpring) }
            }
        }
    }

    fun replayList() {
        scope.launch {
            items.forEach { it.snapTo(0f) }
            val gap = TriggerSpecs.staggerMs(staggerT).toLong()
            items.forEachIndexed { i, a ->
                launch {
                    delay(i * gap)
                    if (i == 0) haptics.play(HapticEvent.Press)
                    a.animateTo(1f, springSpec())
                }
            }
        }
    }

    val samples = remember { mutableStateListOf<Float>() }
    LaunchedEffect(Unit) {
        while (true) {
            snapshotFlow { p.isRunning || items.any { it.isRunning } }.first { it }
            while (p.isRunning || items.any { it.isRunning }) {
                withFrameNanos {
                    val v = if (kind == TriggerKind.STAGGER_LIST) items[0].value else p.value
                    samples.add((v - 0.5f) * 2f)
                    if (samples.size > 120) samples.removeAt(0)
                }
            }
        }
    }

    val readout = remember(stiffnessT, dampingT) {
        val k = ParameterMapping.stiffness(stiffnessT)
        val z = ParameterMapping.dampingRatio(dampingT)
        "${SpringMath.settleMs(k, z)} ms  /  ${(SpringMath.overshoot(z) * 100).roundToInt()}% overshoot"
    }

    EditorScaffold(
        title = ExperimentType.valueOf(kind.name).label,
        onBack = onBack,
        spring = SpringSpec(ParameterMapping.stiffness(stiffnessT), ParameterMapping.dampingRatio(dampingT)),
        stage = {
        Column(Modifier.reveal(0)) {
            Stage {
                when (kind) {
                    TriggerKind.TOGGLE -> {
                        val travel = with(density) { TriggerSpecs.TOGGLE_TRAVEL_DP.dp.toPx() }
                        val stretch = TriggerSpecs.thumbStretch(amountT)
                        Box(
                            Modifier
                                .testTag("triggerTarget")
                                .width(56.dp).height(32.dp)
                                .clip(CircleShape)
                                .background(lerp(t.line, t.ink, p.value.coerceIn(0f, 1f)))
                                .quietClickable {
                                    on = !on
                                    scope.launch { haptics.play(HapticEvent.Snap) }
                                    scope.launch { p.animateTo(if (on) 1f else 0f, springSpec()) }
                                }
                                .padding(3.dp),
                        ) {
                            Box(
                                Modifier
                                    .offset { IntOffset((p.value * travel).roundToInt(), 0) }
                                    .graphicsLayer { scaleX = 1f + stretch * (4f * p.value * (1f - p.value)).coerceAtLeast(0f) }
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(t.canvas),
                            )
                        }
                    }
                    TriggerKind.BUTTON_PRESS -> {
                        val down = TriggerSpecs.pressScale(amountT)
                        Box(
                            Modifier
                                .testTag("triggerTarget")
                                .graphicsLayer { val s = 1f + (down - 1f) * p.value; scaleX = s; scaleY = s }
                                .width(150.dp).height(48.dp)
                                .clip(RoundedCornerShape(t.radiusControl))
                                .background(t.ink)
                                .pointerInput(Unit) {
                                    detectTapGestures(onPress = {
                                        scope.launch { haptics.play(HapticEvent.Press) }
                                        scope.launch { p.animateTo(1f, springSpec()) }
                                        tryAwaitRelease()
                                        scope.launch { haptics.play(HapticEvent.Release) }
                                        scope.launch { p.animateTo(0f, springSpec()) }
                                    })
                                },
                            contentAlignment = Alignment.Center,
                        ) { Text("Press me", color = t.canvas, style = MaterialTheme.typography.labelLarge) }
                    }
                    TriggerKind.TAB_INDICATOR -> {
                        val tabW = with(density) { TriggerSpecs.TAB_WIDTH_DP.dp.toPx() }
                        Column {
                            Row {
                                listOf("Home", "Search", "Saved").forEachIndexed { i, label ->
                                    Box(
                                        Modifier
                                            .testTag("triggerTab$i")
                                            .width(TriggerSpecs.TAB_WIDTH_DP.dp).height(40.dp)
                                            .quietClickable {
                                                tab = i
                                                scope.launch { haptics.play(HapticEvent.Snap) }
                                                scope.launch { p.animateTo(i / 2f, springSpec()) }
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) { Text(label, color = if (tab == i) t.ink else t.inkSoft, style = MaterialTheme.typography.labelLarge) }
                                }
                            }
                            Box(
                                Modifier
                                    .offset { IntOffset((p.value * 2f * tabW).roundToInt(), 0) }
                                    .width(TriggerSpecs.TAB_WIDTH_DP.dp).height(3.dp).background(t.ink),
                            )
                        }
                    }
                    TriggerKind.STAGGER_LIST -> {
                        val rise = with(density) { TriggerSpecs.riseDp(amountT).dp.toPx() }
                        Column(Modifier.testTag("triggerTarget").quietClickable { replayList() }.padding(start = 24.dp, end = 24.dp, top = 52.dp, bottom = 20.dp)) {
                            items.forEachIndexed { i, a ->
                                Box(
                                    Modifier
                                        .offset { IntOffset(0, ((1f - a.value) * rise).roundToInt()) }
                                        .graphicsLayer { alpha = a.value.coerceIn(0f, 1f) }
                                        .fillMaxWidth(if (i % 2 == 0) 0.75f else 0.55f).height(14.dp)
                                        .clip(RoundedCornerShape(7.dp)).background(t.ink),
                                )
                                if (i != items.lastIndex) Spacer(Modifier.height(12.dp))
                            }
                        }
                    }
                    TriggerKind.LIKE_BURST -> {
                        val radius = with(density) { TriggerSpecs.burstRadiusDp(amountT).dp.toPx() }
                        val ink = t.ink
                        val line = t.inkSoft
                        Box(
                            Modifier
                                .testTag("triggerTarget")
                                .size(120.dp)
                                .quietClickable {
                                    on = !on
                                    if (on) {
                                        scope.launch { haptics.play(HapticEvent.Impact) }
                                        scope.launch { p.snapTo(0f); p.animateTo(1f, springSpec()) }
                                        scope.launch { burst.snapTo(0f); burst.animateTo(1f, tween(420)) }
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Canvas(Modifier.size(120.dp)) {
                                val c = center
                                if (on) repeat(TriggerSpecs.PARTICLES) { i ->
                                    val a = 2 * PI * i / TriggerSpecs.PARTICLES
                                    val r = 36.dp.toPx() + radius * burst.value
                                    drawCircle(ink.copy(alpha = (1f - burst.value).coerceIn(0f, 1f)), 3.5.dp.toPx(),
                                        Offset(c.x + (cos(a) * r).toFloat(), c.y + (sin(a) * r).toFloat()))
                                }
                                val s = if (on) 0.6f + 0.4f * p.value else 1f
                                val w = 52.dp.toPx() * s
                                val path = Path().apply {
                                    moveTo(c.x, c.y + w * 0.45f)
                                    cubicTo(c.x - w * 0.95f, c.y - w * 0.05f, c.x - w * 0.45f, c.y - w * 0.75f, c.x, c.y - w * 0.28f)
                                    cubicTo(c.x + w * 0.45f, c.y - w * 0.75f, c.x + w * 0.95f, c.y - w * 0.05f, c.x, c.y + w * 0.45f)
                                }
                                if (on) drawPath(path, ink) else drawPath(path, line, style = Stroke(2.5.dp.toPx()))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                when (kind) {
                    TriggerKind.TOGGLE -> "Tap the switch."
                    TriggerKind.BUTTON_PRESS -> "Press and hold, then let go."
                    TriggerKind.TAB_INDICATOR -> "Tap a tab."
                    TriggerKind.STAGGER_LIST -> "Tap the list to replay it."
                    TriggerKind.LIKE_BURST -> "Tap the heart."
                },
                style = MaterialTheme.typography.bodySmall,
                color = t.inkSoft,
            )
            if (kind == TriggerKind.STAGGER_LIST) TextLink("Replay", onClick = ::replayList)
            chainProperty?.let { prop ->
                val (_, from, to) = TriggerSpecs.FOLLOWERS.first { it.first == prop }
                Spacer(Modifier.height(12.dp))
                Text("Follower: $prop", style = MaterialTheme.typography.labelMedium, color = t.inkSoft)
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.CenterStart) {
                    val v = from + (to - from) * follower.value
                    Box(
                        Modifier
                            .testTag("followerTarget")
                            .graphicsLayer {
                                when (prop) {
                                    "opacity" -> alpha = v.coerceIn(0f, 1f)
                                    "scale" -> { scaleX = v; scaleY = v }
                                    "offsetY" -> translationY = v.dp.toPx()
                                    "rotation" -> rotationZ = v
                                }
                            }
                            .width(if (prop == "progress") (28 + 92 * follower.value).dp else 120.dp).height(20.dp)
                            .clip(RoundedCornerShape(10.dp)).background(t.inkSoft),
                    )
                }
            }
        }
        },
        controls = {
        EditorSection("Presets", 1) {
            SpringPresets(
                name = loaded?.name ?: "",
                stiffnessT = stiffnessT,
                dampingT = dampingT,
                selected = selectedPreset,
                onPreset = ::applyPreset,
                onSpring = ::applySpring,
            )
        }

        EditorSection("Motion", 2) {
            LabeledSlider(
                "Stiffness", stiffnessT,
                onValueChange = { stiffnessT = it; selectedPreset = null },
                onValueChangeFinished = ::persist,
            )
            LabeledSlider(
                "Damping", dampingT,
                onValueChange = { dampingT = it; selectedPreset = null },
                onValueChangeFinished = ::persist,
            )
            if (kind != TriggerKind.TAB_INDICATOR) {
                LabeledSlider(kind.amountLabel, amountT, onValueChange = { amountT = it }, onValueChangeFinished = ::persist)
            }
            if (kind == TriggerKind.STAGGER_LIST) {
                LabeledSlider("Stagger", staggerT, onValueChange = { staggerT = it }, onValueChangeFinished = ::persist)
            }
        }

        EditorSection("Chain", 3, trailing = chainProperty?.let { "starts ${resolvedFollowerStart(kind, motion)} ms" }) {
            Text(
                "A second property that starts once the first has done part of its move, so it follows when you retune.",
                style = MaterialTheme.typography.bodySmall, color = t.inkSoft,
            )
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("None", selected = chainProperty == null, onClick = { chainProperty = null; persist() }, modifier = Modifier.testTag("chainNone"))
                TriggerSpecs.FOLLOWERS.filter { it.first !in leaderProps }.forEach { (prop, _, _) ->
                    Chip(prop, selected = chainProperty == prop, onClick = { chainProperty = prop; persist() }, modifier = Modifier.testTag("chain_$prop"))
                }
            }
            if (chainProperty != null) {
                Spacer(Modifier.height(8.dp))
                LabeledSlider("Starts when first is at", chainAtT, { chainAtT = it }, ::persist)
                LabeledSlider("Extra lag", chainLagT, { chainLagT = it }, ::persist)
                LabeledSlider("Follower stiffness", chainStiffT, { chainStiffT = it }, ::persist)
                LabeledSlider("Follower damping", chainDampT, { chainDampT = it }, ::persist)
            }
        }

        EditorSection("Haptics", 4) {
            HapticControls(preset = hapticPreset, onSelect = ::applyHaptic)
        }

        EditorSection("Curve", 5, trailing = readout) {
            PositionCurve(samples = samples, range = 1.6f)
        }

        Column(Modifier.reveal(6)) {
            ExportPanel(
                onApplySpring = ::applySpring,
                onApplyHaptic = ::applyHaptic,
                compose = { ComposeGenericGenerator.generate(motion()) },
                spec = { motion().toJson() },
                css = { DesignSpec.css(name, ParameterMapping.stiffness(stiffnessT), ParameterMapping.dampingRatio(dampingT)) },
                motion = motion,
            )
        }
        Spacer(Modifier.height(24.dp))
        },
    )
}

/** When the follower begins, in ms after the trigger, per the resolved spec. */
private fun resolvedFollowerStart(kind: TriggerKind, motion: () -> MotionSpec): Int =
    motion().resolved().transitions.lastOrNull()?.delayMs ?: 0
