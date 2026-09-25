package com.motionlab.app.feature.timeline

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.motionlab.app.core.haptics.HapticEffect
import com.motionlab.app.core.haptics.HapticEngine
import com.motionlab.app.core.model.ActiveTimelineStep
import com.motionlab.app.core.model.HapticMarkers
import com.motionlab.app.data.TimelineHapticEntity
import com.motionlab.app.ui.design.Chip
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.motionlab.app.core.model.TimelineMath
import com.motionlab.app.core.model.TimelineSegment
import com.motionlab.app.data.ExperimentEntity
import com.motionlab.app.data.ExperimentRepository
import com.motionlab.app.data.TimelineEntity
import com.motionlab.app.data.TimelineRepository
import com.motionlab.app.data.TimelineStepEntity
import com.motionlab.app.data.nominalDurationMs
import com.motionlab.app.core.physics.ParameterMapping
import com.motionlab.app.export.SpringMath
import android.content.Intent
import androidx.compose.ui.graphics.toArgb
import com.motionlab.app.core.model.TimelineFrames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.motionlab.app.export.HapticExport
import com.motionlab.app.export.TimelineSpec
import com.motionlab.app.feature.common.icon
import com.motionlab.app.feature.common.label
import com.motionlab.app.feature.editor.LabeledSlider
import com.motionlab.app.ui.design.AppIcon
import com.motionlab.app.ui.design.AppScreen
import com.motionlab.app.ui.design.IconKind
import com.motionlab.app.ui.design.ListRow
import com.motionlab.app.ui.design.LocalTokens
import com.motionlab.app.ui.design.PrimaryButton
import com.motionlab.app.ui.design.SectionLabel
import com.motionlab.app.ui.design.Stage
import com.motionlab.app.ui.design.StageObject
import com.motionlab.app.ui.design.TextLink
import com.motionlab.app.ui.design.TopBar
import com.motionlab.app.ui.design.reveal
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * A saved sequence of experiments, played one after another. There is no
 * unified physics engine behind this -- Swipe and Fling isn't the same kind
 * of motion as Spring Drag, so nothing here pretends to render one continuous
 * scene across types. What it *does* show, honestly: pacing (the scrub bar,
 * proportioned by each step's own settle time) and, for whichever step the
 * playhead is currently inside, that step's own real step-response curve
 * ([SpringMath.stepResponse]) driving a small preview dot in that step's own
 * look. Scrubbing and Play both just move a single time value; nothing is
 * simulated beyond what's already tested in [SpringMath].
 */
@Composable
fun TimelineEditorScreen(
    timelineId: Long,
    timelineRepository: TimelineRepository,
    experimentRepository: ExperimentRepository,
    onBack: () -> Unit,
) {
    val t = LocalTokens.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    var timeline by remember { mutableStateOf<TimelineEntity?>(null) }
    LaunchedEffect(timelineId) { timeline = timelineRepository.get(timelineId) }

    val steps by timelineRepository.observeSteps(timelineId).collectAsState(initial = emptyList())
    val experiments by experimentRepository.observeAll().collectAsState(initial = emptyList())
    val byId = remember(experiments) { experiments.associateBy { it.id } }

    // Steps whose experiment vanished (deletion races with this screen being open)
    // are dropped rather than shown as a broken row -- cascade delete removes
    // them from the database moments later anyway.
    val resolved = remember(steps, byId) {
        steps.mapNotNull { s -> byId[s.experimentId]?.let { s to it } }
    }
    val segments = remember(resolved) {
        resolved.map { (s, e) -> TimelineSegment(s.gapBeforeMs, e.nominalDurationMs, e.name) }
    }
    val haptics by timelineRepository.observeHaptics(timelineId).collectAsState(initial = emptyList())
    val markerTimes = remember(haptics) { haptics.map { it.timeMs } }
    val totalMs = remember(segments, markerTimes) { HapticMarkers.totalMs(TimelineMath.totalMs(segments), markerTimes) }
    val engine = remember { HapticEngine(context) }

    val playhead = remember { Animatable(0f) }
    var playing by remember { mutableStateOf(false) }
    val active = remember(segments, playhead.value) { TimelineMath.activeStepAt(playhead.value, segments) }

    // The scrubber's own valueRange grows with totalMs; clamp the playhead so
    // shortening the sequence (removing a step) can't leave it stranded past the end.
    LaunchedEffect(totalMs) {
        if (playhead.value > totalMs) playhead.snapTo(totalMs.toFloat())
    }

    // Fire each marker the playhead passes while playing; scrubbing or restarting never replays them.
    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        var last = playhead.value
        snapshotFlow { playhead.value }.collect { now ->
            HapticMarkers.crossed(markerTimes, last, now).forEach { engine.play(haptics[it].effect) }
            last = now
        }
    }

    fun play() {
        val from = playhead.value
        val remaining = (totalMs - from).roundToInt().coerceAtLeast(1)
        playing = true
        scope.launch {
            playhead.animateTo(totalMs.toFloat(), tween(remaining, easing = LinearEasing))
            playing = false
        }
    }

    fun pause() {
        scope.launch { playhead.stop() }
        playing = false
    }

    var addingStep by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<TimelineStepEntity?>(null) }
    var copied by remember { mutableStateOf(false) }
    var hapticCopied by remember { mutableStateOf<String?>(null) }
    var videoProgress by remember { mutableStateOf<Float?>(null) }
    var videoFile by remember { mutableStateOf<java.io.File?>(null) }
    var videoMessage by remember { mutableStateOf<String?>(null) }
    var addingHaptic by remember { mutableStateOf(false) }
    var removingHaptic by remember { mutableStateOf<TimelineHapticEntity?>(null) }

    AppScreen(scrollable = true) {
        TopBar(title = timeline?.name ?: "Timeline", onBack = onBack)
        Spacer(Modifier.height(8.dp))

        Column(Modifier.reveal(0)) {
            PreviewStage(active = active, resolved = resolved, density = density)
            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                PlayButton(playing = playing, onClick = { if (playing) pause() else play() })
                Spacer(Modifier.width(12.dp))
                Text(
                    "${(playhead.value / 1000f).let { "%.2f".format(it) }}s / ${(totalMs / 1000f).let { "%.2f".format(it) }}s",
                    style = MaterialTheme.typography.bodyMedium,
                    color = t.inkSoft,
                )
            }
            Spacer(Modifier.height(8.dp))
            TimelineScrubber(
                segments = segments,
                totalMs = totalMs,
                playheadMs = playhead.value,
                markersMs = markerTimes,
                onScrub = { ms ->
                    if (playing) pause()
                    scope.launch { playhead.snapTo(ms) }
                },
            )
            Spacer(Modifier.height(40.dp))
        }

        Column(Modifier.reveal(1)) {
            SectionLabel("Steps", trailing = if (resolved.isEmpty()) null else "${resolved.size}")
            if (resolved.isEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("Nothing here yet -- add a step below.", style = MaterialTheme.typography.bodyMedium, color = t.inkSoft)
            }
            resolved.forEachIndexed { i, (step, experiment) ->
                TimelineStepRow(
                    icon = experiment.type.icon,
                    title = experiment.name,
                    durationMs = experiment.nominalDurationMs,
                    gapMs = step.gapBeforeMs,
                    canMoveUp = i > 0,
                    canMoveDown = i < resolved.size - 1,
                    active = active?.index == i,
                    onMoveUp = { scope.launch { timelineRepository.moveStep(step, -1) } },
                    onMoveDown = { scope.launch { timelineRepository.moveStep(step, 1) } },
                    onGapDecrease = { scope.launch { timelineRepository.setGap(step, step.gapBeforeMs - 100) } },
                    onGapIncrease = { scope.launch { timelineRepository.setGap(step, step.gapBeforeMs + 100) } },
                    onLongClick = { removing = step },
                )
            }
            Spacer(Modifier.height(14.dp))
            PrimaryButton("Add step", onClick = { addingStep = true }, icon = IconKind.PLUS)
        }

        Spacer(Modifier.height(40.dp))

        Column(Modifier.reveal(2)) {
            SectionLabel("Haptics", trailing = if (haptics.isEmpty()) null else "${haptics.size}")
            if (haptics.isEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("Pulses at a moment in time, independent of the motion.", style = MaterialTheme.typography.bodyMedium, color = t.inkSoft)
            }
            haptics.forEach { h ->
                HapticMarkerRow(
                    marker = h,
                    onEarlier = { scope.launch { timelineRepository.setHapticTime(h, h.timeMs - 100) } },
                    onLater = { scope.launch { timelineRepository.setHapticTime(h, h.timeMs + 100) } },
                    onFeel = { engine.play(h.effect) },
                    onLongClick = { removingHaptic = h },
                )
            }
            Spacer(Modifier.height(14.dp))
            PrimaryButton("Add haptic", onClick = { addingHaptic = true }, icon = IconKind.PLUS)
        }

        Spacer(Modifier.height(40.dp))

        Column(Modifier.reveal(3)) {
            SectionLabel("Export")
            Spacer(Modifier.height(10.dp))
            ListRow(
                title = if (copied) "Copied" else "Copy sequence spec",
                subtitle = "JSON: each step's name, type, gap and timing",
                onClick = {
                    val spec = TimelineSpec.json(
                        name = timeline?.name ?: "Timeline",
                        steps = resolved.map { (s, e) -> TimelineSpec.Step(e.name, e.type.name.lowercase(), s.gapBeforeMs, e.nominalDurationMs) },
                        haptics = haptics.map { TimelineSpec.Haptic(it.timeMs, it.effect.name.lowercase()) },
                    )
                    clipboard.setText(AnnotatedString(spec))
                    copied = true
                },
                trailing = { AppIcon(IconKind.COPY, tint = t.inkSoft, size = 18.dp) },
            )
            ListRow(
                title = videoProgress?.let { "Rendering video ${(it * 100).roundToInt()}%" } ?: "Export video",
                subtitle = videoMessage ?: "A portrait MP4 with the haptic ticks shown, ready to share",
                enabled = resolved.isNotEmpty() && videoProgress == null,
                onClick = {
                    videoProgress = 0f
                    videoMessage = null
                    val job = VideoJob(
                        name = timeline?.name ?: "Timeline",
                        steps = resolved.map { (_, e) ->
                            VideoStep(e.type.label, TimelineFrames.StepSpring(ParameterMapping.stiffness(e.stiffnessT), ParameterMapping.dampingRatio(e.dampingT)), e.objectStyle)
                        },
                        segments = segments,
                        markers = haptics.map { it.timeMs to it.effect.name.lowercase() },
                        colors = VideoColors(t.canvas.toArgb(), t.surface.toArgb(), t.line.toArgb(), t.ink.toArgb(), t.inkSoft.toArgb(), t.inkFaint.toArgb(), t.accent.toArgb()),
                        typeface = androidx.core.content.res.ResourcesCompat.getFont(context, com.motionlab.app.R.font.manrope) ?: android.graphics.Typeface.SANS_SERIF,
                    )
                    scope.launch {
                        val file = java.io.File(context.cacheDir, "videos/${job.name.replace(Regex("[^A-Za-z0-9]+"), "-").trim('-').ifEmpty { "timeline" }}.mp4")
                        val ok = withContext(Dispatchers.Default) { TimelineVideo.render(job, file) { p -> videoProgress = p } }
                        videoProgress = null
                        if (!ok) { videoMessage = "Couldn't render the video on this device."; return@launch }
                        videoFile = file
                        videoMessage = "Rendered ${"%.1f".format(TimelineFrames.totalMs(segments, markerTimes) / 1000f)} s."
                        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "video/mp4"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }, "Share video"))
                    }
                },
                trailing = { AppIcon(IconKind.SHARE, tint = t.inkSoft, size = 18.dp) },
            )
            if (haptics.isNotEmpty()) {
                val hs = haptics.map { TimelineSpec.Haptic(it.timeMs, it.effect.name.lowercase()) }
                val nm = timeline?.name ?: "Timeline"
                @Composable
                fun hapticRow(id: String, title: String, subtitle: String, text: () -> String) = ListRow(
                    title = if (hapticCopied == id) "Copied" else title,
                    subtitle = subtitle,
                    onClick = { clipboard.setText(AnnotatedString(text())); hapticCopied = id },
                    trailing = { AppIcon(IconKind.COPY, tint = t.inkSoft, size = 18.dp) },
                )
                hapticRow("android", "Copy haptics for Android", "Just the buzz pattern: a VibrationEffect waveform") { HapticExport.android(nm, hs) }
                hapticRow("ios", "Copy haptics for iOS", "Just the buzz pattern: CoreHaptics Swift") { HapticExport.coreHaptics(nm, hs) }
                hapticRow("ahap", "Copy haptics as .ahap", "An Apple Haptic pattern file") { HapticExport.ahap(nm, hs) }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (addingStep) {
        AddStepDialog(
            experiments = experiments,
            onPick = { e ->
                scope.launch { timelineRepository.addStep(timelineId, e.id) }
                addingStep = false
            },
            onDismiss = { addingStep = false },
        )
    }

    if (addingHaptic) {
        AddHapticDialog(
            atMs = playhead.value.roundToInt(),
            onFeel = { engine.play(it) },
            onPick = { effect ->
                scope.launch { timelineRepository.addHaptic(timelineId, playhead.value.roundToInt(), effect) }
                addingHaptic = false
            },
            onDismiss = { addingHaptic = false },
        )
    }

    removingHaptic?.let { h ->
        AlertDialog(
            onDismissRequest = { removingHaptic = null },
            containerColor = t.surface,
            titleContentColor = t.ink,
            textContentColor = t.ink,
            shape = RoundedCornerShape(t.radiusCard),
            title = { Text("Remove haptic", style = MaterialTheme.typography.titleLarge) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { timelineRepository.removeHaptic(h) }
                    removingHaptic = null
                }) { Text("Remove", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge) }
            },
            dismissButton = {
                TextButton(onClick = { removingHaptic = null }) { Text("Cancel", color = t.inkSoft, style = MaterialTheme.typography.labelLarge) }
            },
        )
    }

    removing?.let { step ->
        AlertDialog(
            onDismissRequest = { removing = null },
            containerColor = t.surface,
            titleContentColor = t.ink,
            textContentColor = t.ink,
            shape = RoundedCornerShape(t.radiusCard),
            title = { Text("Remove step", style = MaterialTheme.typography.titleLarge) },
            text = { Text("This only removes it from the timeline; the experiment itself is untouched.", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { timelineRepository.removeStep(step) }
                    removing = null
                }) { Text("Remove", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge) }
            },
            dismissButton = {
                TextButton(onClick = { removing = null }) { Text("Cancel", color = t.inkSoft, style = MaterialTheme.typography.labelLarge) }
            },
        )
    }
}

@Composable
private fun PlayButton(playing: Boolean, onClick: () -> Unit) {
    PrimaryButton(if (playing) "Pause" else "Play", onClick = onClick, icon = if (playing) IconKind.PAUSE else IconKind.PLAY)
}

/** The active step's own object, moving through its own real step-response curve. At rest between steps, it just sits centred. */
@Composable
private fun PreviewStage(
    active: ActiveTimelineStep?,
    resolved: List<Pair<TimelineStepEntity, ExperimentEntity>>,
    density: androidx.compose.ui.unit.Density,
) {
    Stage {
        val experiment = active?.let { resolved.getOrNull(it.index)?.second }
        if (experiment != null) {
            val k = ParameterMapping.stiffness(experiment.stiffnessT)
            val z = ParameterMapping.dampingRatio(experiment.dampingT)
            val durationSec = experiment.nominalDurationMs / 1000.0
            val progress = SpringMath.stepResponse(active!!.progress * durationSec, k.toDouble(), z.toDouble()).toFloat()
            val travelPx = with(density) { 90.dp.toPx() }
            val offsetX = (progress * 2f - 1f) * travelPx // sweeps from -travel to +travel as it settles centre
            StageObject(
                style = experiment.objectStyle,
                modifier = Modifier.offset { IntOffset(offsetX.roundToInt(), 0) },
            )
        } else {
            StageObject(style = com.motionlab.app.core.model.ObjectStyle())
        }
    }
}

@Composable
private fun HapticMarkerRow(
    marker: TimelineHapticEntity,
    onEarlier: () -> Unit,
    onLater: () -> Unit,
    onFeel: () -> Unit,
    onLongClick: () -> Unit,
) {
    val t = LocalTokens.current
    ListRow(
        title = marker.effect.name.lowercase().replaceFirstChar { it.uppercase() },
        subtitle = "at ${"%.2f".format(marker.timeMs / 1000f)}s",
        leading = IconKind.TIMELINE,
        onClick = onFeel,
        onLongClick = onLongClick,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("-", color = t.ink, modifier = Modifier.testTag("hapticEarlier_${marker.id}").clickable(onClick = onEarlier).padding(horizontal = 12.dp, vertical = 8.dp))
                Text("+", color = t.ink, modifier = Modifier.testTag("hapticLater_${marker.id}").clickable(onClick = onLater).padding(horizontal = 12.dp, vertical = 8.dp))
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddHapticDialog(
    atMs: Int,
    onFeel: (HapticEffect) -> Unit,
    onPick: (HapticEffect) -> Unit,
    onDismiss: () -> Unit,
) {
    val t = LocalTokens.current
    var chosen by remember { mutableStateOf(HapticEffect.CLICK) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = t.surface,
        titleContentColor = t.ink,
        textContentColor = t.ink,
        shape = RoundedCornerShape(t.radiusCard),
        title = { Text("Add haptic", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                Text("At the playhead, ${"%.2f".format(atMs / 1000f)}s. Tap an effect to feel it.", style = MaterialTheme.typography.bodyMedium, color = t.inkSoft)
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HapticEffect.entries.forEach { e ->
                        Chip(e.name.lowercase().replaceFirstChar { it.uppercase() }, selected = e == chosen, onClick = {
                            chosen = e
                            onFeel(e)
                        })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(chosen) }) { Text("Add", color = t.ink, style = MaterialTheme.typography.labelLarge) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = t.inkSoft, style = MaterialTheme.typography.labelLarge) } },
    )
}
