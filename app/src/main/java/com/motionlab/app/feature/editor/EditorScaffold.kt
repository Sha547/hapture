package com.motionlab.app.feature.editor

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.motionlab.app.core.model.MaterialMatch
import com.motionlab.app.core.model.MaterialMotion
import com.motionlab.app.core.spec.SpringSpec
import com.motionlab.app.ui.design.Hairline
import com.motionlab.app.ui.design.LocalTokens
import com.motionlab.app.ui.design.TopBar
import com.motionlab.app.ui.design.canvasBackground
import com.motionlab.app.ui.theme.MonoSmall
import kotlin.math.roundToInt

/** Wide enough for the stage and the controls to sit side by side: tablets, unfolded foldables, landscape. */
private val TWO_PANE_MIN_WIDTH = 720.dp

/** Tall enough to keep the stage on screen while the controls scroll underneath it. */
private val PINNED_STAGE_MIN_HEIGHT = 620.dp

/**
 * The frame every interaction editor shares. The point of an editor is to move a
 * slider and try the result straight away, so the stage never scrolls out of reach:
 *
 * - Phone: the stage and a one-line readout stay pinned under the top bar, and
 *   only the controls scroll. A hairline appears under the stage once they do.
 * - Wide (tablet, unfolded foldable, landscape): stage on the left, controls in
 *   their own scrolling column on the right.
 * - Too short to pin (a phone in landscape with the wide layout not fitting):
 *   everything scrolls together, as before.
 *
 * [stage] is the interactive area and its hint; [controls] are the editor's sections.
 */
@Composable
internal fun EditorScaffold(
    title: String,
    onBack: () -> Unit,
    spring: SpringSpec,
    stage: @Composable ColumnScope.() -> Unit,
    controls: @Composable ColumnScope.() -> Unit,
) {
    val t = LocalTokens.current
    CompositionLocalProvider(LocalContentColor provides t.ink) {
        BoxWithConstraints(Modifier.fillMaxSize().canvasBackground(t).systemBarsPadding()) {
            val wide = maxWidth >= TWO_PANE_MIN_WIDTH
            val pinned = !wide && maxHeight >= PINNED_STAGE_MIN_HEIGHT
            when {
                wide -> Row(Modifier.fillMaxSize().testTag("editorTwoPane")) {
                    Column(
                        Modifier
                            .weight(0.46f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(start = 32.dp, end = 28.dp),
                    ) {
                        TopBar(title = title, onBack = onBack)
                        Spacer(Modifier.height(8.dp))
                        stage()
                        Spacer(Modifier.height(12.dp))
                        SpringReadout(spring)
                        Spacer(Modifier.height(24.dp))
                    }
                    Box(Modifier.fillMaxHeight().width(t.hairline).background(t.line))
                    Column(
                        Modifier
                            .weight(0.54f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 32.dp),
                    ) {
                        Spacer(Modifier.height(68.dp))
                        Column(Modifier.widthIn(max = 640.dp)) { controls() }
                    }
                }

                pinned -> Column(Modifier.fillMaxSize()) {
                    val scroll = rememberScrollState()
                    Column(Modifier.padding(horizontal = 24.dp)) {
                        TopBar(title = title, onBack = onBack)
                        Spacer(Modifier.height(4.dp))
                        stage()
                        Spacer(Modifier.height(10.dp))
                        SpringReadout(spring)
                        Spacer(Modifier.height(14.dp))
                    }
                    // Only once something has scrolled under it, so the resting screen stays unlined.
                    val lineAlpha by animateFloatAsState(if (scroll.value > 0) 1f else 0f, tween(160), label = "pinLine")
                    Hairline(Modifier.alpha(lineAlpha))
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .testTag("editorControls")
                            .verticalScroll(scroll)
                            .padding(horizontal = 24.dp),
                    ) {
                        Spacer(Modifier.height(24.dp))
                        controls()
                    }
                }

                else -> Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp),
                ) {
                    TopBar(title = title, onBack = onBack)
                    Spacer(Modifier.height(8.dp))
                    stage()
                    Spacer(Modifier.height(10.dp))
                    SpringReadout(spring)
                    Spacer(Modifier.height(32.dp))
                    controls()
                }
            }
        }
    }
}

/**
 * One line that answers "what did that slider just do?" without scrolling to the curve:
 * settle time, overshoot, and how this spring sits against Material 3's own.
 */
@Composable
internal fun SpringReadout(spring: SpringSpec, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    val settle = remember(spring) { spring.settleMs }
    val match = remember(spring) { MaterialMotion.nearest(spring) }
    Row(
        modifier.fillMaxWidth().testTag("springReadout"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("$settle ms", style = MonoSmall, color = t.ink)
        Text("${spring.overshootPercent.roundToInt()}% over", style = MonoSmall, color = t.inkSoft)
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val onScale = match.closeness == MaterialMatch.Closeness.EXACT || match.closeness == MaterialMatch.Closeness.CLOSE
            Box(Modifier.size(6.dp).background(if (onScale) t.ink else t.inkFaint, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(
                match.summary,
                style = MaterialTheme.typography.bodySmall,
                color = if (onScale) t.ink else t.inkSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("materialMatch"),
            )
        }
    }
}
