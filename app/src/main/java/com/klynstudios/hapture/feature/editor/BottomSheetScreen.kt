package com.klynstudios.hapture.feature.editor

import com.klynstudios.hapture.core.spec.SpringSpec
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.klynstudios.hapture.core.haptics.HapticEngine
import com.klynstudios.hapture.core.haptics.HapticEvent
import com.klynstudios.hapture.core.haptics.HapticPreset
import com.klynstudios.hapture.core.model.MotionPreset
import com.klynstudios.hapture.core.model.ObjectFill
import com.klynstudios.hapture.core.model.ObjectShape
import com.klynstudios.hapture.core.model.ObjectStyle
import com.klynstudios.hapture.core.physics.ParameterMapping
import com.klynstudios.hapture.core.physics.SheetSolver
import com.klynstudios.hapture.core.physics.SnapPoints
import com.klynstudios.hapture.data.ExperimentEntity
import com.klynstudios.hapture.data.ExperimentRepository
import com.klynstudios.hapture.export.DesignSpec
import com.klynstudios.hapture.export.SheetCodeGenerator
import com.klynstudios.hapture.export.SpringMath
import com.klynstudios.hapture.ui.design.AppScreen
import com.klynstudios.hapture.ui.design.Chip
import com.klynstudios.hapture.ui.design.DashedGuide
import com.klynstudios.hapture.ui.design.LocalTokens
import com.klynstudios.hapture.ui.design.Stage
import com.klynstudios.hapture.ui.design.TopBar
import com.klynstudios.hapture.ui.design.objectColors
import com.klynstudios.hapture.ui.design.reveal
import com.klynstudios.hapture.ui.theme.MonoSmall
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Bottom sheet: drag the sheet's edge and it follows, resists past full,
 * remembers how fast you were going, and settles at the nearest of three
 * detents (peek, middle, full) with a real `spring()`. Pull it below the peek,
 * or flick it down, and it dismisses. Where it settles is [SheetSolver], the
 * same formula the exported code carries.
 */
@Composable
fun BottomSheetScreen(
    experimentId: Long,
    repository: ExperimentRepository,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val haptics = remember { HapticEngine(context) }
    val tokens = LocalTokens.current

    var stiffnessT by remember { mutableFloatStateOf(0.45f) }
    var dampingT by remember { mutableFloatStateOf(0.65f) }
    var resistanceT by remember { mutableFloatStateOf(0.5f) }
    var peekT by remember { mutableFloatStateOf(0.4f) }
    var midT by remember { mutableFloatStateOf(0.5f) }
    var momentumT by remember { mutableFloatStateOf(0.5f) }
    var dismissT by remember { mutableFloatStateOf(0.5f) }
    var selectedPreset by remember { mutableStateOf<MotionPreset?>(null) }
    var hapticPreset by remember { mutableStateOf(HapticPreset.CRISP) }
    SideEffect { haptics.preset = hapticPreset }
    var look by remember { mutableStateOf(ObjectStyle(shape = ObjectShape.ROUNDED, fill = ObjectFill.MARKER)) }

    var loaded by remember { mutableStateOf<ExperimentEntity?>(null) }
    LaunchedEffect(experimentId) {
        repository.get(experimentId)?.let { e ->
            loaded = e
            stiffnessT = e.stiffnessT
            dampingT = e.dampingT
            resistanceT = e.resistanceT ?: 0.5f
            peekT = e.sheetPeekT ?: 0.4f
            midT = e.sheetMidT ?: 0.5f
            momentumT = e.sheetMomentumT ?: 0.5f
            dismissT = e.sheetDismissT ?: 0.5f
            look = e.objectStyle
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
                    resistanceT = resistanceT,
                    sheetPeekT = peekT,
                    sheetMidT = midT,
                    sheetMomentumT = momentumT,
                    sheetDismissT = dismissT,
                    updatedAt = System.currentTimeMillis(),
                ).withObjectStyle(look).copy(hapticPreset = hapticPreset)
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

    // Heights are "how much of the sheet is showing", px, measured from the stage's bottom edge.
    var stageHeightPx by remember { mutableFloatStateOf(with(density) { STAGE_HEIGHT_DP.toPx() }) }
    fun peekPx() = stageHeightPx * ParameterMapping.sheetPeekFraction(peekT)
    fun midPx() = stageHeightPx * ParameterMapping.sheetMidFraction(midT)
    fun fullPx() = stageHeightPx * ParameterMapping.SHEET_FULL_FRACTION
    fun detents() = floatArrayOf(peekPx(), midPx(), fullPx())
    fun dismissLine() = peekPx() * ParameterMapping.sheetDismissLineFraction(dismissT)

    val revealed = remember { Animatable(with(density) { STAGE_HEIGHT_DP.toPx() } * ParameterMapping.sheetPeekFraction(0.4f)) }
    var restIndex by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    var hidden by remember { mutableStateOf(false) }

    // While you tune the detents, the sheet's edge follows them so you can see what you're changing.
    LaunchedEffect(peekT, midT, stageHeightPx) {
        if (!dragging && !hidden && !revealed.isRunning) revealed.snapTo(detents()[restIndex])
    }

    // Only sample while there's motion -- dragging, or the sheet still
    // settling -- so Compose is genuinely idle the rest of the time. An
    // unconditional per-frame loop here made this screen never idle, which
    // an instrumented test caught (see ComposeFlowTest and the same note on
    // SpringDragScreen).
    val samples = remember { mutableStateListOf<Float>() }
    LaunchedEffect(Unit) {
        while (true) {
            snapshotFlow { dragging || revealed.isRunning }.first { it }
            while (dragging || revealed.isRunning) {
                withFrameNanos {
                    // Plotted around the peek, so the zero line is the sheet's resting position.
                    samples.add(revealed.value - peekPx())
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
        title = "Bottom sheet",
        onBack = onBack,
        spring = SpringSpec(ParameterMapping.stiffness(stiffnessT), ParameterMapping.dampingRatio(dampingT)),
        stage = {
        Column(Modifier.reveal(0)) {
            Stage(Modifier.onSizeChanged { stageHeightPx = it.height.toFloat() }, height = STAGE_HEIGHT_DP) {
                // Scrim that deepens as the sheet rises, then the detent guides on top of it.
                Canvas(Modifier.matchParentSize()) {
                    val frac = (revealed.value / fullPx()).coerceIn(0f, 1f)
                    drawRect(Color.Black.copy(alpha = 0.22f * frac))
                    detents().forEach { d ->
                        val y = size.height - d
                        drawLine(tokens.ink.copy(alpha = 0.16f), Offset(0f, y), Offset(size.width, y), 1.5f, pathEffect = DashedGuide)
                    }
                }
                listOf("PEEK", "MID", "FULL").forEachIndexed { i, label ->
                    Text(
                        text = label,
                        style = MonoSmall.copy(fontSize = 10.sp),
                        color = tokens.inkFaint,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            // Above its line, except FULL, which would clip against the stage's top edge.
                            .offset {
                                val lift = if (i < 2) detents()[i] + 16.dp.toPx() else detents()[i] - 16.dp.toPx()
                                IntOffset(10.dp.roundToPx(), -lift.roundToInt())
                            },
                    )
                }

                val r = sheetCornerDp(look.shape).dp
                val shape = RoundedCornerShape(topStart = r, topEnd = r)
                val (fill, border) = objectColors(look.fill, tokens)
                val on = if (look.fill == ObjectFill.INK) tokens.canvas else tokens.ink

                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(horizontal = 1.dp)
                        .fillMaxWidth()
                        .height(with(density) { stageHeightPx.toDp() })
                        .offset { IntOffset(0, (stageHeightPx - revealed.value).roundToInt()) }
                        .background(fill, shape)
                        .border(if (look.fill == ObjectFill.PAPER) 1.5.dp else 1.dp, border, shape)
                        .pointerInput(Unit) {
                            var raw = 0f
                            var tracker = VelocityTracker()
                            var zone = 0
                            detectDragGestures(
                                onDragStart = {
                                    raw = revealed.value
                                    tracker = VelocityTracker()
                                    dragging = true
                                    zone = SnapPoints.nearestIndex(raw, detents())
                                    scope.launch { haptics.play(HapticEvent.Press) }
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    raw -= dragAmount.y
                                    val visual = SheetSolver.visualHeight(raw, fullPx(), ParameterMapping.resistance(resistanceT))
                                    // Track the height itself: the sheet moves under the finger, so
                                    // pointer-local positions would barely change and read as no speed.
                                    tracker.addPosition(change.uptimeMillis, Offset(0f, visual))
                                    val nowZone = SnapPoints.nearestIndex(visual, detents())
                                    if (nowZone != zone) {
                                        zone = nowZone
                                        scope.launch { haptics.play(HapticEvent.Threshold) }
                                    }
                                    scope.launch { revealed.snapTo(visual) }
                                },
                                onDragCancel = { dragging = false },
                                onDragEnd = {
                                    dragging = false
                                    val velocity = tracker.calculateVelocity().y
                                    val all = detents()
                                    val target = SheetSolver.settleHeight(
                                        revealed.value, velocity,
                                        ParameterMapping.sheetMomentumSec(momentumT), all, dismissLine(),
                                    )
                                    scope.launch {
                                        revealed.animateTo(
                                            targetValue = target,
                                            animationSpec = spring(
                                                dampingRatio = ParameterMapping.dampingRatio(dampingT),
                                                stiffness = ParameterMapping.stiffness(stiffnessT),
                                            ),
                                            initialVelocity = velocity,
                                        )
                                        if (target == 0f) {
                                            hidden = true
                                            haptics.play(HapticEvent.Release)
                                        } else {
                                            restIndex = all.indexOfFirst { it == target }.coerceAtLeast(0)
                                            haptics.play(HapticEvent.Snap)
                                        }
                                    }
                                },
                            )
                        },
                ) {
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .size(36.dp, 4.dp)
                            .background(on.copy(alpha = 0.35f), RoundedCornerShape(2.dp))
                    )
                    Spacer(Modifier.height(20.dp))
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        listOf(0.45f, 0.9f, 0.8f, 0.9f, 0.6f).forEachIndexed { i, w ->
                            Box(
                                Modifier
                                    .fillMaxWidth(w)
                                    .height(if (i == 0) 10.dp else 8.dp)
                                    .background(on.copy(alpha = if (i == 0) 0.28f else 0.14f), RoundedCornerShape(4.dp))
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }

                if (hidden) {
                    Chip("Show sheet", selected = false, onClick = {
                        hidden = false
                        restIndex = 0
                        scope.launch {
                            haptics.play(HapticEvent.Snap)
                            revealed.animateTo(
                                peekPx(),
                                spring(
                                    dampingRatio = ParameterMapping.dampingRatio(dampingT),
                                    stiffness = ParameterMapping.stiffness(stiffnessT),
                                ),
                            )
                        }
                    })
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Drag the sheet. It settles at peek, middle or full; pull it below the peek to dismiss.",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.inkSoft,
            )
        }
        },
        controls = {
        EditorSection("Detents", 1) {
            LabeledSlider("Peek height", peekT, onValueChange = { peekT = it }, onValueChangeFinished = ::persist)
            LabeledSlider("Middle height", midT, onValueChange = { midT = it }, onValueChangeFinished = ::persist)
        }

        EditorSection("Behaviour", 2) {
            LabeledSlider("Momentum", momentumT, onValueChange = { momentumT = it }, onValueChangeFinished = ::persist)
            LabeledSlider("Dismiss ease", dismissT, onValueChange = { dismissT = it }, onValueChangeFinished = ::persist)
            LabeledSlider("Resistance", resistanceT, onValueChange = { resistanceT = it }, onValueChangeFinished = ::persist)
        }

        EditorSection("Spring", 3) {
            SpringPresets(
                name = loaded?.name ?: "",
                stiffnessT = stiffnessT,
                dampingT = dampingT,
                selected = selectedPreset,
                onPreset = ::applyPreset,
                onSpring = ::applySpring,
            )
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

        EditorSection("Look", 4) {
            SheetLookControls(style = look, onChange = { look = it }, onCommit = ::persist)
        }

        EditorSection("Haptics", 5) {
            HapticControls(preset = hapticPreset, onSelect = ::applyHaptic)
        }

        EditorSection("Curve", 6, trailing = readout) {
            PositionCurve(samples = samples, range = (fullPx() - peekPx()).coerceAtLeast(1f))
        }

        val name = loaded?.name ?: "Bottom Sheet"
        Column(Modifier.reveal(7)) {
            ExportPanel(
                onApplySpring = ::applySpring,
                onApplyHaptic = ::applyHaptic,
                compose = {
                    SheetCodeGenerator.generate(
                        stiffness = ParameterMapping.stiffness(stiffnessT),
                        dampingRatio = ParameterMapping.dampingRatio(dampingT),
                        peekFraction = ParameterMapping.sheetPeekFraction(peekT),
                        midFraction = ParameterMapping.sheetMidFraction(midT),
                        fullFraction = ParameterMapping.SHEET_FULL_FRACTION,
                        momentumSec = ParameterMapping.sheetMomentumSec(momentumT),
                        dismissLineFraction = ParameterMapping.sheetDismissLineFraction(dismissT),
                        resistanceK = ParameterMapping.resistance(resistanceT),
                    )
                },
                spec = {
                    DesignSpec.json(
                        name = name,
                        interaction = "bottom_sheet",
                        stiffness = ParameterMapping.stiffness(stiffnessT),
                        dampingRatio = ParameterMapping.dampingRatio(dampingT),
                        extras = listOf(
                            DesignSpec.Extra("peekFraction", ParameterMapping.sheetPeekFraction(peekT)),
                            DesignSpec.Extra("midFraction", ParameterMapping.sheetMidFraction(midT)),
                            DesignSpec.Extra("fullFraction", ParameterMapping.SHEET_FULL_FRACTION),
                            DesignSpec.Extra("momentumSec", ParameterMapping.sheetMomentumSec(momentumT)),
                            DesignSpec.Extra("dismissLineOfPeek", ParameterMapping.sheetDismissLineFraction(dismissT)),
                            DesignSpec.Extra("resistanceK", ParameterMapping.resistance(resistanceT)),
                            DesignSpec.Extra("cornerRadiusDp", sheetCornerDp(look.shape)),
                        ),
                        style = look,
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

private val STAGE_HEIGHT_DP = 300.dp
