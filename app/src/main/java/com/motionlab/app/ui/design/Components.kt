package com.motionlab.app.ui.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motionlab.app.core.model.BackdropCrop
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.FilterQuality
import com.motionlab.app.core.model.ObjectFill
import com.motionlab.app.core.model.ObjectShape
import com.motionlab.app.core.model.ObjectStyle
import com.motionlab.app.ui.theme.MonoSmall
import kotlinx.coroutines.delay

/**
 * Quiet entrance: fades up 12dp over 600ms on an ease-out curve, staggered by
 * [index]. Runs once when the block first appears; opacity and translation
 * only, so it never triggers layout.
 */
fun Modifier.reveal(index: Int = 0): Modifier = composed {
    val reduce = LocalReduceMotion.current
    val progress = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (reduce) return@LaunchedEffect
        delay(index * 70L)
        progress.animateTo(1f, tween(600, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)))
    }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 12.dp.toPx()
    }
}

/** The widest a single column of content gets, so lines stay readable on large screens. */
val MAX_CONTENT_WIDTH = 680.dp

/** Page: warm canvas with a faint light spot at the top, inside the system bars. */
@Composable
fun AppScreen(scrollable: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val t = LocalTokens.current
    CompositionLocalProvider(LocalContentColor provides t.ink) {
        // On a tablet or an unfolded foldable the page keeps a readable width, centred, instead of stretching.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .canvasBackground(t)
                .systemBarsPadding()
                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = MAX_CONTENT_WIDTH)
                    .fillMaxWidth()
                    .then(if (scrollable) Modifier else Modifier.weight(1f))
                    .padding(horizontal = 24.dp),
                content = content,
            )
        }
    }
}

/** The page background every screen shares: the canvas colour with a faint light spot at the top right. */
fun Modifier.canvasBackground(t: DesignTokens): Modifier = this
    .background(t.canvas)
    .drawBehind {
        drawRect(
            Brush.radialGradient(
                colors = listOf(t.ink.copy(alpha = if (t.dark) 0.05f else 0.035f), Color.Transparent),
                center = Offset(size.width * 0.85f, 0f),
                radius = size.width * 0.9f,
            )
        )
    }

@Composable
fun TopBar(title: String, onBack: (() -> Unit)? = null) {
    val t = LocalTokens.current
    Row(
        modifier = Modifier.fillMaxWidth().height(60.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Box(
                modifier = Modifier
                    .testTag("backButton")
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { AppIcon(IconKind.BACK, tint = t.ink) }
            Spacer(Modifier.width(8.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium, color = t.ink)
    }
}

/** Small tracked caps over a hairline, optionally with a mono readout at the right. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: String? = null) {
    val t = LocalTokens.current
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = t.inkSoft)
            if (trailing != null) Text(trailing, style = MonoSmall, color = t.inkSoft)
        }
        Hairline()
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    Box(modifier.fillMaxWidth().height(t.hairline).background(t.line))
}

/** Solid charcoal, 8dp corners, no shadow. Presses in a hair rather than clicking. */
@Composable
fun PrimaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: IconKind? = null) {
    val t = LocalTokens.current
    Row(
        modifier = modifier
            .pressable(onClick, scaleTo = 0.98f)
            .clip(RoundedCornerShape(t.radiusControl))
            .background(t.ink)
            .padding(horizontal = 20.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            AppIcon(icon, tint = t.canvas, size = 18.dp)
            Spacer(Modifier.width(10.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = t.canvas)
    }
}

/** Understated text action with a trailing arrow, like a link in a paragraph. */
@Composable
fun TextLink(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: IconKind = IconKind.FORWARD) {
    val t = LocalTokens.current
    Row(
        modifier = modifier
            .pressable(onClick, scaleTo = 0.97f)
            .clip(RoundedCornerShape(6.dp))
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = t.inkSoft)
        Spacer(Modifier.width(8.dp))
        AppIcon(icon, tint = t.inkSoft, size = 16.dp)
    }
}

@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    val fill by animateColorAsState(if (selected) t.ink else Color.Transparent, tween(180), label = "chipFill")
    val text by animateColorAsState(if (selected) t.canvas else t.ink, tween(180), label = "chipText")
    val border by animateColorAsState(if (selected) t.ink else t.line, tween(180), label = "chipBorder")
    val shape = RoundedCornerShape(t.radiusControl)
    Box(
        modifier = modifier
            .pressable(onClick, scaleTo = 0.96f)
            .clip(shape)
            .background(fill)
            .border(t.hairline, border, shape)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = MaterialTheme.typography.labelMedium, color = text) }
}

/** Tiny uppercase status pill: the one place a pill shape is allowed. */
@Composable
fun Tag(text: String) {
    val t = LocalTokens.current
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
        color = t.inkSoft,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(t.line.copy(alpha = 0.6f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}


/**
 * A list row with no box: a hairline underneath, a soft wash while pressed.
 * [leading] is an icon tile; [trailing] is any small content.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: IconKind? = null,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit = {},
) {
    val t = LocalTokens.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val wash by animateColorAsState(if (pressed) t.ink.copy(alpha = 0.05f) else Color.Transparent, tween(120), label = "rowWash")
    val titleColor = if (enabled) t.ink else t.inkFaint

    Column(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(wash)
                .then(
                    if (enabled) Modifier.combinedClickable(
                        interactionSource = source, indication = null, onClick = onClick, onLongClick = onLongClick,
                    ) else Modifier
                )
                .padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                val tile = RoundedCornerShape(10.dp)
                Box(
                    Modifier
                        .size(40.dp)
                        .background(t.surface, tile)
                        .border(t.hairline, t.line, tile),
                    contentAlignment = Alignment.Center,
                ) { AppIcon(leading, tint = titleColor, size = 20.dp) }
                Spacer(Modifier.width(16.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = titleColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = if (enabled) t.inkSoft else t.inkFaint)
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(12.dp))
                trailing()
            }
        }
        Hairline()
    }
}

/** The pad the interactive object lives on: a white card, hairline border, a faint dot grid. */
@Composable
fun Stage(
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 200.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val t = LocalTokens.current
    val backdrop = LocalBackdrop.current
    val shape = RoundedCornerShape(t.radiusCard)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(t.surface)
            .drawBehind {
                if (backdrop != null) {
                    // The person's own screenshot, so the object is judged against their real UI, not a blank pad.
                    val w = BackdropCrop.window(backdrop.image.width, backdrop.image.height, size.width.toInt(), size.height.toInt(), backdrop.focusY)
                    drawImage(
                        backdrop.image, srcOffset = IntOffset(w.x, w.y), srcSize = IntSize(w.width, w.height),
                        dstSize = IntSize(size.width.toInt(), size.height.toInt()), filterQuality = FilterQuality.Medium,
                    )
                    return@drawBehind
                }
                val step = 18.dp.toPx()
                val dots = ArrayList<Offset>()
                var y = step / 2f
                while (y < size.height) {
                    var x = step / 2f
                    while (x < size.width) { dots += Offset(x, y); x += step }
                    y += step
                }
                drawPoints(dots, PointMode.Points, t.line, 2.2f, StrokeCap.Round)
            }
            .border(t.hairline, t.line, shape),
        contentAlignment = Alignment.Center,
    ) {
        content()
        LocalBackdropAction.current?.let { open ->
            Box(Modifier.align(Alignment.TopEnd).padding(10.dp)) {
                Chip("Screenshot", selected = LocalBackdropActive.current, onClick = open, modifier = Modifier.testTag("backdropChip"))
            }
        }
    }
}

/** Fill and border for an object, by role, so it follows the theme. */
fun objectColors(fill: ObjectFill, t: DesignTokens): Pair<Color, Color> = when (fill) {
    ObjectFill.INK -> t.ink to Color.Transparent
    ObjectFill.MARKER -> t.accent to t.ink.copy(alpha = 0.18f)
    ObjectFill.PAPER -> t.surface to t.ink
}

/** The draggable thing. Callers add their own offset / gesture modifiers first. */
@Composable
fun StageObject(style: ObjectStyle, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    val h = style.sizeDp
    // CUSTOM is always drawn in square bounds -- the drawing itself carries whatever proportions it has.
    val aspect = if (style.shape == ObjectShape.CUSTOM) 1f else style.shape.aspect
    val shape = style.toComposeShape()
    val (fill, border) = objectColors(style.fill, t)
    Box(
        modifier
            .size((h * aspect).dp, h.dp)
            .background(fill, shape)
            .border(if (style.fill == ObjectFill.PAPER) 1.5.dp else t.hairline, border, shape)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ValueSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)? = null,
    /** One plain-language line under the label, for a control whose name a newcomer wouldn't know. */
    hint: String? = null,
    /** What the readout shows instead of the 0..100 position, when the slider stands for a real quantity. */
    display: String? = null,
) {
    val t = LocalTokens.current
    Column(Modifier.padding(vertical = 2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = t.ink)
            Text(display ?: "${(value * 100).toInt()}", style = MonoSmall, color = t.inkSoft)
        }
        if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = t.inkSoft)
        Slider(
            modifier = Modifier.testTag(sliderTestTag(label)),
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            thumb = {
                Box(
                    Modifier
                        .size(18.dp)
                        .background(t.ink, CircleShape)
                        .border(3.dp, t.canvas, CircleShape)
                )
            },
            track = { state ->
                val span = state.valueRange.endInclusive - state.valueRange.start
                val fraction = ((state.value - state.valueRange.start) / span).coerceIn(0f, 1f)
                Canvas(Modifier.fillMaxWidth().height(24.dp)) {
                    val y = size.height / 2f
                    val sw = 2.dp.toPx()
                    drawLine(t.line, Offset(0f, y), Offset(size.width, y), sw, StrokeCap.Round)
                    drawLine(t.ink, Offset(0f, y), Offset(size.width * fraction, y), sw, StrokeCap.Round)
                }
            },
        )
    }
}

/** Dashed guide used by the curve. */
internal val DashedGuide = PathEffect.dashPathEffect(floatArrayOf(6f, 8f))

/** The tag a [ValueSlider] with this label carries, for tests to find it by. */
fun sliderTestTag(label: String): String = "slider_" + label.lowercase().filter { it.isLetterOrDigit() }
