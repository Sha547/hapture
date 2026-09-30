package com.klynstudios.hapture.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.klynstudios.hapture.core.spec.Archetypes
import com.klynstudios.hapture.core.spec.SpringSpec
import com.klynstudios.hapture.data.MotionTokenEntity
import com.klynstudios.hapture.data.TokenSetWithItems
import com.klynstudios.hapture.export.ExportStamp
import com.klynstudios.hapture.export.MotionTokenExporter
import com.klynstudios.hapture.ui.design.Chip
import com.klynstudios.hapture.ui.design.IconKind
import com.klynstudios.hapture.ui.design.ListRow
import com.klynstudios.hapture.ui.design.LocalTokens
import com.klynstudios.hapture.ui.design.PrimaryButton
import com.klynstudios.hapture.ui.design.SectionLabel
import com.klynstudios.hapture.ui.design.SpringGlyph
import com.klynstudios.hapture.ui.design.TextLink

/** Everything the feel-sets section needs from the app, so [HomeScreen] gains one parameter, not six. */
class FeelSetsUi(
    val sets: List<TokenSetWithItems>,
    val tokens: List<MotionTokenEntity>,
    /** Every spring the app knows by name (tokens and experiments), for checking shipped code against. */
    val currentSprings: List<ExportStamp.Spring>,
    val onApply: (List<Archetypes.Named>) -> Unit,
    val onSaveSet: (name: String, archetype: String) -> Unit,
    val onDeleteSet: (TokenSetWithItems) -> Unit,
)

private fun List<Archetypes.Named>.asTokens() = map { MotionTokenEntity(name = it.name, stiffness = it.stiffness, dampingRatio = it.dampingRatio, createdAt = 0) }

@Composable
fun FeelSetsSection(ui: FeelSetsUi) {
    val t = LocalTokens.current
    var open by remember { mutableStateOf<Triple<String, String, List<Archetypes.Named>>?>(null) }
    var openUser by remember { mutableStateOf<TokenSetWithItems?>(null) }
    var saving by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }

    SectionLabel("Feel sets", trailing = "${Archetypes.all.size + ui.sets.size}")
    Spacer(Modifier.height(4.dp))
    Text(
        "A bundle of springs for a kind of app. Add one to your tokens, or copy it as code for a new project.",
        style = MaterialTheme.typography.bodyMedium, color = t.inkSoft,
    )
    Spacer(Modifier.height(8.dp))
    Archetypes.all.forEach { a ->
        ListRow(
            title = a.title, subtitle = a.blurb,
            onClick = { open = Triple(a.title, a.blurb, a.springs) },
            trailing = { SpringGlyph(SpringSpec(a.springs.first().stiffness, a.springs.first().dampingRatio)) },
        )
    }
    ui.sets.forEach { s ->
        ListRow(
            title = s.set.name, subtitle = "${s.set.archetype.ifEmpty { "Yours" }}  /  ${s.items.size} springs",
            onClick = { openUser = s },
        )
    }
    Spacer(Modifier.height(8.dp))
    if (ui.tokens.isNotEmpty()) TextLink("Save my tokens as a set", icon = IconKind.PLUS, onClick = { saving = true })
    TextLink("Check shipped code against my springs", icon = IconKind.CHECK, onClick = { checking = true }, modifier = Modifier.testTag("checkShipped"))

    open?.let { (title, blurb, springs) ->
        FeelSetDialog(title, blurb, springs, existing = ui.tokens.map { it.name }, onApply = { ui.onApply(springs) }, onDelete = null, onDismiss = { open = null })
    }
    openUser?.let { s ->
        FeelSetDialog(
            s.set.name, s.set.archetype, s.items.map { Archetypes.Named(it.name, it.stiffness, it.dampingRatio) },
            existing = ui.tokens.map { it.name }, onApply = { ui.onApply(s.items.map { Archetypes.Named(it.name, it.stiffness, it.dampingRatio) }) },
            onDelete = { ui.onDeleteSet(s); openUser = null }, onDismiss = { openUser = null },
        )
    }
    if (saving) SaveSetDialog(count = ui.tokens.size, onSave = { n, a -> ui.onSaveSet(n, a); saving = false }, onDismiss = { saving = false })
    if (checking) CheckCodeDialog(ui.currentSprings) { checking = false }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FeelSetDialog(
    title: String, blurb: String, springs: List<Archetypes.Named>, existing: List<String>,
    onApply: () -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit,
) {
    val t = LocalTokens.current
    val clipboard = LocalClipboardManager.current
    var message by remember { mutableStateOf<String?>(null) }
    val plan = Archetypes.plan(existing, springs)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = t.surface, titleContentColor = t.ink, textContentColor = t.ink,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (blurb.isNotBlank()) Text(blurb, style = MaterialTheme.typography.bodyMedium, color = t.inkSoft)
                Spacer(Modifier.height(10.dp))
                springs.forEach { s ->
                    Row(Modifier.fillMaxWidth().padding4(), verticalAlignment = Alignment.CenterVertically) {
                        SpringGlyph(SpringSpec(s.stiffness, s.dampingRatio))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(s.name, style = MaterialTheme.typography.titleMedium, color = t.ink)
                            Text("stiffness ${s.stiffness.toInt()}  /  damping ratio ${"%.2f".format(s.dampingRatio)}", style = MaterialTheme.typography.bodySmall, color = t.inkSoft)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("Copy as", style = MaterialTheme.typography.labelMedium, color = t.inkSoft)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MotionTokenExporter.Format.entries.forEach { f ->
                        Chip(f.label, selected = false, onClick = {
                            clipboard.setText(AnnotatedString(MotionTokenExporter.export(springs.asTokens(), f)))
                            message = "Copied ${f.label}."
                        })
                    }
                }
                message?.let { Spacer(Modifier.height(10.dp)); Text(it, style = MaterialTheme.typography.bodySmall, color = t.ink) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onApply()
                message = "Added ${plan.added} to your tokens" + if (plan.overwritten > 0) " and replaced ${plan.overwritten} with the same name." else "."
            }) { Text("Add to my tokens", color = t.ink, style = MaterialTheme.typography.labelLarge) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete", color = t.inkSoft, style = MaterialTheme.typography.labelLarge) }
                TextButton(onClick = onDismiss) { Text("Close", color = t.inkSoft, style = MaterialTheme.typography.labelLarge) }
            }
        },
    )
}

private fun Modifier.padding4() = this.then(Modifier.padding(vertical = 4.dp))

@Composable
private fun SaveSetDialog(count: Int, onSave: (String, String) -> Unit, onDismiss: () -> Unit) {
    val t = LocalTokens.current
    var name by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = t.surface, titleContentColor = t.ink, textContentColor = t.ink,
        title = { Text("Save $count tokens as a set", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, placeholder = { Text("Set name, e.g. Acme app") })
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = tag, onValueChange = { tag = it }, singleLine = true, placeholder = { Text("Type of app, e.g. Fintech, restrained") })
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, tag) }) { Text("Save", color = t.ink, style = MaterialTheme.typography.labelLarge) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = t.inkSoft, style = MaterialTheme.typography.labelLarge) } },
    )
}

/** Paste generated code back in: is it still what Hapture exported, and do its springs still match the app? */
@Composable
private fun CheckCodeDialog(current: List<ExportStamp.Spring>, onDismiss: () -> Unit) {
    val t = LocalTokens.current
    val clipboard = LocalClipboardManager.current
    var text by remember { mutableStateOf("") }
    var verdict by remember { mutableStateOf<ExportStamp.Verdict?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = t.surface, titleContentColor = t.ink, textContentColor = t.ink,
        title = { Text("Check shipped code", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Paste the exported block, from its first hapture comment to the last. " +
                        "A formatter that rewrites lines counts as an edit.",
                    style = MaterialTheme.typography.bodySmall, color = t.inkSoft,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text, onValueChange = { text = it; verdict = null },
                    minLines = 4, maxLines = 8, modifier = Modifier.fillMaxWidth().testTag("checkInput"),
                    placeholder = { Text("// hapture:v1 ...") },
                )
                TextLink("Paste from clipboard", icon = IconKind.COPY, onClick = { text = clipboard.getText()?.text.orEmpty(); verdict = null })
                verdict?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(describe(it), style = MaterialTheme.typography.bodyMedium, color = t.ink, modifier = Modifier.testTag("checkVerdict"))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { verdict = ExportStamp.check(text, current) }) { Text("Check", color = t.ink, style = MaterialTheme.typography.labelLarge) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close", color = t.inkSoft, style = MaterialTheme.typography.labelLarge) } },
    )
}

internal fun describe(v: ExportStamp.Verdict): String = when (v) {
    ExportStamp.Verdict.NoStamp -> "No Hapture stamp found. Paste the whole generated block, including its first and last comment lines."
    is ExportStamp.Verdict.Edited -> "Edited since export. The code between the markers no longer matches what Hapture generated " +
        "(${v.springs.joinToString(", ") { it.name }}). Someone changed it by hand, or a formatter did."
    is ExportStamp.Verdict.Outdated -> "Untouched, but out of date: " + v.changes.joinToString("; ") {
        "${it.name} was ${it.shipped.stiffness.toInt()} / ${"%.2f".format(it.shipped.dampingRatio)} when exported and is ${it.now.stiffness.toInt()} / ${"%.2f".format(it.now.dampingRatio)} now"
    } + "."
    is ExportStamp.Verdict.Current -> "Matches: unchanged since export, and ${v.springs.joinToString(", ") { it.name }} still ${if (v.springs.size == 1) "has" else "have"} these values." +
        if (v.missing.isNotEmpty()) " Not in this app any more: ${v.missing.joinToString(", ")}." else ""
}
