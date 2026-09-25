package com.motionlab.app.feature.editor

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.motionlab.app.core.haptics.HapticEngine
import com.motionlab.app.core.haptics.HapticEvent
import com.motionlab.app.core.haptics.HapticPreset
import com.motionlab.app.core.model.MotionPreset
import com.motionlab.app.core.model.ObjectStyle
import com.motionlab.app.core.physics.ParameterMapping
import com.motionlab.app.core.physics.ZoomSolver
import com.motionlab.app.data.ExperimentEntity
import com.motionlab.app.data.ExperimentRepository
import com.motionlab.app.export.DesignSpec
import com.motionlab.app.export.SpringMath
import com.motionlab.app.export.ZoomCodeGenerator
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
 * Pinch to zoom: two fingers scale the object, rubber-banded past its bounds
 * ([ZoomSolver], the same formula every other bounded drag here reuses).
 * Every interaction in this app ends at a real, demonstrable spring settle --
 * for Pinch to Zoom that's always back to 1x, on any release, in or out of
 * bounds; see [ZoomSolver]'s doc comment. Two fingers, so there's no
 * "dragging" boolean here the way single-pointer screens have one -- the live
 * curve gates on the [Animatable] alone, which is enough: it's running for
 * the whole gesture (every `snapTo` counts) and for the settle afterward.
 */
@Composable
fun PinchZoomScreen(
    experimentId: Long,
    repository: ExperimentRepository,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = remember { HapticEngine(context) }
    val tokens = LocalTokens.current

    var stiffnessT by remember { mutableFloatStateOf(0.4f) }
    var dampingT by remember { mutableFloatStateOf(0.6f) }
    var resistanceT by remember { mutableFloatStateOf(0.5f) }
    var minT by remember { mutableFloatStateOf(0.5f) }
    var maxT by remember { mutableFloatStateOf(0.5f) }
    var selectedPreset by remember { mutableStateOf<MotionPreset?>(null) }
    var hapticPreset by remember { mutableStateOf(HapticPreset.CRISP) }
    SideEffect { haptics.preset = hapticPreset }
    var objectStyle by remember { mutableStateOf(ObjectStyle(sizeT = 0.7f)) }

    var loaded by remember { mutableStateOf<ExperimentEntity?>(null) }
    LaunchedEffect(experimentId) {
        repository.get(experimentId)?.let { e ->
            loaded = e
            stiffnessT = e.stiffnessT
            dampingT = e.dampingT
            resistanceT = e.resistanceT ?: 0.5f
            minT = e.zoomMinT ?: 0.5f
            maxT = e.zoomMaxT ?: 0.5f
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
                    zoomMinT = minT,
                    zoomMaxT = maxT,
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

    fun minScale() = ParameterMapping.zoomMinScale(minT)
    fun maxScale() = ParameterMapping.zoomMaxScale(maxT)

    val scale = remember { Animatable(1f) }
    var crossed by remember { mutableStateOf(false) }
    // How far the curve needs to reach either side of 1x: the wider bound, plus headroom for overshoot.
    val curveRange = remember(minT, maxT) {
        maxOf(maxScale() - 1f, 1f - minScale(), 0.3f) * 1.4f
    }

    val samples = remember { mutableStateListOf<Float>() }
    LaunchedEffect(Unit) {
        while (true) {
            snapshotFlow { scale.isRunning }.first { it }
            while (scale.isRunning) {
                withFrameNanos {
                    // Centred on 1x so the curve's zero line is "no zoom", matching every other screen's rest position.
                    samples.add(scale.value - 1f)
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

    AppScreen(scrollable = true) {
        TopBar(title = "Pinch to zoom", onBack = onBack)
        Spacer(Modifier.height(8.dp))

        Column(Modifier.reveal(0)) {
            Stage {
                StageObject(
                    style = objectStyle,
                    modifier = Modifier
                        .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
                        .pointerInput(Unit) {
                            while (true) {
                                var raw = scale.value
                                scope.launch { haptics.play(HapticEvent.Press) }
                                detectTransformGestures { _, _, zoom, _ ->
                                    raw *= zoom
                                    val visual = ZoomSolver.visualScale(raw, minScale(), maxScale(), ParameterMapping.resistance(resistanceT))
                                    val nowCrossed = visual > maxScale() || visual < minScale()
                                    if (nowCrossed != crossed) {
                                        crossed = nowCrossed
                                        scope.launch { haptics.play(HapticEvent.Threshold) }
                                    }
                                    scope.launch { scale.snapTo(visual) }
                                }
                                // detectTransformGestures returns once every finger has lifted.
                                crossed = false
                                val stiffness = ParameterMapping.stiffness(stiffnessT)
                                val dampingRatio = ParameterMapping.dampingRatio(dampingT)
                                scope.launch {
                                    scale.animateTo(1f, spring(dampingRatio = dampingRatio, stiffness = stiffness))
                                    haptics.play(HapticEvent.Release)
                                }
                            }
                        },
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Pinch with two fingers; let go and it springs back to 1x.",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.inkSoft,
            )
            Spacer(Modifier.height(40.dp))
        }

        EditorSection("Presets", 1) {
            PresetRow(selected = selectedPreset, onSelect = ::applyPreset)
            TokenRow { s, d -> stiffnessT = s; dampingT = d; selectedPreset = null; persist() }
        }

        EditorSection("Motion", 2) {
            LabeledSlider("Zoom out limit", minT, onValueChange = { minT = it }, onValueChangeFinished = ::persist)
            LabeledSlider("Zoom in limit", maxT, onValueChange = { maxT = it }, onValueChangeFinished = ::persist)
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
            HapticControls(preset = hapticPreset, onSelect = {
                hapticPreset = it
                haptics.preset = it
                haptics.preview(it)
                persist()
            })
        }

        EditorSection("Curve", 5, trailing = readout) {
            PositionCurve(samples = samples, range = curveRange)
        }

        val name = loaded?.name ?: "Pinch to Zoom"
        EditorSection("Export", 6, gap = 0.dp) {
            ExportPanel(
                compose = {
                    ZoomCodeGenerator.generate(
                        stiffness = ParameterMapping.stiffness(stiffnessT),
                        dampingRatio = ParameterMapping.dampingRatio(dampingT),
                        minScale = minScale(),
                        maxScale = maxScale(),
                        resistanceK = ParameterMapping.resistance(resistanceT),
                    )
                },
                spec = {
                    DesignSpec.json(
                        name = name,
                        interaction = "pinch_zoom",
                        stiffness = ParameterMapping.stiffness(stiffnessT),
                        dampingRatio = ParameterMapping.dampingRatio(dampingT),
                        extras = listOf(
                            DesignSpec.Extra("minScale", minScale()),
                            DesignSpec.Extra("maxScale", maxScale()),
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
    }
}
