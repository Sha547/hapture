package com.motionlab.app.feature.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.motionlab.app.core.haptics.HapticEngine
import com.motionlab.app.core.haptics.HapticPreset
import com.motionlab.app.core.haptics.HapticSupport
import com.motionlab.app.ui.design.Chip
import com.motionlab.app.ui.design.LocalTokens

/**
 * How the interaction feels in the hand. Picking a preset plays it once, so
 * you can compare Soft and Firm by touch. Saved with the experiment and
 * included in the design spec. Under the description, one line says whether
 * this phone can actually play it -- cheaper motors turn Soft and Firm into
 * the same buzz, and nobody should tune by a feel their phone can't make.
 */
@Composable
internal fun HapticControls(preset: HapticPreset, onSelect: (HapticPreset) -> Unit) {
    val t = LocalTokens.current
    val context = LocalContext.current
    val engine = remember { HapticEngine(context) }
    val support = remember(preset) { engine.support(preset) }
    Column {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(HapticPreset.entries) { p ->
                Chip(p.label, selected = p == preset, onClick = { onSelect(p) })
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(preset.description, style = MaterialTheme.typography.bodySmall, color = t.inkSoft)
        if (support != null) {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.testTag("hapticSupport"), verticalAlignment = Alignment.Top) {
                val dot = when (support) {
                    HapticSupport.EXACT -> t.ink
                    HapticSupport.UNKNOWN -> t.inkSoft
                    HapticSupport.APPROXIMATE, HapticSupport.NONE -> t.inkFaint
                }
                Box(Modifier.padding(top = 6.dp).size(6.dp).background(dot, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(support.note, style = MaterialTheme.typography.bodySmall, color = t.inkSoft)
            }
        }
    }
}
