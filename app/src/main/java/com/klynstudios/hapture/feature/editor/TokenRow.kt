package com.klynstudios.hapture.feature.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.klynstudios.hapture.core.physics.ParameterMapping
import com.klynstudios.hapture.feature.common.LocalMotionTokens
import com.klynstudios.hapture.ui.design.Chip

/** Saved motion tokens as chips under the built-in presets. Renders nothing until one is saved. */
@Composable
internal fun TokenRow(onApply: (stiffnessT: Float, dampingT: Float) -> Unit) {
    val tokens = LocalMotionTokens.current
    if (tokens.isEmpty()) return
    Spacer(Modifier.height(10.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(tokens) { tk ->
            Chip(label = tk.name, selected = false, onClick = {
                onApply(ParameterMapping.stiffnessT(tk.stiffness), ParameterMapping.dampingT(tk.dampingRatio))
            })
        }
    }
}
