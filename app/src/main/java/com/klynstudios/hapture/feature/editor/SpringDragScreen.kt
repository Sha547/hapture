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
import androidx.compose.ui.geometry.Offset
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
 * Drag the card: it follows your finger, resists past a boundary, springs
 * back on release and taps once when it settles. The sliders retune the same
 * spring the card animates with, so there's no separate preview maths to
 * keep in sync with the export.
 */
@Composable
fun SpringDragScreen(
    experimentId: Long,
    repository: ExperimentRepository,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val haptics = remember { HapticEngine(context) }

    var stiffnessT by remember { mutableFloatStateOf(0.4f) }
    var dampingT by remember { mutableFloatStateOf(0.55f) }
    var resistanceT by remember { mutableFloatStateOf(0.5f) }
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
            resistanceT = entity.resistanceT ?: 0.5f
            objectStyle = entity.objectStyle
            hapticPreset = entity.hapticPreset
        }
    }

    // Persists once per slider release, not per frame of drag -- see
    // LabeledSlider's onValueChangeFinished.
    fun persist() {
        val entity = loaded ?: return
        scope.launch {
            repository.save(
                entity.copy(
                    stiffnessT = stiffnessT,
                    dampingT = dampingT,
                    resistanceT = resistanceT,
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

    val offsetX = remember { Animatable(0f) }
    val boundPx = remember(density) { with(density) { 56.dp.toPx() } }
    val curveRangePx = remember(density) { with(density) { 160.dp.toPx() } }
    var dragging by remember { mutableStateOf(false) }

    // One sample per frame for the curve, but only while dragging or settling.
    // Sampling all the time keeps Compose from ever going idle, which breaks
    // waitForIdle() in the UI tests.
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

    val t = LocalTokens.current
    val readout = remember(stiffnessT, dampingT) {
        val k = ParameterMapping.stiffness(stiffnessT)
        val z = ParameterMapping.dampingRatio(dampingT)
        "${SpringMath.settleMs(k, z)} ms  /  ${(SpringMath.overshoot(z) * 100).roundToInt()}% overshoot"
    }

    EditorScaffold(
        title = "Spring drag",
        onBack = onBack,
        spring = SpringSpec(ParameterMapping.stiffness(stiffnessT), ParameterMapping.dampingRatio(dampingT)),
        stage = {
        Column(Modifier.reveal(0)) {
            Stage {
                StageObject(
                    style = objectStyle,
                    modifier = Modifier
                        .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                        .pointerInput(Unit) {
                            var raw = 0f
                            var tracker = VelocityTracker()
                            detectDragGestures(
                                onDragStart = {
                                    raw = offsetX.value
                                    tracker = VelocityTracker()
                                    dragging = true
                                    scope.launch { haptics.play(HapticEvent.Press) }
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    raw += dragAmount.x
                                    val k = ParameterMapping.resistance(resistanceT)
                                    val visual = RubberBand.apply(raw, boundPx, k)
                                    // Track what's on screen, in a stable frame: the shape moves under the finger,
                                    // so change.position (local to it) would read close to zero speed.
                                    tracker.addPosition(change.uptimeMillis, Offset(visual, 0f))
                                    scope.launch { offsetX.snapTo(visual) }
                                },
                                onDragCancel = { dragging = false },
                                onDragEnd = {
                                    dragging = false
                                    val releaseVelocity = tracker.calculateVelocity().x
                                    val stiffness = ParameterMapping.stiffness(stiffnessT)
                                    val dampingRatio = ParameterMapping.dampingRatio(dampingT)
                                    scope.launch {
                                        offsetX.animateTo(
                                            targetValue = 0f,
                                            animationSpec = spring(
                                                dampingRatio = dampingRatio,
                                                stiffness = stiffness,
                                            ),
                                            initialVelocity = releaseVelocity,
                                        )
                                        // animateTo only returns once Compose's own
                                        // convergence threshold is met, so this *is*
                                        // the settle event -- no separate "is it
                                        // close enough yet" check to keep in sync.
                                        haptics.play(HapticEvent.Release)
                                    }
                                },
                            )
                        },
                )
            }
            Spacer(Modifier.height(12.dp))
            Text("Drag the shape sideways and let go.", style = MaterialTheme.typography.bodySmall, color = t.inkSoft)
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
            LabeledSlider("Resistance", resistanceT, onValueChange = { resistanceT = it }, onValueChangeFinished = ::persist)
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

        val name = loaded?.name ?: "Spring Drag"
        Column(Modifier.reveal(6)) {
            ExportPanel(
                onApplySpring = ::applySpring,
                onApplyHaptic = ::applyHaptic,
                compose = {
                    SpringCodeGenerator.generate(
                        stiffness = ParameterMapping.stiffness(stiffnessT),
                        dampingRatio = ParameterMapping.dampingRatio(dampingT),
                        resistanceK = ParameterMapping.resistance(resistanceT),
                        bound = boundPx,
                    )
                },
                spec = {
                    DesignSpec.json(
                        name = name,
                        interaction = "spring_drag",
                        stiffness = ParameterMapping.stiffness(stiffnessT),
                        dampingRatio = ParameterMapping.dampingRatio(dampingT),
                        extras = listOf(
                            DesignSpec.Extra("resistanceK", ParameterMapping.resistance(resistanceT)),
                            DesignSpec.Extra("boundDp", with(density) { boundPx.toDp().value }),
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
