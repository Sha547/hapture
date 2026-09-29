package com.motionlab.app.feature.editor

import com.motionlab.app.core.spec.SpringSpec
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.motionlab.app.core.haptics.HapticEngine
import com.motionlab.app.core.haptics.HapticEvent
import com.motionlab.app.core.haptics.HapticPreset
import com.motionlab.app.core.model.MotionPreset
import com.motionlab.app.core.model.ObjectStyle
import com.motionlab.app.core.physics.ParameterMapping
import com.motionlab.app.core.physics.PullRefreshSolver
import com.motionlab.app.core.physics.RubberBand
import com.motionlab.app.data.ExperimentEntity
import com.motionlab.app.data.ExperimentRepository
import com.motionlab.app.export.DesignSpec
import com.motionlab.app.export.PullRefreshCodeGenerator
import com.motionlab.app.export.SpringMath
import com.motionlab.app.ui.design.AppScreen
import com.motionlab.app.ui.design.DashedGuide
import com.motionlab.app.ui.design.LocalTokens
import com.motionlab.app.ui.design.Stage
import com.motionlab.app.ui.design.StageObject
import com.motionlab.app.ui.design.TopBar
import com.motionlab.app.ui.design.reveal
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Pull to refresh: drag down, resisted the whole way (no free zone -- real
 * pull-to-refresh resists from the first pixel, unlike a boundary you travel
 * to before feeling resistance), cross the trigger line and it holds there
 * (simulating real work happening) before a real `spring()` brings it back.
 * Let go short and it springs straight back, nothing triggered. The trigger
 * check is [PullRefreshSolver]; the hold is honestly nominal -- there's no
 * real network call behind it, just a timed delay.
 */
@Composable
fun PullRefreshScreen(
    experimentId: Long,
    repository: ExperimentRepository,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val haptics = remember { HapticEngine(context) }
    val tokens = LocalTokens.current

    var stiffnessT by remember { mutableFloatStateOf(0.5f) }
    var dampingT by remember { mutableFloatStateOf(0.6f) }
    var resistanceT by remember { mutableFloatStateOf(0.5f) }
    var triggerT by remember { mutableFloatStateOf(0.4f) }
    var holdT by remember { mutableFloatStateOf(0.3f) }
    var selectedPreset by remember { mutableStateOf<MotionPreset?>(null) }
    var hapticPreset by remember { mutableStateOf(HapticPreset.CRISP) }
    SideEffect { haptics.preset = hapticPreset }
    var objectStyle by remember { mutableStateOf(ObjectStyle()) }

    var loaded by remember { mutableStateOf<ExperimentEntity?>(null) }
    LaunchedEffect(experimentId) {
        repository.get(experimentId)?.let { e ->
            loaded = e
            stiffnessT = e.stiffnessT
            dampingT = e.dampingT
            resistanceT = e.resistanceT ?: 0.5f
            triggerT = e.pullTriggerT ?: 0.4f
            holdT = e.pullHoldT ?: 0.3f
            objectStyle = e.objectStyle
            hapticPreset = e.hapticPreset
        }
    }

    fun persist() {
        val e = loaded ?: return
        scope.launch {
            repository.save(
                e.copy(
                    stiffnessT = stiffnessT,
                    dampingT = dampingT,
                    resistanceT = resistanceT,
                    pullTriggerT = triggerT,
                    pullHoldT = holdT,
                    updatedAt = System.currentTimeMillis(),
                ).withObjectStyle(objectStyle).copy(hapticPreset = hapticPreset)
            )
        }
    }

    fun applyPreset(preset: MotionPreset) {
        selectedPreset = preset
        stiffnessT = preset.stiffnessT
        dampingT = preset.dampingT
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

    fun triggerPx() = with(density) { ParameterMapping.pullTriggerDp(triggerT).dp.toPx() }
    fun holdMs() = ParameterMapping.pullHoldMs(holdT)

    val offsetY = remember { Animatable(0f) }
    var dragging by remember { mutableStateOf(false) }
    var holding by remember { mutableStateOf(false) }
    var crossed by remember { mutableStateOf(false) }
    val curveRangePx = remember(density) { with(density) { 140.dp.toPx() } }

    val samples = remember { mutableStateListOf<Float>() }
    LaunchedEffect(Unit) {
        while (true) {
            snapshotFlow { dragging || holding || offsetY.isRunning }.first { it }
            while (dragging || holding || offsetY.isRunning) {
                withFrameNanos {
                    samples.add(offsetY.value)
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
        title = "Pull to refresh",
        onBack = onBack,
        spring = SpringSpec(ParameterMapping.stiffness(stiffnessT), ParameterMapping.dampingRatio(dampingT)),
        stage = {
        Column(Modifier.reveal(0)) {
            Stage {
                Canvas(Modifier.matchParentSize()) {
                    val y = triggerPx()
                    val color = if (crossed) tokens.ink.copy(alpha = 0.6f) else tokens.ink.copy(alpha = 0.16f)
                    drawLine(color, Offset(18.dp.toPx(), y), Offset(size.width - 18.dp.toPx(), y), 1.5f, pathEffect = DashedGuide)
                }
                StageObject(
                    style = objectStyle,
                    modifier = Modifier
                        .offset { IntOffset(0, offsetY.value.roundToInt()) }
                        .pointerInput(Unit) {
                            var raw = 0f
                            detectDragGestures(
                                onDragStart = {
                                    if (holding) return@detectDragGestures
                                    raw = offsetY.value
                                    dragging = true
                                    crossed = PullRefreshSolver.triggered(raw, triggerPx())
                                    scope.launch { haptics.play(HapticEvent.Press) }
                                },
                                onDrag = { change, dragAmount ->
                                    if (holding) return@detectDragGestures
                                    change.consume()
                                    raw += dragAmount.y
                                    val k = ParameterMapping.resistance(resistanceT)
                                    val visual = PullRefreshSolver.visual(raw, k, triggerPx()).coerceAtLeast(0f)
                                    val nowCrossed = PullRefreshSolver.triggered(visual, triggerPx())
                                    if (nowCrossed != crossed) {
                                        crossed = nowCrossed
                                        scope.launch { haptics.play(HapticEvent.Threshold) }
                                    }
                                    scope.launch { offsetY.snapTo(visual) }
                                },
                                onDragCancel = { dragging = false },
                                onDragEnd = {
                                    dragging = false
                                    val triggered = crossed
                                    val stiffness = ParameterMapping.stiffness(stiffnessT)
                                    val dampingRatio = ParameterMapping.dampingRatio(dampingT)
                                    scope.launch {
                                        if (triggered) {
                                            holding = true
                                            offsetY.animateTo(
                                                triggerPx(),
                                                spring(dampingRatio = dampingRatio, stiffness = stiffness),
                                            )
                                            haptics.play(HapticEvent.Impact)
                                            delay(holdMs().toLong())
                                            haptics.play(HapticEvent.Success)
                                        }
                                        offsetY.animateTo(
                                            0f,
                                            spring(dampingRatio = dampingRatio, stiffness = stiffness),
                                        )
                                        haptics.play(HapticEvent.Release)
                                        crossed = false
                                        holding = false
                                    }
                                },
                            )
                        },
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Pull down past the line and let go; it holds, then springs back.",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.inkSoft,
            )
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
            LabeledSlider("Trigger distance", triggerT, onValueChange = { triggerT = it }, onValueChangeFinished = ::persist)
            LabeledSlider("Hold duration", holdT, onValueChange = { holdT = it }, onValueChangeFinished = ::persist)
            LabeledSlider("Resistance", resistanceT, onValueChange = { resistanceT = it }, onValueChangeFinished = ::persist)
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
        }

        EditorSection("Object", 3) {
            ObjectControls(style = objectStyle, onChange = { objectStyle = it }, onCommit = ::persist)
        }

        EditorSection("Haptics", 4) {
            HapticControls(preset = hapticPreset, onSelect = ::applyHaptic)
        }

        EditorSection("Curve", 5, trailing = readout) {
            PositionCurve(samples = samples, range = curveRangePx)
        }

        val name = loaded?.name ?: "Pull to Refresh"
        Column(Modifier.reveal(6)) {
            ExportPanel(
                onApplySpring = ::applySpring,
                onApplyHaptic = ::applyHaptic,
                compose = {
                    PullRefreshCodeGenerator.generate(
                        stiffness = ParameterMapping.stiffness(stiffnessT),
                        dampingRatio = ParameterMapping.dampingRatio(dampingT),
                        triggerDp = ParameterMapping.pullTriggerDp(triggerT),
                        holdMs = holdMs(),
                        resistanceK = ParameterMapping.resistance(resistanceT),
                    )
                },
                spec = {
                    DesignSpec.json(
                        name = name,
                        interaction = "pull_refresh",
                        stiffness = ParameterMapping.stiffness(stiffnessT),
                        dampingRatio = ParameterMapping.dampingRatio(dampingT),
                        extras = listOf(
                            DesignSpec.Extra("triggerDistanceDp", ParameterMapping.pullTriggerDp(triggerT)),
                            DesignSpec.Extra("holdMs", holdMs()),
                            DesignSpec.Extra("resistanceK", ParameterMapping.resistance(resistanceT)),
                        ),
                        style = objectStyle,
                        tokens = tokens,
                        haptics = hapticPreset,
                    )
                },
                css = {
                    DesignSpec.css(name, ParameterMapping.stiffness(stiffnessT), ParameterMapping.dampingRatio(dampingT))
                },
            )
        }
        Spacer(Modifier.height(24.dp))
        },
    )
}
