package com.klynstudios.hapture.feature.editor

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.klynstudios.hapture.core.haptics.HapticEngine
import com.klynstudios.hapture.core.haptics.HapticEvent
import com.klynstudios.hapture.core.haptics.HapticPreset
import com.klynstudios.hapture.core.model.MaterialSpring
import com.klynstudios.hapture.core.model.MotionPreset
import com.klynstudios.hapture.core.physics.CardExpandSolver
import com.klynstudios.hapture.core.physics.ParameterMapping
import com.klynstudios.hapture.core.spec.SpringSpec
import com.klynstudios.hapture.data.ExperimentEntity
import com.klynstudios.hapture.data.ExperimentRepository
import com.klynstudios.hapture.data.ExperimentType
import com.klynstudios.hapture.export.CardExpandCodeGenerator
import com.klynstudios.hapture.export.DesignSpec
import com.klynstudios.hapture.export.SpringMath
import com.klynstudios.hapture.feature.common.label
import com.klynstudios.hapture.ui.design.LocalBackdrop
import com.klynstudios.hapture.ui.design.LocalBackdropAction
import com.klynstudios.hapture.ui.design.LocalTokens
import com.klynstudios.hapture.ui.design.Stage
import com.klynstudios.hapture.ui.design.quietClickable
import com.klynstudios.hapture.ui.design.reveal
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val CARDS = 4

/**
 * A card that grows into a full page and back (Material's container transform). Tap any of the four cards
 * and it fills the stage while the rest dim; tap the page to send it home. One spring drives the bounds and
 * the corners; the contents fade through on their own timer.
 */
@Composable
fun CardExpandScreen(
    experimentId: Long,
    repository: ExperimentRepository,
    onBack: () -> Unit,
) {
    val t = LocalTokens.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val haptics = remember { HapticEngine(context) }

    var stiffnessT by remember { mutableFloatStateOf(ParameterMapping.stiffnessT(MaterialSpring.STANDARD_DEFAULT.stiffness)) }
    var dampingT by remember { mutableFloatStateOf(ParameterMapping.dampingT(MaterialSpring.STANDARD_DEFAULT.dampingRatio)) }
    var cornerT by remember { mutableFloatStateOf(0.4f) }
    var fadeT by remember { mutableFloatStateOf(0.4f) }
    var selectedPreset by remember { mutableStateOf<MotionPreset?>(null) }
    var hapticPreset by remember { mutableStateOf(HapticPreset.CRISP) }
    SideEffect { haptics.preset = hapticPreset }

    var loaded by remember { mutableStateOf<ExperimentEntity?>(null) }
    LaunchedEffect(experimentId) {
        repository.get(experimentId)?.let { e ->
            loaded = e
            stiffnessT = e.stiffnessT
            dampingT = e.dampingT
            cornerT = e.expandCornerT ?: 0.4f
            fadeT = e.expandFadeT ?: 0.4f
            hapticPreset = e.hapticPreset
        }
    }

    fun persist() {
        val e = loaded ?: return
        scope.launch {
            repository.save(
                e.copy(
                    stiffnessT = stiffnessT, dampingT = dampingT,
                    expandCornerT = cornerT, expandFadeT = fadeT,
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

    val progress = remember { Animatable(0f) }
    val fade = remember { Animatable(0f) }
    var selected by remember { mutableIntStateOf(0) }
    var open by remember { mutableStateOf(false) }

    fun toggle(index: Int) {
        if (!open) selected = index
        open = !open
        val target = if (open) 1f else 0f
        val opening = open
        scope.launch {
            haptics.play(HapticEvent.Press)
            progress.animateTo(
                target,
                spring(dampingRatio = ParameterMapping.dampingRatio(dampingT), stiffness = ParameterMapping.stiffness(stiffnessT)),
            )
            haptics.play(if (opening) HapticEvent.Snap else HapticEvent.Release)
        }
        scope.launch { fade.animateTo(target, tween(ParameterMapping.expandFadeMs(fadeT).toInt())) }
    }

    val samples = remember { mutableStateListOf<Float>() }
    LaunchedEffect(Unit) {
        while (true) {
            snapshotFlow { progress.isRunning }.first { it }
            while (progress.isRunning) {
                withFrameNanos {
                    samples.add((progress.value - 0.5f) * 2f)
                    if (samples.size > 120) samples.removeAt(0)
                }
            }
        }
    }

    val cornerDp = ParameterMapping.expandCornerDp(cornerT)
    val fadeMs = ParameterMapping.expandFadeMs(fadeT)
    val readout = remember(stiffnessT, dampingT) {
        val k = ParameterMapping.stiffness(stiffnessT)
        val z = ParameterMapping.dampingRatio(dampingT)
        "${SpringMath.settleMs(k, z)} ms  /  ${(SpringMath.overshoot(z) * 100).roundToInt()}% overshoot"
    }
    val name = loaded?.name ?: "Card Expand"

    EditorScaffold(
        title = ExperimentType.CARD_EXPAND.label,
        onBack = onBack,
        spring = SpringSpec(ParameterMapping.stiffness(stiffnessT), ParameterMapping.dampingRatio(dampingT)),
        stage = {
            Column(Modifier.reveal(0)) {
                // The cards fill the stage, so there's no room for a screenshot backdrop or its button.
                CompositionLocalProvider(LocalBackdropAction provides null, LocalBackdrop provides null) {
                    Stage(height = 280.dp) {
                        BoxWithConstraints(Modifier.fillMaxSize().testTag("expandStage")) {
                            val w = constraints.maxWidth.toFloat()
                            val h = constraints.maxHeight.toFloat()
                            val pad = with(density) { 14.dp.toPx() }
                            val gap = with(density) { 10.dp.toPx() }
                            val cellH = (h - 2 * pad - gap) / 2f
                            val page = CardExpandSolver.Box(0f, 0f, w, h)
                            val p = progress.value
                            val cornerPx = with(density) { cornerDp.dp.toPx() }

                            // The resting cards, dimming as the chosen one takes over the stage.
                            repeat(CARDS) { i ->
                                if (i != selected) {
                                    val cell = CardExpandSolver.cell(i, 2, w, pad, gap, cellH)
                                    MockCard(
                                        box = cell, cornerPx = cornerPx, cardAlpha = 1f, pageAlpha = 0f, index = i,
                                        modifier = Modifier
                                            .graphicsLayer { alpha = 1f - 0.7f * p.coerceIn(0f, 1f) }
                                            .testTag("expandCard$i"),
                                        onClick = { if (!open) toggle(i) },
                                    )
                                }
                            }
                            // The chosen card, drawn last so it grows over the others.
                            val from = CardExpandSolver.cell(selected, 2, w, pad, gap, cellH)
                            MockCard(
                                box = CardExpandSolver.lerp(from, page, p),
                                cornerPx = CardExpandSolver.cornerPx(cornerPx, p),
                                cardAlpha = CardExpandSolver.cardContentAlpha(fade.value),
                                pageAlpha = CardExpandSolver.pageContentAlpha(fade.value),
                                index = selected,
                                modifier = Modifier.testTag(if (open) "expandPage" else "expandCard$selected"),
                                onClick = { toggle(selected) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Tap a card to open it; tap the page to close it.", style = MaterialTheme.typography.bodySmall, color = t.inkSoft)
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
                LabeledSlider("Corner radius", cornerT, onValueChange = { cornerT = it }, onValueChangeFinished = ::persist)
                LabeledSlider("Content fade", fadeT, onValueChange = { fadeT = it }, onValueChangeFinished = ::persist)
                Text(
                    "Cards are ${cornerDp.roundToInt()} dp round. The card's contents fade out and the page's fade in over ${fadeMs.roundToInt()} ms.",
                    style = MaterialTheme.typography.bodySmall,
                    color = t.inkSoft,
                )
            }

            EditorSection("Haptics", 3) {
                HapticControls(preset = hapticPreset, onSelect = ::applyHaptic)
            }

            EditorSection("Curve", 4, trailing = readout) {
                PositionCurve(samples = samples, range = 1.6f)
            }

            Column(Modifier.reveal(5)) {
                ExportPanel(
                    onApplySpring = ::applySpring,
                    onApplyHaptic = ::applyHaptic,
                    compose = {
                        CardExpandCodeGenerator.generate(
                            stiffness = ParameterMapping.stiffness(stiffnessT),
                            dampingRatio = ParameterMapping.dampingRatio(dampingT),
                            cornerDp = cornerDp,
                            fadeMs = fadeMs,
                        )
                    },
                    spec = {
                        DesignSpec.json(
                            name = name,
                            interaction = "card_expand",
                            stiffness = ParameterMapping.stiffness(stiffnessT),
                            dampingRatio = ParameterMapping.dampingRatio(dampingT),
                            extras = listOf(
                                DesignSpec.Extra("cornerDp", cornerDp),
                                DesignSpec.Extra("fadeMs", fadeMs),
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

/**
 * A card at [box] (px, within the stage). It carries both faces: the card's small thumbnail-and-lines, and the
 * page's large header with paragraphs, cross-faded by [cardAlpha] and [pageAlpha].
 */
@Composable
private fun MockCard(
    box: CardExpandSolver.Box,
    cornerPx: Float,
    cardAlpha: Float,
    pageAlpha: Float,
    index: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val density = LocalDensity.current
    val shape = RoundedCornerShape(with(density) { cornerPx.toDp() })
    Box(
        modifier
            .offset { IntOffset(box.left.roundToInt(), box.top.roundToInt()) }
            .layout { measurable, _ ->
                val w = box.width.roundToInt().coerceAtLeast(1)
                val h = box.height.roundToInt().coerceAtLeast(1)
                val placeable = measurable.measure(Constraints.fixed(w, h))
                layout(w, h) { placeable.place(0, 0) }
            }
            .clip(shape)
            .background(t.line)
            .quietClickable(onClick),
    ) {
        // The card face: a thumbnail and two lines of title.
        Column(Modifier.fillMaxSize().padding(12.dp).graphicsLayer { alpha = cardAlpha }) {
            Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(t.inkFaint.copy(alpha = 0.6f)))
            Spacer(Modifier.weight(1f))
            Box(Modifier.fillMaxWidth(if (index % 2 == 0) 0.8f else 0.65f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(t.inkSoft.copy(alpha = 0.7f)))
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth(0.5f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(t.inkFaint.copy(alpha = 0.6f)))
        }
        // The page face: a big header image, a title and a few paragraphs.
        Column(Modifier.fillMaxSize().graphicsLayer { alpha = pageAlpha }) {
            Box(Modifier.fillMaxWidth().weight(0.42f).background(t.inkFaint.copy(alpha = 0.45f)))
            Column(Modifier.padding(16.dp).weight(0.58f)) {
                Box(Modifier.width(150.dp).height(12.dp).clip(RoundedCornerShape(6.dp)).background(t.ink.copy(alpha = 0.75f)))
                Spacer(Modifier.height(12.dp))
                repeat(3) { r ->
                    Box(Modifier.fillMaxWidth(if (r == 2) 0.6f else 1f).height(7.dp).clip(RoundedCornerShape(4.dp)).background(t.inkSoft.copy(alpha = 0.55f)))
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}
