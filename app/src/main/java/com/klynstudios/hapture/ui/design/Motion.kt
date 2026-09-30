package com.klynstudios.hapture.ui.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntSize

/**
 * True when the phone's "remove animations" setting is on. Every piece of motion the app adds around its
 * content (screen changes, list changes, press feedback, entrances) checks this and drops to nothing.
 * The tuning demos are the exception on purpose: moving is their whole job.
 */
val LocalReduceMotion = compositionLocalOf { false }

/** Android's animator scale is 0 when animations are switched off. */
fun isMotionReduced(animatorDurationScale: Float): Boolean = animatorDurationScale == 0f

/** A click that presses in a hair and springs back. Does nothing extra when motion is reduced. */
fun Modifier.pressable(onClick: () -> Unit, scaleTo: Float = 0.97f): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val reduce = LocalReduceMotion.current
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduce) scaleTo else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium),
        label = "pressScale",
    )
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = null, onClick = onClick)
}

/**
 * A list row that eases in when it is [animateIn] (added while you're looking) and eases out when [leaving],
 * so the rows below close the gap smoothly instead of jumping. Rows that were already there just show.
 */
@Composable
fun ListItemMotion(animateIn: Boolean, leaving: Boolean, content: @Composable () -> Unit) {
    if (LocalReduceMotion.current) {
        if (!leaving) content()
        return
    }
    val visible = remember { MutableTransitionState(!animateIn) }
    visible.targetState = !leaving
    AnimatedVisibility(
        visibleState = visible,
        enter = fadeIn(tween(220)) + expandVertically(
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntSize.VisibilityThreshold),
            expandFrom = Alignment.Top,
        ),
        exit = fadeOut(tween(140)) + shrinkVertically(tween(200), shrinkTowards = Alignment.Top),
    ) { content() }
}
