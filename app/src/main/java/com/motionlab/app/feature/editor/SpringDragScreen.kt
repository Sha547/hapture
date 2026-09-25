package com.motionlab.app.feature.editor

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
import com.motionlab.app.core.haptics.HapticEngine
import com.motionlab.app.core.haptics.HapticEvent
import com.motionlab.app.core.haptics.HapticPreset
import com.motionlab.app.core.model.MotionPreset
import com.motionlab.app.core.model.ObjectStyle
import com.motionlab.app.core.physics.MagneticSolver
import com.motionlab.app.core.physics.ParameterMapping
import com.motionlab.app.core.physics.RubberBand
import com.motionlab.app.core.physics.SnapPoints
import com.motionlab.app.data.ExperimentEntity
import com.motionlab.app.data.ExperimentRepository
import com.motionlab.app.export.DesignSpec
import com.motionlab.app.export.MagneticSnapCodeGenerator
import com.motionlab.app.export.SpringCodeGenerator
import com.motionlab.app.export.SpringMath
import com.motionlab.app.ui.design.AppScreen
import com.motionlab.app.ui.design.LocalTokens
import com.motionlab.app.ui.design.Stage
import com.motionlab.app.ui.design.StageObject
import com.motionlab.app.ui.design.TopBar
import com.motionlab.app.ui.design.reveal
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Phase 1 flagship interaction (spec §1, §51): drag the card, it follows
 * your finger, resists past a boundary, releases into a real spring, and
 * taps once when it settles. Every slider re-tunes the *same* spring the
 * card is animating with -- there's no separate "preview math" to keep in
 * sync with the export.
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

    val offsetX = remember { Animatable(0f) }
    val boundPx = remember(density) { with(density) { 56.dp.toPx() } }
    val curveRangePx = remember(density) { with(density) { 160.dp.toPx() } }
    var dragging by remember { mutableStateOf(false) }

    // Sampled once per frame for the live curve, but only while there's
    // actually something to plot -- dragging, or the spring still settling.
    // Sampling unconditionally for the screen's whole lifetime (the previous
    // version) meant Compose was never idle while this screen was open: no
    // real cost most of the time, but it made the screen impossible to
    // synchronize with in an instrumented test, which is how this was found
    // (see ComposeFlowTest). A SnapshotStateList add/trim every frame still
    // isn't free (spec §34) but at 120 points it's cheap relative to the
    // spring/gesture work already happening per frame while it's running.
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

    AppScreen(scrollable = true) {
        TopBar(title = "Spring drag", onBack = onBack)
        Spacer(Modifier.height(8.dp))

        Column(Modifier.reveal(0)) {
            // --- Interactive object: the visual hero (spec §20) ---
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
            Spacer(Modifier.height(40.dp))
        }

        EditorSection("Presets", 1) {
            PresetRow(selected = selectedPreset, onSelect = ::applyPreset)
            TokenRow { s, d -> stiffnessT = s; dampingT = d; selectedPreset = null; persist() }
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
            HapticControls(preset = hapticPreset, onSelect = {
                hapticPreset = it
                haptics.preset = it
                haptics.preview(it)
                persist()
            })
        }

        EditorSection("Curve", 5, trailing = readout) {
            PositionCurve(samples = samples, range = curveRangePx)
        }

        val name = loaded?.name ?: "Spring Drag"
        EditorSection("Export", 6, gap = 0.dp) {
            ExportPanel(
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
    }
}
