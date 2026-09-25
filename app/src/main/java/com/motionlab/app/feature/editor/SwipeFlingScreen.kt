package com.motionlab.app.feature.editor

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
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
import com.motionlab.app.core.physics.SwipeSolver
import com.motionlab.app.data.ExperimentEntity
import com.motionlab.app.data.ExperimentRepository
import com.motionlab.app.export.DesignSpec
import com.motionlab.app.export.SpringMath
import com.motionlab.app.export.SwipeCodeGenerator
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
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Swipe to dismiss (velocity-driven): drag the card past the guides, or flick
 * it, and it is thrown off with Compose's real `exponentialDecay`; let go short
 * and slow and it springs back with a real `spring()`. The decision itself is
 * [SwipeSolver], the same formula the exported code carries. After a dismissal
 * the card fades back in so you can throw it again.
 */
@Composable
fun SwipeFlingScreen(
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
    var distanceT by remember { mutableFloatStateOf(0.4f) }
    var sensitivityT by remember { mutableFloatStateOf(0.5f) }
    var frictionT by remember { mutableFloatStateOf(0.35f) }
    var tiltT by remember { mutableFloatStateOf(0.4f) }
    var selectedPreset by remember { mutableStateOf<MotionPreset?>(null) }
    var hapticPreset by remember { mutableStateOf(HapticPreset.CRISP) }
    SideEffect { haptics.preset = hapticPreset }
    var objectStyle by remember { mutableStateOf(ObjectStyle(sizeT = 0.8f)) }

    var loaded by remember { mutableStateOf<ExperimentEntity?>(null) }
    LaunchedEffect(experimentId) {
        repository.get(experimentId)?.let { e ->
            loaded = e
            stiffnessT = e.stiffnessT
            dampingT = e.dampingT
            distanceT = e.flingDistanceT ?: 0.4f
            sensitivityT = e.flingSensitivityT ?: 0.5f
            frictionT = e.flingFrictionT ?: 0.35f
            tiltT = e.flingTiltT ?: 0.4f
            objectStyle = e.objectStyle
            hapticPreset = e.hapticPreset
        }
    }

    // Once per slider release, not per frame -- see LabeledSlider.
    fun persist() {
        val e = loaded ?: return
        scope.launch {
            repository.save(
                e.copy(
                    stiffnessT = stiffnessT,
                    dampingT = dampingT,
                    flingDistanceT = distanceT,
                    flingSensitivityT = sensitivityT,
                    flingFrictionT = frictionT,
                    flingTiltT = tiltT,
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

    // Read at gesture time, not captured: the sliders can change between drags.
    fun distancePx() = with(density) { ParameterMapping.flingDistanceDp(distanceT).dp.toPx() }
    fun velocityThresholdPx() = with(density) { ParameterMapping.flingVelocityDpPerSec(sensitivityT).dp.toPx() }
    fun friction() = ParameterMapping.flingFriction(frictionT)
    fun maxTilt() = ParameterMapping.flingTiltDegrees(tiltT)

    var stageWidthPx by remember { mutableFloatStateOf(with(density) { 320.dp.toPx() }) }
    // Far enough that the card is fully clear of the stage.
    fun exitPx() = stageWidthPx / 2f + with(density) { (objectStyle.sizeDp * objectStyle.shape.aspect).dp.toPx() }

    val offsetX = remember { Animatable(0f) }
    val cardAlpha = remember { Animatable(1f) }
    val cardScale = remember { Animatable(1f) }
    var busy by remember { mutableStateOf(false) }
    var crossed by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    val curveRangePx = remember(density) { with(density) { 240.dp.toPx() } }

    // Only sample while there's motion -- dragging, or the card still moving
    // (thrown off, springing back, or the decay/exit throw) -- so Compose is
    // genuinely idle the rest of the time. An unconditional per-frame loop
    // here made this screen never idle, which an instrumented test caught
    // (see ComposeFlowTest and the same note on SpringDragScreen).
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

    val readout = remember(stiffnessT, dampingT) {
        val k = ParameterMapping.stiffness(stiffnessT)
        val z = ParameterMapping.dampingRatio(dampingT)
        "${SpringMath.settleMs(k, z)} ms  /  ${(SpringMath.overshoot(z) * 100).roundToInt()}% return overshoot"
    }

    AppScreen(scrollable = true) {
        TopBar(title = "Swipe and fling", onBack = onBack)
        Spacer(Modifier.height(8.dp))

        Column(Modifier.reveal(0)) {
            Stage(Modifier.onSizeChanged { stageWidthPx = it.width.toFloat() }) {
                // Dismiss lines: they darken once you're past them.
                Canvas(Modifier.matchParentSize()) {
                    val cx = size.width / 2f
                    val d = distancePx()
                    val color = if (crossed) tokens.ink.copy(alpha = 0.6f) else tokens.ink.copy(alpha = 0.16f)
                    val top = 18.dp.toPx()
                    val bottom = size.height - 18.dp.toPx()
                    drawLine(color, Offset(cx - d, top), Offset(cx - d, bottom), 1.5f, pathEffect = DashedGuide)
                    drawLine(color, Offset(cx + d, top), Offset(cx + d, bottom), 1.5f, pathEffect = DashedGuide)
                }
                StageObject(
                    style = objectStyle,
                    modifier = Modifier
                        .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                        .graphicsLayer {
                            rotationZ = SwipeSolver.tiltDegrees(offsetX.value, distancePx(), maxTilt())
                            alpha = cardAlpha.value
                            scaleX = cardScale.value
                            scaleY = cardScale.value
                        }
                        .pointerInput(Unit) {
                            var raw = 0f
                            var tracker = VelocityTracker()
                            detectDragGestures(
                                onDragStart = {
                                    if (busy) return@detectDragGestures
                                    raw = offsetX.value
                                    tracker = VelocityTracker()
                                    dragging = true
                                    crossed = abs(raw) >= distancePx()
                                    scope.launch { haptics.play(HapticEvent.Press) }
                                },
                                onDrag = { change, dragAmount ->
                                    if (busy) return@detectDragGestures
                                    change.consume()
                                    raw += dragAmount.x
                                    // Track what's on screen, in a stable frame: the shape moves under the finger,
                                    // so change.position (local to it) would read close to zero speed.
                                    tracker.addPosition(change.uptimeMillis, Offset(raw, 0f))
                                    val past = abs(raw) >= distancePx()
                                    if (past != crossed) {
                                        crossed = past
                                        scope.launch { haptics.play(HapticEvent.Threshold) }
                                    }
                                    scope.launch { offsetX.snapTo(raw) }
                                },
                                onDragCancel = { dragging = false },
                                onDragEnd = {
                                    dragging = false
                                    if (busy) return@detectDragGestures
                                    val velocity = tracker.calculateVelocity().x
                                    val threshold = velocityThresholdPx()
                                    val direction = SwipeSolver.dismissDirection(offsetX.value, velocity, distancePx(), threshold)
                                    crossed = false
                                    scope.launch {
                                        if (direction == 0) {
                                            offsetX.animateTo(
                                                targetValue = 0f,
                                                animationSpec = spring(
                                                    dampingRatio = ParameterMapping.dampingRatio(dampingT),
                                                    stiffness = ParameterMapping.stiffness(stiffnessT),
                                                ),
                                                initialVelocity = velocity,
                                            )
                                            haptics.play(HapticEvent.Release)
                                        } else {
                                            busy = true
                                            haptics.play(HapticEvent.Impact)
                                            // A slow release past the distance is thrown at the threshold speed.
                                            val thrown = if (abs(velocity) >= threshold) velocity else direction * threshold
                                            offsetX.animateDecay(thrown, exponentialDecay(frictionMultiplier = friction()))
                                            val exit = exitPx()
                                            if (abs(offsetX.value) < exit) {
                                                offsetX.animateTo(direction * exit, tween(160))
                                            }
                                            // Bring the card back so you can throw it again.
                                            delay(380)
                                            offsetX.snapTo(0f)
                                            cardAlpha.snapTo(0f)
                                            cardScale.snapTo(0.92f)
                                            launch { cardAlpha.animateTo(1f, tween(280)) }
                                            cardScale.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 200f))
                                            busy = false
                                        }
                                    }
                                },
                            )
                        },
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Flick the card away, or drag past the lines and let go.",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.inkSoft,
            )
            Spacer(Modifier.height(40.dp))
        }

        EditorSection("Fling", 1) {
            LabeledSlider("Dismiss distance", distanceT, onValueChange = { distanceT = it }, onValueChangeFinished = ::persist)
            LabeledSlider("Fling sensitivity", sensitivityT, onValueChange = { sensitivityT = it }, onValueChangeFinished = ::persist)
            LabeledSlider("Friction", frictionT, onValueChange = { frictionT = it }, onValueChangeFinished = ::persist)
            LabeledSlider("Tilt", tiltT, onValueChange = { tiltT = it }, onValueChangeFinished = ::persist)
        }

        EditorSection("Return spring", 2) {
            PresetRow(selected = selectedPreset, onSelect = ::applyPreset)
            TokenRow { s, d -> stiffnessT = s; dampingT = d; selectedPreset = null; persist() }
            Spacer(Modifier.height(16.dp))
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

        val name = loaded?.name ?: "Swipe and Fling"
        EditorSection("Export", 6, gap = 0.dp) {
            ExportPanel(
                compose = {
                    SwipeCodeGenerator.generate(
                        stiffness = ParameterMapping.stiffness(stiffnessT),
                        dampingRatio = ParameterMapping.dampingRatio(dampingT),
                        distanceDp = ParameterMapping.flingDistanceDp(distanceT),
                        velocityDpPerSec = ParameterMapping.flingVelocityDpPerSec(sensitivityT),
                        friction = ParameterMapping.flingFriction(frictionT),
                        tiltDegrees = ParameterMapping.flingTiltDegrees(tiltT),
                    )
                },
                spec = {
                    DesignSpec.json(
                        name = name,
                        interaction = "swipe_fling",
                        stiffness = ParameterMapping.stiffness(stiffnessT),
                        dampingRatio = ParameterMapping.dampingRatio(dampingT),
                        extras = listOf(
                            DesignSpec.Extra("dismissDistanceDp", ParameterMapping.flingDistanceDp(distanceT)),
                            DesignSpec.Extra("velocityThresholdDpPerSec", ParameterMapping.flingVelocityDpPerSec(sensitivityT)),
                            DesignSpec.Extra("frictionMultiplier", ParameterMapping.flingFriction(frictionT)),
                            DesignSpec.Extra("tiltDegrees", ParameterMapping.flingTiltDegrees(tiltT)),
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
