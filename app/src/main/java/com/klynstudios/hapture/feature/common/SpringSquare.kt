package com.klynstudios.hapture.feature.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.klynstudios.hapture.core.haptics.HapticEffect
import com.klynstudios.hapture.core.haptics.HapticEngine
import com.klynstudios.hapture.core.physics.ParameterMapping
import com.klynstudios.hapture.core.physics.RubberBand
import com.klynstudios.hapture.ui.design.LocalTokens
import com.klynstudios.hapture.ui.design.Stage
import kotlinx.coroutines.launch

/**
 * A square on the stage you can drag in any direction. It moves freely up to a margin from the stage's edge,
 * rubber-bands beyond it, and on release springs home with the given slider positions, carrying the flick's
 * velocity. Used by the Home demo and the intro; the Spring drag editor has the full version.
 */
@Composable
fun SpringSquare(
    stiffnessT: Float,
    dampingT: Float,
    haptics: HapticEngine,
    modifier: Modifier = Modifier,
    height: Dp = 170.dp,
    testTag: String = "springSquare",
) {
    val t = LocalTokens.current
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    Stage(modifier, height = height) {
        Box(
            Modifier
                .fillMaxSize()
                .testTag(testTag)
                .pointerInput(stiffnessT, dampingT) {
                    // Free travel stops short of the edges so the square never leaves the stage; past that it resists.
                    val square = 56.dp.toPx()
                    val boundX = (size.width - square) / 2f - 16.dp.toPx()
                    val boundY = (size.height - square) / 2f - 26.dp.toPx()
                    val k = 0.05f
                    var raw = Offset.Zero
                    var tracker = VelocityTracker()
                    detectDragGestures(
                        onDragStart = {
                            scope.launch { offset.stop() }
                            raw = offset.value
                            tracker = VelocityTracker()
                            haptics.play(HapticEffect.SOFT)
                        },
                        onDrag = { change, delta ->
                            change.consume()
                            raw += delta
                            val visual = Offset(RubberBand.apply(raw.x, boundX, k), RubberBand.apply(raw.y, boundY, k))
                            tracker.addPosition(change.uptimeMillis, visual)
                            scope.launch { offset.snapTo(visual) }
                        },
                        onDragEnd = {
                            val v = tracker.calculateVelocity()
                            scope.launch {
                                offset.animateTo(
                                    Offset.Zero,
                                    spring(
                                        dampingRatio = ParameterMapping.dampingRatio(dampingT),
                                        stiffness = ParameterMapping.stiffness(stiffnessT),
                                        visibilityThreshold = Offset(0.5f, 0.5f),
                                    ),
                                    initialVelocity = Offset(v.x, v.y),
                                )
                                haptics.play(HapticEffect.TICK)
                            }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(56.dp)
                    .graphicsLayer { translationX = offset.value.x; translationY = offset.value.y }
                    .background(t.ink, RoundedCornerShape(16.dp)),
            )
        }
    }
}
