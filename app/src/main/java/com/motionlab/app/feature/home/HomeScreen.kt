package com.motionlab.app.feature.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.motionlab.app.data.DoodleEntity
import com.motionlab.app.data.MotionTokenEntity
import com.motionlab.app.data.toMotionSpec
import com.motionlab.app.core.spec.MotionGrade
import com.motionlab.app.core.spec.SpringSpec
import com.motionlab.app.ui.design.SpringGlyph
import com.motionlab.app.export.MotionTokenExporter
import com.motionlab.app.data.ExperimentEntity
import com.motionlab.app.data.ProjectFile
import com.motionlab.app.data.TimelineEntity
import com.motionlab.app.export.DesignTokenExporter
import com.motionlab.app.feature.common.icon
import com.motionlab.app.feature.common.label
import com.motionlab.app.ui.design.AppIcon
import com.motionlab.app.ui.design.AppScreen
import com.motionlab.app.ui.design.DesignThemes
import com.motionlab.app.ui.design.DesignTokens
import com.motionlab.app.ui.design.IconKind
import com.motionlab.app.ui.design.ListRow
import com.motionlab.app.ui.design.LocalTokens
import com.motionlab.app.ui.design.PrimaryButton
import com.motionlab.app.ui.design.SectionLabel
import com.motionlab.app.ui.design.Tag
import com.motionlab.app.ui.design.TextLink
import com.motionlab.app.ui.design.ThemeId
import com.motionlab.app.ui.design.reveal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * No account, no cloud requirement, no onboarding wall (spec §32) --
 * opening the app lands here, with saved experiments one tap away.
 * Long-press a row to rename or delete it. "Appearance" switches the theme
 * and exports its tokens.
 */
@Composable
fun HomeScreen(
    recent: List<ExperimentEntity>,
    timelines: List<TimelineEntity>,
    themeId: ThemeId,
    onTheme: (ThemeId) -> Unit,
    onNewExperiment: () -> Unit,
    onOpen: (ExperimentEntity) -> Unit,
    onRename: (ExperimentEntity, String) -> Unit,
    onDelete: (ExperimentEntity) -> Unit,
    onUndoDelete: (ExperimentEntity) -> Unit,
    onPin: (ExperimentEntity, Boolean) -> Unit,
    onImport: suspend (String) -> String,
    onNewTimeline: () -> Unit,
    onOpenTimeline: (TimelineEntity) -> Unit,
    onRenameTimeline: (TimelineEntity, String) -> Unit,
    onDeleteTimeline: (TimelineEntity) -> Unit,
    doodles: List<DoodleEntity>,
    onNewDoodle: () -> Unit,
    onOpenDoodle: (DoodleEntity) -> Unit,
    onRenameDoodle: (DoodleEntity, String) -> Unit,
    onDeleteDoodle: (DoodleEntity) -> Unit,
    onUndoDeleteDoodle: (DoodleEntity) -> Unit,
    tokens: List<MotionTokenEntity>,
    onDeleteToken: (MotionTokenEntity) -> Unit,
    onCapture: () -> Unit,
    onCompare: () -> Unit,
    feelSets: FeelSetsUi,
) {
    val t = LocalTokens.current
    var managing by remember { mutableStateOf<ExperimentEntity?>(null) }
    var managingTimeline by remember { mutableStateOf<TimelineEntity?>(null) }
    var managingDoodle by remember { mutableStateOf<DoodleEntity?>(null) }
    var managingToken by remember { mutableStateOf<MotionTokenEntity?>(null) }
    var exportingTokens by remember { mutableStateOf(false) }
    val tokenClipboard = LocalClipboardManager.current
    var deletedDoodle by remember { mutableStateOf<DoodleEntity?>(null) }
    LaunchedEffect(deletedDoodle) {
        if (deletedDoodle != null) {
            delay(4000)
            deletedDoodle = null
        }
    }
    val now = remember(recent) { System.currentTimeMillis() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var notice by remember { mutableStateOf<String?>(null) }
    var exporting by remember { mutableStateOf<ExperimentEntity?>(null) }
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(3200)
            notice = null
        }
    }
    // Delete is instant, not a second confirm -- this is what makes it reversible.
    var recentlyDeleted by remember { mutableStateOf<ExperimentEntity?>(null) }
    LaunchedEffect(recentlyDeleted) {
        if (recentlyDeleted != null) {
            delay(4000)
            recentlyDeleted = null
        }
    }

    // System file pickers: no storage permission needed, the user hands us one file.
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            notice = try {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { readCapped(it, ProjectFile.MAX_BYTES) }
                }
                if (text == null) "Couldn't open that file." else onImport(text)
            } catch (_: Exception) {
                "Couldn't read that file."
            }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(
        // Octet-stream so the provider keeps the .motionlab name instead of appending .json.
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val item = exporting
        exporting = null
        if (uri != null && item != null) scope.launch {
            notice = try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(ProjectFile.encode(item).toByteArray()) }
                }
                "Exported \u201c${item.name}\u201d"
            } catch (_: Exception) {
                "Couldn't write that file."
            }
        }
    }

    AppScreen(scrollable = true) {
        Spacer(Modifier.height(72.dp))

        Column(Modifier.reveal(0)) {
            Text("Motion Lab", style = MaterialTheme.typography.displayMedium, color = t.ink)
            Spacer(Modifier.height(18.dp))
            Text(
                text = "Tune how things move in an app, by feel. Then copy the code into your project.",
                style = MaterialTheme.typography.bodyLarge,
                color = t.inkSoft,
            )
            Spacer(Modifier.height(24.dp))
            HomeDemo()
            Spacer(Modifier.height(28.dp))
            PrimaryButton("New experiment", onClick = onNewExperiment, icon = IconKind.PLUS)
            Spacer(Modifier.height(4.dp))
            TextLink(label = "Capture a feel from a drawing or video", icon = IconKind.BRUSH, onClick = onCapture)
            TextLink(label = "Compare two springs, race or blind", icon = IconKind.SPRING, onClick = onCompare)
        }

        Spacer(Modifier.height(64.dp))

        Column(Modifier.reveal(1)) {
            SectionLabel("Recent", trailing = if (recent.isEmpty()) null else "${recent.size}")
            notice?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = t.ink)
            }
            recentlyDeleted?.let { deleted ->
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Deleted “${deleted.name}”",
                        style = MaterialTheme.typography.bodySmall,
                        color = t.inkSoft,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Undo",
                        style = MaterialTheme.typography.bodySmall,
                        color = t.ink,
                        modifier = Modifier.clickable {
                            onUndoDelete(deleted)
                            recentlyDeleted = null
                        },
                    )
                }
            }
            if (recent.isEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("Nothing here yet.", style = MaterialTheme.typography.bodyMedium, color = t.inkSoft)
            }
            recent.forEach { item ->
                ListRow(
                    title = item.name,
                    subtitle = "${item.type.label}  /  ${relativeTime(now, item.updatedAt)}",
                    leading = item.type.icon,
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val grade = remember(item) { MotionGrade.of(item.toMotionSpec()) }
                            Tag(grade.letter)
                            Spacer(Modifier.width(10.dp))
                            if (item.pinned) {
                                AppIcon(IconKind.PIN, tint = t.inkSoft, size = 16.dp)
                                Spacer(Modifier.width(8.dp))
                            }
                            AppIcon(IconKind.FORWARD, tint = t.inkFaint, size = 18.dp)
                        }
                    },
                    onClick = { onOpen(item) },
                    onLongClick = { managing = item },
                )
            }
            Spacer(Modifier.height(8.dp))
            TextLink(
                label = "Import a .motionlab file",
                icon = IconKind.PLUS,
                onClick = { importLauncher.launch(arrayOf("*/*")) },
            )
        }

        Spacer(Modifier.height(56.dp))
        Column(Modifier.reveal(2)) {
            SectionLabel("Timelines", trailing = if (timelines.isEmpty()) null else "${timelines.size}")
            if (timelines.isEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "A saved sequence of experiments, played one after another.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = t.inkSoft,
                )
                Spacer(Modifier.height(16.dp))
            }
            timelines.forEach { tl ->
                ListRow(
                    title = tl.name,
                    subtitle = "Timeline  /  ${relativeTime(now, tl.updatedAt)}",
                    leading = IconKind.TIMELINE,
                    trailing = { AppIcon(IconKind.FORWARD, tint = t.inkFaint, size = 18.dp) },
                    onClick = { onOpenTimeline(tl) },
                    onLongClick = { managingTimeline = tl },
                )
            }
            Spacer(Modifier.height(8.dp))
            TextLink(label = "New timeline", icon = IconKind.PLUS, onClick = onNewTimeline)
        }

        Spacer(Modifier.height(56.dp))
        Column(Modifier.reveal(3)) {
            SectionLabel("Doodles", trailing = if (doodles.isEmpty()) null else "${doodles.size}")
            deletedDoodle?.let { gone ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Deleted \u201c${gone.name}\u201d", style = MaterialTheme.typography.bodySmall, color = t.inkSoft)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Undo", style = MaterialTheme.typography.bodySmall, color = t.ink,
                        modifier = Modifier.clickable { onUndoDeleteDoodle(gone); deletedDoodle = null },
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
            if (doodles.isEmpty()) {
                Text(
                    "A blank canvas to sketch on, with brushes and paints.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = t.inkSoft,
                )
                Spacer(Modifier.height(16.dp))
            }
            doodles.forEach { d ->
                ListRow(
                    title = d.name,
                    subtitle = "Doodle  /  ${relativeTime(now, d.updatedAt)}",
                    leading = IconKind.BRUSH,
                    trailing = { AppIcon(IconKind.FORWARD, tint = t.inkFaint, size = 18.dp) },
                    onClick = { onOpenDoodle(d) },
                    onLongClick = { managingDoodle = d },
                )
            }
            Spacer(Modifier.height(8.dp))
            TextLink(label = "New doodle", icon = IconKind.PLUS, onClick = onNewDoodle)
        }

        Spacer(Modifier.height(56.dp))
        Column(Modifier.reveal(4)) {
            SectionLabel("Motion tokens", trailing = if (tokens.isEmpty()) null else "${tokens.size}")
            if (tokens.isEmpty()) {
                Text(
                    "Name a spring from any experiment's Export section, reuse it anywhere, and export the set for every platform.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = t.inkSoft,
                )
            }
            tokens.forEach { tk ->
                ListRow(
                    title = tk.name,
                    subtitle = "stiffness ${"%.0f".format(tk.stiffness)}  /  damping ratio ${"%.2f".format(tk.dampingRatio)}",
                    trailing = {
                        val spring = remember(tk) { SpringSpec(tk.stiffness, tk.dampingRatio) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Tag(remember(tk) { MotionGrade.ofSpring(tk.name, spring).letter })
                            Spacer(Modifier.width(10.dp))
                            SpringGlyph(spring)
                        }
                    },
                    onLongClick = { managingToken = tk },
                )
            }
            if (tokens.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                TextLink(label = "Copy tokens as code", icon = IconKind.COPY, onClick = { exportingTokens = true })
            }
        }

        Spacer(Modifier.height(56.dp))
        Column(Modifier.reveal(4)) { FeelSetsSection(feelSets) }

        Spacer(Modifier.height(56.dp))
        Column(Modifier.reveal(5)) { AppearanceSection(themeId, onTheme) }
        Spacer(Modifier.height(48.dp))
    }

    managing?.let { item ->
        ManageExperimentDialog(
            item = item,
            onDismiss = { managing = null },
            onRename = { newName ->
                onRename(item, newName)
                managing = null
            },
            onDelete = {
                onDelete(item)
                recentlyDeleted = item
                managing = null
            },
            onExport = {
                exporting = item
                managing = null
                exportLauncher.launch(ProjectFile.fileName(item.name))
            },
            onTogglePin = {
                onPin(item, !item.pinned)
                managing = null
            },
        )
    }

    managingToken?.let { tk ->
        AlertDialog(
            onDismissRequest = { managingToken = null },
            containerColor = t.surface,
            titleContentColor = t.ink,
            textContentColor = t.ink,
            shape = RoundedCornerShape(t.radiusCard),
            title = { Text(tk.name, style = MaterialTheme.typography.titleLarge) },
            confirmButton = {
                TextButton(onClick = { onDeleteToken(tk); managingToken = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = { TextButton(onClick = { managingToken = null }) { Text("Cancel", color = t.inkSoft, style = MaterialTheme.typography.labelLarge) } },
        )
    }

    if (exportingTokens) {
        AlertDialog(
            onDismissRequest = { exportingTokens = false },
            containerColor = t.surface,
            titleContentColor = t.ink,
            textContentColor = t.ink,
            shape = RoundedCornerShape(t.radiusCard),
            title = { Text("Copy tokens as", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column {
                    MotionTokenExporter.Format.entries.forEach { f ->
                        TextLink(label = f.label, icon = IconKind.COPY, onClick = {
                            tokenClipboard.setText(AnnotatedString(MotionTokenExporter.export(tokens, f)))
                            notice = "Copied ${f.label}"
                            exportingTokens = false
                        })
                    }
                }
            },
            confirmButton = { TextButton(onClick = { exportingTokens = false }) { Text("Close", color = t.inkSoft, style = MaterialTheme.typography.labelLarge) } },
        )
    }

    managingDoodle?.let { item ->
        var name by remember(item.id) { mutableStateOf(item.name) }
        AlertDialog(
            onDismissRequest = { managingDoodle = null },
            containerColor = t.surface,
            titleContentColor = t.ink,
            textContentColor = t.ink,
            shape = RoundedCornerShape(t.radiusCard),
            title = { Text("Rename doodle", style = MaterialTheme.typography.titleLarge) },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, shape = RoundedCornerShape(t.radiusControl)) },
            confirmButton = {
                TextButton(onClick = { if (name.isNotBlank()) { onRenameDoodle(item, name); managingDoodle = null } }) {
                    Text("Save", color = t.ink, style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { onDeleteDoodle(item); deletedDoodle = item; managingDoodle = null }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                    }
                    TextButton(onClick = { managingDoodle = null }) {
                        Text("Cancel", color = t.inkSoft, style = MaterialTheme.typography.labelLarge)
                    }
                }
            },
        )
    }

    managingTimeline?.let { item ->
        ManageTimelineDialog(
            item = item,
            onDismiss = { managingTimeline = null },
            onRename = { newName ->
                onRenameTimeline(item, newName)
                managingTimeline = null
            },
            onDelete = {
                onDeleteTimeline(item)
                managingTimeline = null
            },
        )
    }
}

/** Reads at most [max] characters; anything longer comes back one over so the caller's size check rejects it. */
private fun readCapped(stream: InputStream, max: Int): String {
    val out = ByteArrayOutputStream()
    val buf = ByteArray(8 * 1024)
    while (out.size() <= max) {
        val n = stream.read(buf)
        if (n < 0) break
        out.write(buf, 0, n)
    }
    return out.toString(Charsets.UTF_8.name())
}

/** Theme swatches plus a way to take the design tokens into a design tool. */
@Composable
private fun AppearanceSection(themeId: ThemeId, onTheme: (ThemeId) -> Unit) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1600)
            copied = false
        }
    }

    SectionLabel("Appearance")
    Spacer(Modifier.height(20.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        DesignThemes.all.forEach { theme ->
            Swatch(theme, selected = theme.id == themeId, onClick = { onTheme(theme.id) })
        }
    }
    Spacer(Modifier.height(12.dp))
    TextLink(
        label = if (copied) "Copied" else "Copy design tokens",
        icon = if (copied) IconKind.CHECK else IconKind.COPY,
        onClick = {
            clipboard.setText(AnnotatedString(DesignTokenExporter.json(DesignThemes.of(themeId))))
            copied = true
        },
    )
}

/** A miniature of a theme: its canvas, with its ink as the dot. */
@Composable
private fun Swatch(theme: DesignTokens, selected: Boolean, onClick: () -> Unit) {
    val current = LocalTokens.current
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier.clip(shape).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(theme.canvas, shape)
                // Outlined in the *current* ink so every swatch reads against the page behind it.
                .border(if (selected) 1.5.dp else 1.dp, if (selected) current.ink else current.line, shape),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(14.dp).background(theme.ink, CircleShape))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = theme.id.label,
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) current.ink else current.inkSoft,
        )
    }
}

@Composable
private fun ManageExperimentDialog(
    item: ExperimentEntity,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
    onTogglePin: () -> Unit,
) {
    val t = LocalTokens.current
    var name by remember(item.id) { mutableStateOf(item.name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = t.surface,
        titleContentColor = t.ink,
        textContentColor = t.ink,
        shape = RoundedCornerShape(t.radiusCard),
        title = { Text("Rename experiment", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    shape = RoundedCornerShape(t.radiusControl),
                )
                Spacer(Modifier.height(8.dp))
                TextLink(
                    label = if (item.pinned) "Unpin from top" else "Pin to top",
                    icon = IconKind.PIN,
                    onClick = onTogglePin,
                )
                Spacer(Modifier.height(8.dp))
                TextLink(label = "Export as .motionlab", icon = IconKind.SHARE, onClick = onExport)
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onRename(name) }) {
                Text("Save", color = t.ink, style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) {
                    Text("Delete", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = t.inkSoft, style = MaterialTheme.typography.labelLarge)
                }
            }
        },
    )
}

@Composable
private fun ManageTimelineDialog(
    item: TimelineEntity,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val t = LocalTokens.current
    var name by remember(item.id) { mutableStateOf(item.name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = t.surface,
        titleContentColor = t.ink,
        textContentColor = t.ink,
        shape = RoundedCornerShape(t.radiusCard),
        title = { Text("Rename timeline", style = MaterialTheme.typography.titleLarge) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                shape = RoundedCornerShape(t.radiusControl),
            )
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onRename(name) }) {
                Text("Save", color = t.ink, style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) {
                    Text("Delete", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = t.inkSoft, style = MaterialTheme.typography.labelLarge)
                }
            }
        },
    )
}
