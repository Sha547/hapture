package com.motionlab.app.feature.editor

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.motionlab.app.core.haptics.HapticEngine
import com.motionlab.app.core.haptics.HapticEvent
import com.motionlab.app.core.haptics.HapticPreset
import com.motionlab.app.core.model.MotionPreset
import com.motionlab.app.core.model.ObjectFill
import com.motionlab.app.core.model.ObjectStyle
import com.motionlab.app.core.physics.ParameterMapping
import com.motionlab.app.core.physics.ReorderSolver
import com.motionlab.app.data.ExperimentEntity
import com.motionlab.app.data.ExperimentRepository
import com.motionlab.app.export.DesignSpec
import com.motionlab.app.export.ReorderCodeGenerator
import com.motionlab.app.export.SpringMath
import com.motionlab.app.ui.design.AppScreen
import com.motionlab.app.ui.design.LocalTokens
import com.motionlab.app.ui.design.Stage
import com.motionlab.app.ui.design.TopBar
import com.motionlab.app.ui.design.objectColors
import com.motionlab.app.ui.design.reveal
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val ITEM_HEIGHT = 40.dp
private val ITEM_GAP = 8.dp
private val ITEM_LABELS = listOf("Item 1", "Item 2", "Item 3", "Item 4")

/**
 * Drag to reorder: the one screen here that isn't "one object on a stage" --
 * a small fixed list, since that's what this interaction actually is. Drag a
 * row; cross [ReorderSolver]'s threshold into a neighbor's slot and releasing
 * settles it there with a real `spring()`. Only the dragged row animates on
 * release -- the others snap straight to their new slot rather than sliding
 * to make room live while you drag. That's a real, honest simplification
 * (every other screen's "spring only fires on release" rule stays true here
 * too, instead of also live-animating neighbors mid-drag), not a bug.
 */
@Composable
fun DragReorderScreen(
    experimentId: Long,
    repository: ExperimentRepository,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val haptics = remember { HapticEngine(context) }
    val tokens = LocalTokens.current

    var stiffnessT by remember { mutableFloatStateOf(0.55f) }
    var dampingT by remember { mutableFloatStateOf(0.7f) }
    var thresholdT by remember { mutableFloatStateOf(0.5f) }
    var selectedPreset by remember { mutableStateOf<MotionPreset?>(null) }
    var hapticPreset by remember { mutableStateOf(HapticPreset.CRISP) }
    SideEffect { haptics.preset = hapticPreset }
    var objectStyle by remember { mutableStateOf(ObjectStyle(fill = ObjectFill.MARKER)) }

    var loaded by remember { mutableStateOf<ExperimentEntity?>(null) }
    LaunchedEffect(experimentId) {
        repository.get(experimentId)?.let { e ->
            loaded = e
            stiffnessT = e.stiffnessT
            dampingT = e.dampingT
            thresholdT = e.reorderThresholdT ?: 0.5f
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
                    reorderThresholdT = thresholdT,
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

    val itemHeightPx = with(density) { (ITEM_HEIGHT + ITEM_GAP).toPx() }
    fun thresholdPx() = ParameterMapping.reorderThresholdFraction(thresholdT) * itemHeightPx

    // The order itself: order[slot] = which original label sits there. Stable
    // labels tied to their original index, not their current slot, so "Item 2"
    // is always the same row wherever it ends up.
    val order = remember { mutableStateListOf(0, 1, 2, 3) }
    var draggingSlot by remember { mutableIntStateOf(-1) }
    val dragY = remember { Animatable(0f) }
    val curveRangePx = remember(density) { itemHeightPx * 1.5f }

    val samples = remember { mutableStateListOf<Float>() }
    LaunchedEffect(Unit) {
        while (true) {
            snapshotFlow { draggingSlot >= 0 || dragY.isRunning }.first { it }
            while (draggingSlot >= 0 || dragY.isRunning) {
                withFrameNanos {
                    samples.add(dragY.value)
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
        TopBar(title = "Drag to reorder", onBack = onBack)
        Spacer(Modifier.height(8.dp))

        Column(Modifier.reveal(0)) {
            Stage(height = 220.dp) {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    order.forEachIndexed { slot, original ->
                        val corner = sheetCornerDp(objectStyle.shape).dp
                        val (fill, border) = objectColors(objectStyle.fill, tokens)
                        val onColor = if (objectStyle.fill == ObjectFill.INK) tokens.canvas else tokens.ink
                        val yOffset = if (draggingSlot == slot) dragY.value.roundToInt() else 0

                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(ITEM_HEIGHT)
                                .offset { IntOffset(0, yOffset) }
                                .background(fill, RoundedCornerShape(corner))
                                .border(
                                    if (objectStyle.fill == ObjectFill.PAPER) 1.5.dp else tokens.hairline,
                                    border,
                                    RoundedCornerShape(corner),
                                )
                                .pointerInput(order) {
                                    var raw = 0f
                                    detectDragGestures(
                                        onDragStart = {
                                            raw = 0f
                                            draggingSlot = slot
                                            scope.launch { dragY.snapTo(0f) }
                                            scope.launch { haptics.play(HapticEvent.Press) }
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            raw += dragAmount.y
                                            scope.launch { dragY.snapTo(raw) }
                                        },
                                        onDragCancel = { draggingSlot = -1; scope.launch { dragY.snapTo(0f) } },
                                        onDragEnd = {
                                            val target = ReorderSolver.targetIndex(slot, raw, itemHeightPx, thresholdPx(), order.size)
                                            val stiffness = ParameterMapping.stiffness(stiffnessT)
                                            val dampingRatio = ParameterMapping.dampingRatio(dampingT)
                                            scope.launch {
                                                dragY.animateTo(
                                                    (target - slot) * itemHeightPx,
                                                    spring(dampingRatio = dampingRatio, stiffness = stiffness),
                                                )
                                                if (target != slot) {
                                                    val reordered = ReorderSolver.reordered(order.toList(), slot, target)
                                                    order.clear()
                                                    order.addAll(reordered)
                                                    haptics.play(HapticEvent.Snap)
                                                } else {
                                                    haptics.play(HapticEvent.Release)
                                                }
                                                draggingSlot = -1
                                                dragY.snapTo(0f)
                                            }
                                        },
                                    )
                                },
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(
                                ITEM_LABELS[original],
                                style = MaterialTheme.typography.labelLarge,
                                color = onColor,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                        if (slot != order.lastIndex) Spacer(Modifier.height(ITEM_GAP))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Drag a row past its neighbor and let go.",
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
            LabeledSlider("Swap sensitivity", thresholdT, onValueChange = { thresholdT = it }, onValueChangeFinished = ::persist)
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

        val name = loaded?.name ?: "Drag to Reorder"
        EditorSection("Export", 6, gap = 0.dp) {
            ExportPanel(
                compose = {
                    ReorderCodeGenerator.generate(
                        stiffness = ParameterMapping.stiffness(stiffnessT),
                        dampingRatio = ParameterMapping.dampingRatio(dampingT),
                        itemHeightDp = itemHeightPx / density.density,
                        thresholdDp = thresholdPx() / density.density,
                    )
                },
                spec = {
                    DesignSpec.json(
                        name = name,
                        interaction = "drag_reorder",
                        stiffness = ParameterMapping.stiffness(stiffnessT),
                        dampingRatio = ParameterMapping.dampingRatio(dampingT),
                        extras = listOf(
                            DesignSpec.Extra("itemHeightDp", with(density) { itemHeightPx / density.density }),
                            DesignSpec.Extra("swapThresholdDp", with(density) { thresholdPx() / density.density }),
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
