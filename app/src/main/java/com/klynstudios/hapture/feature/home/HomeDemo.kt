package com.klynstudios.hapture.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.klynstudios.hapture.core.haptics.HapticEffect
import com.klynstudios.hapture.core.haptics.HapticEngine
import com.klynstudios.hapture.core.model.MotionPreset
import com.klynstudios.hapture.core.physics.ParameterMapping
import com.klynstudios.hapture.ui.design.Chip
import com.klynstudios.hapture.ui.design.LocalTokens
import com.klynstudios.hapture.ui.design.Stage
import kotlinx.coroutines.launch

/**
 * What the app is, shown instead of explained: drag the square, let go, feel it spring back. The three
 * chips change how it moves, so the difference between a soft and a bouncy feel is felt in a few seconds.
 * It's the same spring the editors tune and export.
 */
@Composable
fun HomeDemo() {
    val t = LocalTokens.current
    val context = LocalContext.current
    val haptics = remember { HapticEngine(context) }
    val scope = rememberCoroutineScope()
    val x = remember { Animatable(0f) }
    var preset by remember { mutableStateOf(MotionPreset.SNAPPY) }
    val demoPresets = listOf(MotionPreset.GENTLE, MotionPreset.SNAPPY, MotionPreset.BOUNCY)

    Stage(height = 150.dp) {
        Box(
            Modifier
                .fillMaxSize()
                .testTag("homeDemo")
                .pointerInput(preset) {
                    val reach = size.width * 0.36f
                    detectDragGestures(
                        onDragStart = { scope.launch { x.stop() } },
                        onDrag = { change, delta ->
                            change.consume()
                            scope.launch { x.snapTo((x.value + delta.x).coerceIn(-reach, reach)) }
                        },
                        onDragEnd = {
                            scope.launch {
                                x.animateTo(
                                    0f,
                                    spring(
                                        dampingRatio = ParameterMapping.dampingRatio(preset.dampingT),
                                        stiffness = ParameterMapping.stiffness(preset.stiffnessT),
                                        visibilityThreshold = 0.5f,
                                    ),
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
                    .graphicsLayer { translationX = x.value }
                    .background(t.ink, RoundedCornerShape(16.dp)),
            )
        }
    }
    Spacer(Modifier.height(10.dp))
    Text("Drag the square and let go. The buttons change how it moves.", style = MaterialTheme.typography.bodySmall, color = t.inkSoft)
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        demoPresets.forEach { p -> Chip(p.displayName, selected = preset == p, onClick = { preset = p }) }
    }
}
