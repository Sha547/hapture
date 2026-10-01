package com.klynstudios.hapture.feature.doodle

import android.content.ContentValues
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.klynstudios.hapture.core.doodle.Brush
import com.klynstudios.hapture.core.doodle.DOODLE_PALETTE
import com.klynstudios.hapture.core.doodle.DoodleBackground
import com.klynstudios.hapture.core.doodle.DoodleCodec
import com.klynstudios.hapture.core.doodle.DoodleGeometry
import com.klynstudios.hapture.core.doodle.DoodleStroke
import com.klynstudios.hapture.core.doodle.DoodleSvg
import com.klynstudios.hapture.core.doodle.Pt
import com.klynstudios.hapture.core.doodle.doodleWidthFor
import com.klynstudios.hapture.data.DoodleEntity
import com.klynstudios.hapture.data.DoodleRepository
import com.klynstudios.hapture.ui.design.AppIcon
import com.klynstudios.hapture.ui.design.AppScreen
import com.klynstudios.hapture.ui.design.Chip
import com.klynstudios.hapture.ui.design.IconKind
import com.klynstudios.hapture.ui.design.ListRow
import com.klynstudios.hapture.ui.design.LocalTokens
import com.klynstudios.hapture.ui.design.SectionLabel
import com.klynstudios.hapture.ui.design.TextLink
import com.klynstudios.hapture.ui.design.TopBar
import com.klynstudios.hapture.ui.design.ValueSlider
import com.klynstudios.hapture.ui.design.reveal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A freehand canvas. Strokes are stored canvas-relative ([Pt]), painted by one
 * shared painter ([drawDoodle]) and autosaved after every change, so a doodle
 * survives leaving the screen and exports at any resolution.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DoodleScreen(
    doodleId: Long,
    repository: DoodleRepository,
    onBack: () -> Unit,
) {
    val t = LocalTokens.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var loaded by remember { mutableStateOf<DoodleEntity?>(null) }
    val strokes = remember { mutableStateListOf<DoodleStroke>() }
    val redo = remember { mutableStateListOf<DoodleStroke>() }
    var background by remember { mutableStateOf(DoodleBackground.PAPER) }
    var brush by remember { mutableStateOf(Brush.PEN) }
    var color by remember { mutableIntStateOf(DOODLE_PALETTE[0]) }
    var sizeT by remember { mutableFloatStateOf(0.3f) }
    var hueT by remember { mutableFloatStateOf(0.6f) }
    var live by remember { mutableStateOf<DoodleStroke?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(doodleId) {
        repository.get(doodleId)?.let { e ->
            loaded = e
            strokes.clear()
            strokes.addAll(DoodleCodec.decode(e.strokes))
            background = DoodleBackground.entries.firstOrNull { it.name == e.background } ?: DoodleBackground.PAPER
        }
    }
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(3200)
            notice = null
        }
    }

    fun persist() {
        val e = loaded ?: return
        val snapshot = strokes.toList()
        val bg = background
        scope.launch { repository.save(e.copy(strokes = DoodleCodec.encode(snapshot), background = bg.name)) }
    }

    fun pngFile(): File {
        val dir = File(context.cacheDir, "doodles").apply { mkdirs() }
        val safe = (loaded?.name ?: "doodle").replace(Regex("[^A-Za-z0-9]+"), "-").trim('-').ifEmpty { "doodle" }
        return File(dir, "$safe.png")
    }

    fun savePng() {
        val bytes = renderDoodlePng(strokes.toList(), background)
        val name = pngFile().name
        scope.launch {
            notice = try {
                withContext(Dispatchers.IO) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val values = ContentValues().apply {
                            put(MediaStore.Images.Media.DISPLAY_NAME, name)
                            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Hapture")
                        }
                        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                            ?: error("no uri")
                        context.contentResolver.openOutputStream(uri)!!.use { it.write(bytes) }
                        "Saved to Pictures/Hapture"
                    } else {
                        val dir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)!!.apply { mkdirs() }
                        File(dir, name).writeBytes(bytes)
                        "Saved to ${File(dir, name).absolutePath}"
                    }
                }
            } catch (_: Exception) {
                "Couldn't save the image."
            }
        }
    }

    fun sharePng() {
        val bytes = renderDoodlePng(strokes.toList(), background)
        scope.launch {
            try {
                val file = withContext(Dispatchers.IO) { pngFile().also { it.writeBytes(bytes) } }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(send, "Share doodle"))
            } catch (_: Exception) {
                notice = "Couldn't share the image."
            }
        }
    }

    AppScreen(scrollable = true) {
        TopBar(title = loaded?.name ?: "Doodle", onBack = onBack)
        Spacer(Modifier.height(8.dp))

        Column(Modifier.reveal(0)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(t.radiusCard))
                    .border(t.hairline, t.line, RoundedCornerShape(t.radiusCard))
                    .testTag("doodleCanvas")
                    .drawBehind {
                        drawDoodle(strokes.toList(), background)
                        live?.let { drawDoodleStroke(it, background) }
                    }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            down.consume()
                            fun norm(p: androidx.compose.ui.geometry.Offset) =
                                Pt((p.x / size.width).coerceIn(0f, 1f), (p.y / size.height).coerceIn(0f, 1f))

                            val pts = mutableListOf(norm(down.position))
                            val base = DoodleStroke(brush, color, doodleWidthFor(sizeT), pts.toList())
                            live = base
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                pts += norm(change.position)
                                change.consume()
                                live = base.copy(points = pts.toList())
                            }
                            val finished = base.copy(points = DoodleGeometry.thinned(pts))
                            live = null
                            strokes += finished
                            redo.clear()
                            persist()
                        }
                    },
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                TextLink("Undo", icon = IconKind.UNDO, modifier = Modifier.testTag("doodleUndo"), onClick = {
                    if (strokes.isNotEmpty()) {
                        redo += strokes.removeAt(strokes.lastIndex)
                        persist()
                    }
                })
                TextLink("Redo", icon = IconKind.REDO, onClick = {
                    if (redo.isNotEmpty()) {
                        strokes += redo.removeAt(redo.lastIndex)
                        persist()
                    }
                })
                TextLink("Clear", icon = IconKind.CLOSE, onClick = {
                    if (strokes.isNotEmpty()) {
                        redo.clear()
                        strokes.clear()
                        persist()
                    }
                })
            }
            Spacer(Modifier.height(32.dp))
        }

        Column(Modifier.reveal(1)) {
            SectionLabel("Brush")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Brush.entries.forEach { b -> Chip(b.label, selected = b == brush, onClick = { brush = b }) }
            }
            Spacer(Modifier.height(12.dp))
            ValueSlider("Size", sizeT, onValueChange = { sizeT = it })
            Spacer(Modifier.height(32.dp))
        }

        Column(Modifier.reveal(2)) {
            SectionLabel("Colour")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DOODLE_PALETTE.forEach { c ->
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(c))
                            .border(if (c == color) 2.dp else t.hairline, if (c == color) t.ink else t.line, CircleShape)
                            .clickable { color = c; if (brush == Brush.ERASER) brush = Brush.PEN },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            ValueSlider("Custom hue", hueT, onValueChange = {
                hueT = it
                color = Color.hsv(it * 360f, 0.72f, 0.88f).toArgb()
                if (brush == Brush.ERASER) brush = Brush.PEN
            })
            Spacer(Modifier.height(32.dp))
        }

        Column(Modifier.reveal(3)) {
            SectionLabel("Canvas")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DoodleBackground.entries.forEach { b ->
                    Chip(b.label, selected = b == background, onClick = { background = b; persist() })
                }
            }
            Spacer(Modifier.height(32.dp))
        }

        Column(Modifier.reveal(4)) {
            SectionLabel("Export")
            notice?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = t.ink)
                Spacer(Modifier.height(8.dp))
            }
            ListRow(
                title = "Save PNG", subtitle = "1080 px image in your Pictures",
                onClick = ::savePng, trailing = { AppIcon(IconKind.SHARE, tint = t.inkSoft, size = 18.dp) },
            )
            ListRow(
                title = "Share PNG", subtitle = "Send the image to another app",
                onClick = ::sharePng, trailing = { AppIcon(IconKind.SHARE, tint = t.inkSoft, size = 18.dp) },
            )
            ListRow(
                title = "Copy SVG", subtitle = "Vector paths for a design tool",
                onClick = {
                    clipboard.setText(AnnotatedString(DoodleSvg.of(strokes.toList(), background)))
                    notice = "SVG copied"
                },
                trailing = { AppIcon(IconKind.COPY, tint = t.inkSoft, size = 18.dp) },
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
