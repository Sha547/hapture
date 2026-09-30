package com.klynstudios.hapture.feature.capture

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.klynstudios.hapture.core.capture.MultiTrack
import com.klynstudios.hapture.core.capture.ObjectTracker
import com.klynstudios.hapture.core.capture.SpringFit
import com.klynstudios.hapture.core.spec.MotionLint
import com.klynstudios.hapture.core.spec.MotionSpec
import com.klynstudios.hapture.core.spec.MotionTransition
import com.klynstudios.hapture.core.spec.SpringSpec
import com.klynstudios.hapture.export.SpringMath
import com.klynstudios.hapture.feature.common.LocalSaveMotionToken
import com.klynstudios.hapture.ui.design.AppScreen
import com.klynstudios.hapture.ui.design.Chip
import com.klynstudios.hapture.ui.design.DashedGuide
import com.klynstudios.hapture.ui.design.LocalTokens
import com.klynstudios.hapture.ui.design.PrimaryButton
import com.klynstudios.hapture.ui.design.SectionLabel
import com.klynstudios.hapture.ui.design.TextLink
import com.klynstudios.hapture.ui.design.TopBar
import com.klynstudios.hapture.ui.design.ValueSlider
import com.klynstudios.hapture.ui.design.IconKind
import com.klynstudios.hapture.ui.design.reveal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val BASE_Y = 0.82f
private const val TARGET_Y = 0.32f

/**
 * Turn a feel you can show into a spring you can use: draw the motion curve
 * you have in mind, or point at a screen recording of an animation you like.
 * Both end in the same least-squares fit ([SpringFit]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CaptureScreen(
    onBack: () -> Unit,
    onCreate: (stiffness: Float, dampingRatio: Float) -> Unit,
    onCreateStagger: (stiffness: Float, dampingRatio: Float, staggerMs: Float) -> Unit = { _, _, _ -> },
) {
    val t = LocalTokens.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val saveToken = LocalSaveMotionToken.current

    var mode by remember { mutableStateOf("draw") }
    var durationT by remember { mutableFloatStateOf(0.35f) }
    val durationSec = 0.4f + 1.6f * durationT
    var drawn by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var result by remember { mutableStateOf<SpringFit.Result?>(null) }
    var resultSpan by remember { mutableFloatStateOf(1f) }
    var measured by remember { mutableStateOf<List<Pair<Float, Float>>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var tokenName by remember { mutableStateOf("") }

    // Video state
    var uri by remember { mutableStateOf<Uri?>(null) }
    var videoLen by remember { mutableFloatStateOf(0f) }
    var startT by remember { mutableFloatStateOf(0f) }
    var frame by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var pickedColor by remember { mutableStateOf<Int?>(null) }
    var twoObjects by remember { mutableStateOf(false) }
    var slot by remember { mutableIntStateOf(0) }
    var pickedColor2 by remember { mutableStateOf<Int?>(null) }
    var pair by remember { mutableStateOf<MultiTrack.Analysis?>(null) }
    var progress by remember { mutableStateOf<Float?>(null) }

    fun startSec() = (startT * (videoLen - 1f).coerceAtLeast(0f))

    fun fitDrawn(points: List<Offset>) {
        // Keep the curve a function of time: only points that move forward in x.
        val mono = ArrayList<Offset>()
        for (p in points) if (mono.isEmpty() || p.x > mono.last().x) mono += p
        val samples = mono.map { it.x * durationSec to (BASE_Y - it.y) / (BASE_Y - TARGET_Y) }
        val fit = SpringFit.fit(samples)
        result = fit
        resultSpan = durationSec
        measured = samples
        message = if (fit == null) "Draw a longer curve, left to right." else null
    }

    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { picked ->
        if (picked != null) scope.launch {
            uri = picked
            result = null
            pickedColor = null
            message = null
            withContext(Dispatchers.IO) {
                videoLen = VideoFrames.durationSec(context, picked)
                startT = 0f
                frame = VideoFrames.frameAt(context, picked, 0f)
            }
        }
    }

    AppScreen(scrollable = true) {
        TopBar(title = "Capture a feel", onBack = onBack)
        Spacer(Modifier.height(8.dp))

        Column(Modifier.reveal(0)) {
            Text(
                "Get a spring from something you can show: a curve you draw, a motion you sketch on a screen, or a recording of an animation you like.",
                style = MaterialTheme.typography.bodyMedium, color = t.inkSoft,
            )
            Spacer(Modifier.height(16.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Draw a curve", selected = mode == "draw", onClick = { mode = "draw"; result = null; message = null })
                Chip("Match a video", selected = mode == "video", onClick = { mode = "video"; result = null; message = null })
                Chip("Sketch it", selected = mode == "sketch", onClick = { mode = "sketch"; result = null; message = null })
            }
            Spacer(Modifier.height(20.dp))

            if (mode == "draw") {
                Text("Draw how far it has moved over time. The dashed lines are the start and the rest position.",
                    style = MaterialTheme.typography.bodySmall, color = t.inkSoft)
                Spacer(Modifier.height(10.dp))
                val shape = RoundedCornerShape(t.radiusCard)
                Box(
                    Modifier
                        .fillMaxWidth().aspectRatio(1.5f)
                        .clip(shape).background(t.surface).border(t.hairline, t.line, shape)
                        .testTag("captureCanvas")
                        .drawBehind {
                            listOf(BASE_Y, TARGET_Y).forEach {
                                drawLine(t.line, Offset(0f, size.height * it), Offset(size.width, size.height * it), 1.5f, pathEffect = DashedGuide)
                            }
                            if (drawn.size > 1) {
                                val path = Path().apply {
                                    drawn.forEachIndexed { i, p -> if (i == 0) moveTo(p.x * size.width, p.y * size.height) else lineTo(p.x * size.width, p.y * size.height) }
                                }
                                drawPath(path, t.ink.copy(alpha = 0.35f), style = Stroke(6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                            }
                            result?.let { r ->
                                val path = Path()
                                for (i in 0..120) {
                                    val x = i / 120f
                                    val v = SpringMath.stepResponse(x * resultSpan.toDouble(), r.stiffness.toDouble(), r.dampingRatio.toDouble()).toFloat()
                                    val y = BASE_Y - v * (BASE_Y - TARGET_Y)
                                    if (i == 0) path.moveTo(0f, y * size.height) else path.lineTo(x * size.width, y * size.height)
                                }
                                drawPath(path, t.ink, style = Stroke(4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                            }
                        }
                        .pointerInput(durationSec) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                down.consume()
                                val pts = mutableListOf(Offset(down.position.x / size.width, down.position.y / size.height))
                                drawn = pts.toList()
                                while (true) {
                                    val ch = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                    if (!ch.pressed) break
                                    pts += Offset((ch.position.x / size.width).coerceIn(0f, 1f), (ch.position.y / size.height).coerceIn(0f, 1.2f))
                                    ch.consume()
                                    drawn = pts.toList()
                                }
                                fitDrawn(pts)
                            }
                        },
                )
                Spacer(Modifier.height(12.dp))
                ValueSlider("Time shown", durationT, onValueChange = { durationT = it })
                Text("The canvas spans ${(durationSec * 1000).roundToInt()} ms.", style = MaterialTheme.typography.bodySmall, color = t.inkSoft)
            } else if (mode == "sketch") {
                SketchPad(fit = result, span = resultSpan, onSketch = { curve ->
                    val fit = curve?.let { SpringFit.fit(it) }
                    result = fit
                    measured = curve.orEmpty()
                    resultSpan = (curve?.lastOrNull()?.first ?: 1f).coerceAtLeast(0.2f)
                    message = when {
                        curve == null -> "Drag from where it starts to where it lands, in one stroke and a little slower than a flick."
                        fit == null -> "Couldn't fit a spring to that motion."
                        else -> null
                    }
                })
            } else {
                PrimaryButton(if (uri == null) "Choose a video" else "Choose another video", onClick = { pickVideo.launch(arrayOf("video/*")) }, icon = IconKind.PLUS)
                frame?.let { bmp ->
                    Spacer(Modifier.height(14.dp))
                    Text("Tap the object you want to follow.", style = MaterialTheme.typography.bodySmall, color = t.inkSoft)
                    Spacer(Modifier.height(8.dp))
                    Image(
                        bitmap = bmp.asImageBitmap(), contentDescription = "First frame",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth().aspectRatio(bmp.width.toFloat() / bmp.height)
                            .clip(RoundedCornerShape(t.radiusControl))
                            .testTag("captureFrame")
                            .pointerInput(bmp) {
                                detectTapGestures { p ->
                                    val c = VideoFrames.colorAt(bmp, p.x / size.width, p.y / size.height)
                                    if (twoObjects && slot == 1) pickedColor2 = c else pickedColor = c
                                }
                            },
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(22.dp).clip(CircleShape).background(pickedColor?.let { Color(it) } ?: t.line).border(t.hairline, t.line, CircleShape))
                        Spacer(Modifier.size(10.dp))
                        Text(if (pickedColor == null) "No object picked yet" else "Following this colour", style = MaterialTheme.typography.bodySmall, color = t.inkSoft)
                    }
                    Spacer(Modifier.height(10.dp))
                    Chip(if (twoObjects) "Tracking two objects" else "Track a second object", selected = twoObjects, onClick = {
                        twoObjects = !twoObjects; slot = 0; pair = null; result = null
                    }, modifier = Modifier.testTag("twoObjects"))
                    if (twoObjects) {
                        Spacer(Modifier.height(10.dp))
                        Text("A card and its shadow, or two staggered rows: pick each, then tap the frame.", style = MaterialTheme.typography.bodySmall, color = t.inkSoft)
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Chip("Object 1", selected = slot == 0, onClick = { slot = 0 })
                            Chip("Object 2", selected = slot == 1, onClick = { slot = 1 })
                            Box(Modifier.size(22.dp).clip(CircleShape).background(pickedColor2?.let { Color(it) } ?: t.line).border(t.hairline, t.line, CircleShape))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    ValueSlider("Start at", startT, onValueChange = { startT = it }, onValueChangeFinished = {
                        uri?.let { u -> scope.launch { frame = withContext(Dispatchers.IO) { VideoFrames.frameAt(context, u, startSec()) } } }
                    })
                    Text("${"%.1f".format(startSec())} s into a ${"%.1f".format(videoLen)} s video; the next 3 s are analysed.",
                        style = MaterialTheme.typography.bodySmall, color = t.inkSoft)
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton(if (progress != null) "Analysing ${(progress!! * 100).roundToInt()}%" else "Analyse", onClick = {
                        val u = uri
                        val c = pickedColor
                        if (u == null || c == null) { message = "Tap the object first."; return@PrimaryButton }
                        if (progress != null) return@PrimaryButton
                        val c2 = pickedColor2
                        if (twoObjects) {
                            if (c2 == null) { message = "Pick the second object too: choose Object 2, then tap it."; return@PrimaryButton }
                            if (!MultiTrack.distinguishable(c, c2)) { message = "Those two colours are too alike to follow separately."; return@PrimaryButton }
                        }
                        progress = 0f
                        message = null
                        pair = null
                        if (twoObjects && c2 != null) {
                            scope.launch {
                                val both = withContext(Dispatchers.Default) {
                                    VideoFrames.trackAll(context, u, listOf(c, c2), startSec()) { p -> progress = p }
                                }
                                progress = null
                                when (val out = MultiTrack.analyse(both[0], both[1])) {
                                    is MultiTrack.Outcome.Failed -> { result = null; message = out.reason }
                                    is MultiTrack.Outcome.Ok -> {
                                        pair = out.analysis
                                        result = out.analysis.first.fit
                                        measured = out.analysis.first.curve
                                        resultSpan = (measured.lastOrNull()?.first ?: 1f).coerceAtLeast(0.2f)
                                    }
                                }
                            }
                            return@PrimaryButton
                        }
                        scope.launch {
                            val samples = withContext(Dispatchers.Default) {
                                VideoFrames.track(context, u, c, startSec()) { p -> progress = p }
                            }
                            progress = null
                            val curve = ObjectTracker.toCurve(samples)
                            val fit = curve?.let { SpringFit.fit(it) }
                            result = fit
                            measured = curve.orEmpty()
                            resultSpan = (curve?.lastOrNull()?.first ?: 1f).coerceAtLeast(0.2f)
                            message = when {
                                samples.size < 6 -> "Couldn't find that colour in enough frames."
                                curve == null -> "That object barely moved; pick the moving one, or start earlier."
                                fit == null -> "Couldn't fit a spring to that motion."
                                else -> null
                            }
                        }
                    })
                }
            }
            message?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = t.ink, modifier = Modifier.testTag("captureMessage"))
            }
            Spacer(Modifier.height(40.dp))
        }

        result?.let { r ->
            Column(Modifier.reveal(1)) {
                SectionLabel("Result")
                val spring = SpringSpec(r.stiffness, r.dampingRatio)
                Text("Stiffness ${r.stiffness.roundToInt()}   /   damping ratio ${"%.2f".format(r.dampingRatio)}",
                    style = MaterialTheme.typography.titleMedium, color = t.ink, modifier = Modifier.testTag("captureResult"))
                Text("Settles in ${spring.settleMs} ms, overshoot ${spring.overshootPercent.roundToInt()}%. Fit error ${"%.1f".format(r.rms * 100)}%.",
                    style = MaterialTheme.typography.bodyMedium, color = t.inkSoft)
                if (mode != "draw" && measured.size > 2) {
                    Spacer(Modifier.height(12.dp))
                    FitChart(measured, r, resultSpan)
                }
                val spec = MotionSpec("Captured", "captured", "release", spring, listOf(MotionTransition("progress", 0f, 1f, 0, spring)))
                MotionLint.check(spec).forEach {
                    Text("Note: " + it.message, style = MaterialTheme.typography.bodySmall, color = t.ink)
                }
                pair?.let { p ->
                    Spacer(Modifier.height(16.dp))
                    val s2 = SpringSpec(p.second.fit.stiffness, p.second.fit.dampingRatio)
                    Text("Object 2: stiffness ${s2.stiffness.roundToInt()}   /   damping ratio ${"%.2f".format(s2.dampingRatio)}",
                        style = MaterialTheme.typography.titleMedium, color = t.ink, modifier = Modifier.testTag("captureResult2"))
                    Text("Settles in ${s2.settleMs} ms, overshoot ${s2.overshootPercent.roundToInt()}%.", style = MaterialTheme.typography.bodyMedium, color = t.inkSoft)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        when {
                            p.delayMs > 15 -> "Object 2 starts ${p.delayMs} ms after object 1."
                            p.delayMs < -15 -> "Object 2 starts ${-p.delayMs} ms before object 1."
                            else -> "Both objects start together."
                        },
                        style = MaterialTheme.typography.bodyMedium, color = t.ink, modifier = Modifier.testTag("captureDelay"),
                    )
                    if (p.delayMs > 15) {
                        Spacer(Modifier.height(12.dp))
                        PrimaryButton("Create staggered list", onClick = { onCreateStagger(r.stiffness, r.dampingRatio, p.delayMs.toFloat()) }, icon = IconKind.STAGGER)
                    }
                }
                Spacer(Modifier.height(16.dp))
                PrimaryButton("Create experiment", onClick = { onCreate(r.stiffness, r.dampingRatio) }, icon = IconKind.PLUS)
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(value = tokenName, onValueChange = { tokenName = it }, singleLine = true, placeholder = { Text("Token name") })
                TextLink("Save as motion token", icon = IconKind.PLUS, onClick = {
                    if (tokenName.isNotBlank()) { saveToken(tokenName, r.stiffness, r.dampingRatio); message = "Saved token “${tokenName.trim()}”" }
                })
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** Measured points (dots) against the fitted spring (line). */
@Composable
private fun FitChart(measured: List<Pair<Float, Float>>, fit: SpringFit.Result, span: Float) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.radiusCard)
    Canvas(Modifier.fillMaxWidth().height(120.dp).clip(shape).background(t.surface).border(t.hairline, t.line, shape)) {
        val top = (measured.maxOfOrNull { it.second } ?: 1f).coerceAtLeast(1.1f)
        fun y(v: Float) = size.height * (0.9f - 0.8f * (v / top))
        drawLine(t.line, Offset(0f, y(1f)), Offset(size.width, y(1f)), 1.5f, pathEffect = DashedGuide)
        val path = Path()
        for (i in 0..120) {
            val x = i / 120f
            val v = SpringMath.stepResponse(x * span.toDouble(), fit.stiffness.toDouble(), fit.dampingRatio.toDouble()).toFloat()
            if (i == 0) path.moveTo(0f, y(v)) else path.lineTo(x * size.width, y(v))
        }
        drawPath(path, t.ink, style = Stroke(3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        measured.forEach { (tm, v) -> drawCircle(t.inkSoft, 4f, Offset(tm / span * size.width, y(v))) }
    }
}
