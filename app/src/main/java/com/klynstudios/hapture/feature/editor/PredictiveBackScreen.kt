package com.klynstudios.hapture.feature.editor

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.klynstudios.hapture.core.haptics.HapticEngine
import com.klynstudios.hapture.core.haptics.HapticEvent
import com.klynstudios.hapture.core.haptics.HapticPreset
import com.klynstudios.hapture.core.model.MaterialSpring
import com.klynstudios.hapture.core.model.MotionPreset
import com.klynstudios.hapture.core.physics.ParameterMapping
import com.klynstudios.hapture.core.physics.PredictiveBackSolver
import com.klynstudios.hapture.core.spec.SpringSpec
import com.klynstudios.hapture.data.ExperimentEntity
import com.klynstudios.hapture.data.ExperimentRepository
import com.klynstudios.hapture.data.ExperimentType
import com.klynstudios.hapture.export.DesignSpec
import com.klynstudios.hapture.export.PredictiveBackCodeGenerator
import com.klynstudios.hapture.export.SpringMath
import com.klynstudios.hapture.feature.common.label
import com.klynstudios.hapture.ui.design.AppIcon
import com.klynstudios.hapture.ui.design.IconKind
import com.klynstudios.hapture.ui.design.LocalBackdrop
import com.klynstudios.hapture.ui.design.LocalBackdropAction
import com.klynstudios.hapture.ui.design.LocalTokens
import com.klynstudios.hapture.ui.design.Stage
import com.klynstudios.hapture.ui.design.reveal
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** How far a swipe has to travel, as a share of the page width, to reach full progress. */
private const val FULL_SWIPE_FRACTION = 0.6f

/**
 * Android's predictive back gesture, tuned by feel: swipe in from a side of the
 * mock page and it shrinks and leans toward your finger, with the page it would
 * go back to showing underneath. Let go past the line (or flick) and the tuned
 * spring carries it away; let go short and the same spring brings it back.
 * The preview's commit rule stands in for the system's, which decides on device.
 */
@Composable
fun PredictiveBackScreen(
    experimentId: Long,
    repository: ExperimentRepository,
    onBack: () -> Unit,
) {
    val t = LocalTokens.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = remember { HapticEngine(context) }

    var stiffnessT by remember { mutableFloatStateOf(ParameterMapping.stiffnessT(MaterialSpring.STANDARD_DEFAULT.stiffness)) }
    var dampingT by remember { mutableFloatStateOf(ParameterMapping.dampingT(MaterialSpring.STANDARD_DEFAULT.dampingRatio)) }
    var shrinkT by remember { mutableFloatStateOf(0.5f) }
    var shiftT by remember { mutableFloatStateOf(0.5f) }
    var selectedPreset by remember { mutableStateOf<MotionPreset?>(null) }
    var hapticPreset by remember { mutableStateOf(HapticPreset.CRISP) }
    SideEffect { haptics.preset = hapticPreset }

    var loaded by remember { mutableStateOf<ExperimentEntity?>(null) }
    LaunchedEffect(experimentId) {
        repository.get(experimentId)?.let { e ->
            loaded = e
            stiffnessT = e.stiffnessT
            dampingT = e.dampingT
            shrinkT = e.backShrinkT ?: 0.5f
            shiftT = e.backShiftT ?: 0.5f
            hapticPreset = e.hapticPreset
        }
    }

    fun persist() {
        val e = loaded ?: return
        scope.launch {
            repository.save(
                e.copy(
                    stiffnessT = stiffnessT, dampingT = dampingT,
                    backShrinkT = shrinkT, backShiftT = shiftT,
                    hapticPreset = hapticPreset, updatedAt = System.currentTimeMillis(),
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

    fun settleSpring() = spring<Float>(
        dampingRatio = ParameterMapping.dampingRatio(dampingT),
        stiffness = ParameterMapping.stiffness(stiffnessT),
    )

    val progress = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }
    val pageAlpha = remember { Animatable(1f) }
    var fromLeft by remember { mutableStateOf(true) }
    var widthPx by remember { mutableFloatStateOf(1f) }
    var dragging by remember { mutableStateOf(false) }

    // The curve plots progress plus exit, so a commit reads as the line carrying on up and a cancel as it falling back.
    val samples = remember { mutableStateListOf<Float>() }
    LaunchedEffect(Unit) {
        while (true) {
            snapshotFlow { dragging || progress.isRunning || exit.isRunning }.first { it }
            while (dragging || progress.isRunning || exit.isRunning) {
                withFrameNanos {
                    samples.add((progress.value + exit.value) * 2f - 1f)
                    if (samples.size > 120) samples.removeAt(0)
                }
            }
        }
    }

    val minScale = ParameterMapping.backMinScale(shrinkT)
    val shiftFraction = ParameterMapping.backShiftFraction(shiftT)
    val readout = remember(stiffnessT, dampingT) {
        val k = ParameterMapping.stiffness(stiffnessT)
        val z = ParameterMapping.dampingRatio(dampingT)
        "${SpringMath.settleMs(k, z)} ms  /  ${(SpringMath.overshoot(z) * 100).roundToInt()}% overshoot"
    }
    val name = loaded?.name ?: "Predictive Back"

    EditorScaffold(
        title = ExperimentType.PREDICTIVE_BACK.label,
        onBack = onBack,
        spring = SpringSpec(ParameterMapping.stiffness(stiffnessT), ParameterMapping.dampingRatio(dampingT)),
        stage = {
            Column(Modifier.reveal(0)) {
                // The page is what moves here, so a screenshot backdrop behind it has nothing to add: no button for it.
                CompositionLocalProvider(LocalBackdropAction provides null, LocalBackdrop provides null) {
                Stage(Modifier.onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }, height = 280.dp) {
                    // The page you'd go back to, dimmed until the swipe lifts the top page off it.
                    MockPage(title = "Inbox", rows = 5, modifier = Modifier.fillMaxSize())
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(t.ink.copy(alpha = PredictiveBackSolver.scrimAlpha(progress.value, exit.value))),
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .testTag("backPage")
                            .graphicsLayer {
                                val s = PredictiveBackSolver.scale(progress.value, minScale)
                                scaleX = s
                                scaleY = s
                                translationX = PredictiveBackSolver.shiftPx(progress.value, exit.value, widthPx, shiftFraction, fromLeft)
                                alpha = pageAlpha.value
                                shape = RoundedCornerShape(PredictiveBackSolver.cornerDp(progress.value).dp.toPx())
                                clip = true
                            }
                            .pointerInput(Unit) {
                                var tracker = VelocityTracker()
                                var raw = 0f
                                var crossed = false
                                detectHorizontalDragGestures(
                                    onDragStart = { at ->
                                        scope.launch { progress.stop() }
                                        fromLeft = at.x < size.width / 2f
                                        raw = progress.value * size.width * FULL_SWIPE_FRACTION
                                        crossed = PredictiveBackSolver.commits(progress.value, 0f)
                                        tracker = VelocityTracker()
                                        dragging = true
                                    },
                                    onHorizontalDrag = { change, dx ->
                                        change.consume()
                                        raw = (raw + if (fromLeft) dx else -dx).coerceAtLeast(0f)
                                        val p = (raw / (size.width * FULL_SWIPE_FRACTION)).coerceIn(0f, 1f)
                                        tracker.addPosition(change.uptimeMillis, Offset(p, 0f))
                                        scope.launch { progress.snapTo(p) }
                                        // One tick as the swipe crosses the line where letting go would go back.
                                        val nowCrossed = PredictiveBackSolver.commits(p, 0f)
                                        if (nowCrossed != crossed) {
                                            crossed = nowCrossed
                                            if (nowCrossed) scope.launch { haptics.play(HapticEvent.Threshold) }
                                        }
                                    },
                                    onDragCancel = { dragging = false; scope.launch { progress.animateTo(0f, settleSpring()) } },
                                    onDragEnd = {
                                        dragging = false
                                        val v = tracker.calculateVelocity().x
                                        scope.launch {
                                            if (PredictiveBackSolver.commits(progress.value, v)) {
                                                haptics.play(HapticEvent.Snap)
                                                exit.animateTo(1f, settleSpring(), initialVelocity = abs(v) * 0.3f)
                                                // Gone; bring a fresh page back so it can be tried again.
                                                delay(250)
                                                pageAlpha.snapTo(0f)
                                                progress.snapTo(0f)
                                                exit.snapTo(0f)
                                                pageAlpha.animateTo(1f, tween(220))
                                            } else {
                                                progress.animateTo(0f, settleSpring(), initialVelocity = -abs(v))
                                                haptics.play(HapticEvent.Release)
                                            }
                                        }
                                    },
                                )
                            },
                    ) {
                        MockPage(title = "Message", rows = 3, modifier = Modifier.fillMaxSize(), raised = true)
                    }
                }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Swipe in from either side of the page. Let go past a third of the way to go back.",
                    style = MaterialTheme.typography.bodySmall,
                    color = t.inkSoft,
                )
            }
        },
        controls = {
            EditorSection("Presets", 1) {
                SpringPresets(
                    name = name,
                    stiffnessT = stiffnessT,
                    dampingT = dampingT,
                    selected = selectedPreset,
                    onPreset = ::applyPreset,
                    onSpring = ::applySpring,
                )
            }

            EditorSection("Motion", 2) {
                LabeledSlider("Stiffness", stiffnessT, onValueChange = { stiffnessT = it; selectedPreset = null }, onValueChangeFinished = ::persist)
                LabeledSlider("Damping", dampingT, onValueChange = { dampingT = it; selectedPreset = null }, onValueChangeFinished = ::persist)
                LabeledSlider("Shrink", shrinkT, onValueChange = { shrinkT = it }, onValueChangeFinished = ::persist)
                LabeledSlider("Edge shift", shiftT, onValueChange = { shiftT = it }, onValueChangeFinished = ::persist)
                Text(
                    "At a full swipe the page is ${(minScale * 100).roundToInt()}% of its size and leans ${(shiftFraction * 100).roundToInt()}% of its width. Material's own is 90% with a slight lean.",
                    style = MaterialTheme.typography.bodySmall,
                    color = t.inkSoft,
                )
            }

            EditorSection("Haptics", 3) {
                HapticControls(preset = hapticPreset, onSelect = ::applyHaptic)
            }

            EditorSection("Curve", 4, trailing = readout) {
                PositionCurve(samples = samples, range = 1.1f)
            }

            Column(Modifier.reveal(5)) {
                ExportPanel(
                    onApplySpring = ::applySpring,
                    onApplyHaptic = ::applyHaptic,
                    compose = {
                        PredictiveBackCodeGenerator.generate(
                            stiffness = ParameterMapping.stiffness(stiffnessT),
                            dampingRatio = ParameterMapping.dampingRatio(dampingT),
                            minScale = minScale,
                            shiftFraction = shiftFraction,
                        )
                    },
                    spec = {
                        DesignSpec.json(
                            name = name,
                            interaction = "predictive_back",
                            stiffness = ParameterMapping.stiffness(stiffnessT),
                            dampingRatio = ParameterMapping.dampingRatio(dampingT),
                            extras = listOf(
                                DesignSpec.Extra("minScale", minScale),
                                DesignSpec.Extra("shiftFraction", shiftFraction),
                                DesignSpec.Extra("maxCornerDp", PredictiveBackSolver.MAX_CORNER_DP),
                            ),
                            style = loaded?.objectStyle ?: com.klynstudios.hapture.core.model.ObjectStyle(),
                            tokens = t,
                            haptics = hapticPreset,
                        )
                    },
                    css = { DesignSpec.css(name, ParameterMapping.stiffness(stiffnessT), ParameterMapping.dampingRatio(dampingT)) },
                )
            }
            Spacer(Modifier.height(24.dp))
        },
    )
}

/** A stand-in app page: a title bar and a few placeholder rows, drawn in the theme's own colours. */
@Composable
private fun MockPage(title: String, rows: Int, modifier: Modifier = Modifier, raised: Boolean = false) {
    val t = LocalTokens.current
    Column(
        modifier
            .background(if (raised) t.surface else t.canvas)
            .then(if (raised) Modifier.border(t.hairline, t.line) else Modifier)
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            if (raised) {
                AppIcon(IconKind.BACK, tint = t.ink, size = 18.dp)
                Spacer(Modifier.width(12.dp))
            }
            Text(title, style = MaterialTheme.typography.titleMedium, color = t.ink)
        }
        Spacer(Modifier.height(18.dp))
        repeat(rows) { i ->
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(t.line))
                Spacer(Modifier.width(12.dp))
                Column {
                    Box(Modifier.width(if (i % 2 == 0) 150.dp else 110.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(t.inkFaint.copy(alpha = 0.5f)))
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.width(if (i % 2 == 0) 90.dp else 120.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(t.line))
                }
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}
