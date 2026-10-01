package com.klynstudios.hapture.feature.editor

import com.klynstudios.hapture.core.spec.SpringSpec
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.klynstudios.hapture.core.haptics.HapticEngine
import com.klynstudios.hapture.core.haptics.HapticEvent
import com.klynstudios.hapture.core.haptics.HapticPreset
import com.klynstudios.hapture.core.model.MotionPreset
import com.klynstudios.hapture.core.model.ObjectStyle
import com.klynstudios.hapture.core.physics.MagneticSolver
import com.klynstudios.hapture.core.physics.ParameterMapping
import com.klynstudios.hapture.core.physics.RubberBand
import com.klynstudios.hapture.core.physics.SnapPoints
import com.klynstudios.hapture.data.ExperimentEntity
import com.klynstudios.hapture.data.ExperimentRepository
import com.klynstudios.hapture.export.DesignSpec
import com.klynstudios.hapture.export.MagneticSnapCodeGenerator
import com.klynstudios.hapture.export.SpringCodeGenerator
import com.klynstudios.hapture.export.SpringMath
import com.klynstudios.hapture.ui.design.AppScreen
import com.klynstudios.hapture.ui.design.LocalTokens
import com.klynstudios.hapture.ui.design.Stage
import com.klynstudios.hapture.ui.design.StageObject
import com.klynstudios.hapture.ui.design.TopBar
import com.klynstudios.hapture.ui.design.reveal
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Drag between three discrete targets.
 * Getting close to one pulls the card toward it (magnetic assist, still
 * under your finger); releasing hands off to the nearest target and a real
 * spring, exactly like SpringDragScreen -- snapping isn't its own bespoke
 * animation, it's "spring to whichever target won."
 */
@Composable
fun MagneticSnapScreen(
    experimentId: Long,
    repository: ExperimentRepository,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val haptics = remember { HapticEngine(context) }

    var stiffnessT by remember { mutableFloatStateOf(0.4f) }
    var dampingT by remember { mutableFloatStateOf(0.6f) }
    var strengthT by remember { mutableFloatStateOf(0.6f) }
    var thresholdT by remember { mutableFloatStateOf(0.6f) }
    var selectedPreset by remember { mutableStateOf<MotionPreset?>(null) }
    var hapticPreset by remember { mutableStateOf(HapticPreset.CRISP) }
    SideEffect { haptics.preset = hapticPreset }

    var objectStyle by remember { mutableStateOf(ObjectStyle()) }
    val tokens = LocalTokens.current
    var loaded by remember { mutableStateOf<ExperimentEntity?>(null) }
    LaunchedEffect(experimentId) {
        repository.get(experimentId)?.let { entity ->
            loaded = entity
            stiffnessT = entity.stiffnessT
            dampingT = entity.dampingT
            strengthT = entity.magneticStrengthT ?: 0.6f
            thresholdT = entity.magneticThresholdT ?: 0.6f
            objectStyle = entity.objectStyle
            hapticPreset = entity.hapticPreset
        }
    }

    fun persist() {
        val entity = loaded ?: return
        scope.launch {
            repository.save(
                entity.copy(
                    stiffnessT = stiffnessT,
                    dampingT = dampingT,
                    magneticStrengthT = strengthT,
                    magneticThresholdT = thresholdT,
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

    val spacingPx = remember(density) { with(density) { 90.dp.toPx() } }
    val targets = remember(spacingPx) { floatArrayOf(-spacingPx, 0f, spacingPx) }
    val curveRangePx = remember(spacingPx) { spacingPx * 1.3f }

    val offsetX = remember { Animatable(0f) }
    var nearestIndex by remember { mutableIntStateOf(1) }
    var lastHapticIndex by remember { mutableIntStateOf(1) }
    var dragging by remember { mutableStateOf(false) }

    // Only sample while there's motion -- dragging, or the snap still
    // settling -- so Compose is genuinely idle the rest of the time (an
    // unconditional per-frame loop here made this screen never idle; see the
    // same note on SpringDragScreen, where an instrumented test caught it).
    val samples = remember { mutableStateListOf<Float>() }
    LaunchedEffect(Unit) {
        while (true) {
            snapshotFlow { dragging || offsetX.isRunning }.first { it }
            while (dragging || offsetX.isRunning) {
                withFrameNanos {
                    samples.add(offsetX.value)
                    if (samples.size > 120) samples.removeAt(0)
                }
            }
        }
    }

    val t = tokens
    val readout = remember(stiffnessT, dampingT) {
        val k = ParameterMapping.stiffness(stiffnessT)
        val z = ParameterMapping.dampingRatio(dampingT)
        "${SpringMath.settleMs(k, z)} ms  /  ${(SpringMath.overshoot(z) * 100).roundToInt()}% overshoot"
    }

    EditorScaffold(
        title = "Magnetic snap",
        onBack = onBack,
        spring = SpringSpec(ParameterMapping.stiffness(stiffnessT), ParameterMapping.dampingRatio(dampingT)),
        stage = {
        Column(Modifier.reveal(0)) {
            Stage {
                // Snap targets, drawn where they actually are (spacing either side of centre).
                Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp)) {
                    listOf("A", "B", "C").forEachIndexed { i, label ->
                        Box(Modifier.width(with(density) { spacingPx.toDp() }), contentAlignment = Alignment.Center) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (i == nearestIndex) t.ink else t.inkFaint,
                            )
                        }
                    }
                }
                StageObject(
                    style = objectStyle,
                    modifier = Modifier
                        .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                        .pointerInput(Unit) {
                            var raw = 0f
                            detectDragGestures(
                                onDragStart = {
                                    raw = offsetX.value
                                    dragging = true
                                    scope.launch { haptics.play(HapticEvent.Press) }
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    raw += dragAmount.x

                                    val strength = ParameterMapping.magneticStrength(strengthT)
                                    val threshold = ParameterMapping.magneticThresholdPx(thresholdT, spacingPx)
                                    val idx = SnapPoints.nearestIndex(raw, targets)
                                    nearestIndex = idx
                                    if (idx != lastHapticIndex) {
                                        lastHapticIndex = idx
                                        scope.launch { haptics.play(HapticEvent.Threshold) }
                                    }
                                    val visual = MagneticSolver.pulledPosition(raw, targets[idx], threshold, strength)
                                    scope.launch { offsetX.snapTo(visual) }
                                },
                                onDragCancel = { dragging = false },
                                onDragEnd = {
                                    dragging = false
                                    val idx = SnapPoints.nearestIndex(offsetX.value, targets)
                                    val stiffness = ParameterMapping.stiffness(stiffnessT)
                                    val dampingRatio = ParameterMapping.dampingRatio(dampingT)
                                    scope.launch {
                                        offsetX.animateTo(
                                            targetValue = targets[idx],
                                            animationSpec = spring(
                                                dampingRatio = dampingRatio,
                                                stiffness = stiffness,
                                            ),
                                        )
                                        haptics.play(HapticEvent.Snap)
                                    }
                                },
                            )
                        },
                )
            }
            Spacer(Modifier.height(12.dp))
            Text("Drag toward a point and let go; it pulls you in.", style = MaterialTheme.typography.bodySmall, color = t.inkSoft)
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
            LabeledSlider("Magnetic strength", strengthT, onValueChange = { strengthT = it }, onValueChangeFinished = ::persist)
            LabeledSlider("Capture radius", thresholdT, onValueChange = { thresholdT = it }, onValueChangeFinished = ::persist)
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

        val name = loaded?.name ?: "Magnetic Snap"
        Column(Modifier.reveal(6)) {
            ExportPanel(
                onApplySpring = ::applySpring,
                onApplyHaptic = ::applyHaptic,
                compose = {
                    MagneticSnapCodeGenerator.generate(
                        stiffness = ParameterMapping.stiffness(stiffnessT),
                        dampingRatio = ParameterMapping.dampingRatio(dampingT),
                        magneticStrength = ParameterMapping.magneticStrength(strengthT),
                        magneticThreshold = ParameterMapping.magneticThresholdPx(thresholdT, spacingPx),
                        targetSpacing = spacingPx,
                    )
                },
                spec = {
                    DesignSpec.json(
                        name = name,
                        interaction = "magnetic_snap",
                        stiffness = ParameterMapping.stiffness(stiffnessT),
                        dampingRatio = ParameterMapping.dampingRatio(dampingT),
                        extras = listOf(
                            DesignSpec.Extra("magneticStrength", ParameterMapping.magneticStrength(strengthT)),
                            DesignSpec.Extra("captureRadiusDp", with(density) { ParameterMapping.magneticThresholdPx(thresholdT, spacingPx).toDp().value }),
                            DesignSpec.Extra("targetSpacingDp", with(density) { spacingPx.toDp().value }),
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
