package com.klynstudios.hapture.feature.editor

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import com.klynstudios.hapture.core.haptics.HapticPreset
import com.klynstudios.hapture.core.physics.ParameterMapping
import com.klynstudios.hapture.core.spec.LintFix
import com.klynstudios.hapture.ui.design.TextLink
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.snapshotFlow
import com.klynstudios.hapture.core.sync.LiveSync
import com.klynstudios.hapture.ui.design.QrImage
import com.klynstudios.hapture.export.ExportBundle
import com.klynstudios.hapture.export.ExportStamp
import com.klynstudios.hapture.core.spec.LintIssue
import com.klynstudios.hapture.core.spec.MotionLint
import com.klynstudios.hapture.core.spec.MotionSpec
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import com.klynstudios.hapture.feature.common.LocalSaveMotionToken
import com.klynstudios.hapture.export.platform.ComposeGenericGenerator
import com.klynstudios.hapture.export.platform.FlutterGenerator
import com.klynstudios.hapture.export.platform.LottieGenerator
import com.klynstudios.hapture.export.platform.ReactNativeGenerator
import com.klynstudios.hapture.export.platform.SwiftUiGenerator
import com.klynstudios.hapture.export.platform.WebGenerator
import com.klynstudios.hapture.ui.design.AppIcon
import com.klynstudios.hapture.ui.design.IconKind
import com.klynstudios.hapture.ui.design.ListRow
import com.klynstudios.hapture.ui.design.LocalTokens
import com.klynstudios.hapture.ui.design.SectionLabel
import kotlinx.coroutines.delay

/**
 * Every way out of the app, one row each. Generators are lambdas so nothing is
 * built until a row is tapped.
 *
 * - Checks: what a motion reviewer would flag (see [MotionLint])
 * - Compose, design spec (Figma numbers), CSS easing
 * - Other platforms: SwiftUI, Flutter, React Native, Web, Lottie and the
 *   neutral spec, all generated from one [MotionSpec]. Gesture editors don't
 *   pass [motion]; it is derived from the same design JSON they already build.
 */
@Composable
internal fun ExportPanel(
    compose: () -> String,
    spec: () -> String,
    css: () -> String,
    motion: (() -> MotionSpec)? = null,
    /** Sets the editor's main spring, in slider positions. Given, checks that a spring change clears get a Fix button. */
    onApplySpring: ((stiffnessT: Float, dampingT: Float) -> Unit)? = null,
    /** Sets the editor's haptic preset, for the "haptics are off" check. */
    onApplyHaptic: ((HapticPreset) -> Unit)? = null,
) {
    val t = LocalTokens.current
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var copied by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(copied) {
        if (copied != null) {
            delay(1600)
            copied = null
        }
    }

    val saveToken = LocalSaveMotionToken.current
    var naming by remember { mutableStateOf(false) }
    var tokenName by remember { mutableStateOf("") }
    var qrLink by remember { mutableStateOf<String?>(null) }

    fun current(): MotionSpec? = motion?.invoke() ?: MotionSpec.fromDesignJson(spec())

    // While live sync is on, every change to the sliders is pushed to connected clients.
    LaunchedEffect(LiveSync.running) {
        if (LiveSync.running) snapshotFlow { current()?.toJson() ?: "" }.collect {
            LiveSync.publish(it, ExportBundle.json(compose(), spec(), css(), current()))
        }
    }
    val issues = current()?.let { MotionLint.check(it) }.orEmpty()

    // The last fix applied, with how to take it back. Clears itself after a while, like the Home undo bar.
    var undo by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var noFix by remember { mutableStateOf<LintIssue.Rule?>(null) }
    LaunchedEffect(undo) {
        if (undo != null) {
            delay(10_000)
            undo = null
        }
    }

    fun canFix(issue: LintIssue): Boolean = issue.fixLabel != null && when (issue.rule) {
        LintIssue.Rule.NO_HAPTICS -> onApplyHaptic != null
        null, LintIssue.Rule.STAGGER -> false
        else -> onApplySpring != null
    }

    fun applyFix(issue: LintIssue) {
        val spec = current() ?: return
        val rule = issue.rule ?: return
        when (val fix = MotionLint.fix(spec, rule)) {
            is LintFix.Spring -> onApplySpring?.let { apply ->
                val before = ParameterMapping.stiffnessT(spec.spring.stiffness) to ParameterMapping.dampingT(spec.spring.dampingRatio)
                apply(ParameterMapping.stiffnessT(fix.stiffness), ParameterMapping.dampingT(fix.dampingRatio))
                undo = issue.fixLabel.orEmpty() to { apply(before.first, before.second) }
            }
            is LintFix.Haptic -> onApplyHaptic?.let { apply ->
                val before = HapticPreset.entries.firstOrNull { it.name.lowercase() == spec.haptic } ?: HapticPreset.OFF
                apply(HapticPreset.entries.firstOrNull { it.name.lowercase() == fix.preset } ?: HapticPreset.CRISP)
                undo = issue.fixLabel.orEmpty() to { apply(before) }
            }
            null -> noFix = rule
        }
    }

    @Composable
    fun copyRow(id: String, title: String, subtitle: String, text: () -> String) {
        ListRow(
            title = title,
            subtitle = subtitle,
            onClick = {
                clipboard.setText(AnnotatedString(text()))
                copied = id
            },
            trailing = {
                if (copied == id) {
                    Text("Copied", style = MaterialTheme.typography.bodySmall, color = t.inkSoft)
                } else {
                    AppIcon(IconKind.COPY, tint = t.inkSoft, size = 18.dp)
                }
            },
        )
    }

    // Code exports carry a stamp so pasted-back code can be checked for hand edits (see ExportStamp).
    fun stamped(style: ExportStamp.Style, code: String): String = current()?.let {
        ExportStamp.wrap(code, style, listOf(ExportStamp.Spring("x", it.name, it.spring.stiffness, it.spring.dampingRatio)))
    } ?: code

    @Composable
    fun platformRow(id: String, title: String, subtitle: String, gen: (MotionSpec) -> String, stamp: Boolean = true) {
        copyRow(id, title, subtitle) { current()?.let { m -> gen(m).let { if (stamp) stamped(ExportStamp.Style.SLASH, it) else it } } ?: "" }
    }

    Column {
        SectionLabel("Checks", trailing = if (issues.isEmpty()) "all clear" else "${issues.size}")
        Spacer(Modifier.height(12.dp))
        if (issues.isEmpty() && undo == null) {
            Text("Nothing a motion reviewer would flag.", style = MaterialTheme.typography.bodySmall, color = t.inkSoft)
        }
        issues.forEach { issue ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag("lint_${issue.rule?.name?.lowercase()}"), verticalAlignment = Alignment.Top) {
                Box(
                    Modifier
                        .padding(top = 6.dp)
                        .size(6.dp)
                        .background(if (issue.level == LintIssue.Level.WARN) t.ink else t.inkFaint, CircleShape),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(issue.message, style = MaterialTheme.typography.bodySmall, color = if (issue.level == LintIssue.Level.WARN) t.ink else t.inkSoft)
                    if (noFix == issue.rule) {
                        Text("No spring in range fixes this; try the sliders.", style = MaterialTheme.typography.bodySmall, color = t.inkFaint)
                    } else if (canFix(issue)) {
                        TextLink(
                            label = issue.fixLabel.orEmpty(),
                            icon = IconKind.CHECK,
                            onClick = { applyFix(issue) },
                            modifier = Modifier.testTag("fix_${issue.rule?.name?.lowercase()}"),
                        )
                    }
                }
            }
        }
        undo?.let { (label, revert) ->
            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Applied \u201c$label\u201d", style = MaterialTheme.typography.bodySmall, color = t.inkSoft)
                Spacer(Modifier.width(12.dp))
                Text(
                    "Undo",
                    style = MaterialTheme.typography.labelMedium,
                    color = t.ink,
                    modifier = Modifier
                        .testTag("undoFix")
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { revert(); undo = null }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
        }
        Spacer(Modifier.height(28.dp))
        SectionLabel("Code")

        copyRow("compose", "Compose code", "Kotlin spring() and gesture handling") { stamped(ExportStamp.Style.SLASH, compose()) }
        copyRow("spec", "Design spec", "JSON: Figma custom spring, CSS, object, theme", spec)
        copyRow("css", "CSS easing", "linear() curve and duration for the web") { stamped(ExportStamp.Style.BLOCK, css()) }
        ListRow(
            title = "Share spec",
            subtitle = "Send the JSON to another app",
            onClick = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_TEXT, spec())
                }
                context.startActivity(Intent.createChooser(send, "Share spec"))
            },
            trailing = { AppIcon(IconKind.SHARE, tint = t.inkSoft, size = 18.dp) },
        )

        Spacer(Modifier.height(28.dp))
        SectionLabel("Other platforms")
        platformRow("swift", "SwiftUI", "interpolatingSpring, Reduce Motion aware", SwiftUiGenerator::generate)
        platformRow("flutter", "Flutter", "SpringDescription and SpringSimulation", FlutterGenerator::generate)
        platformRow("rn", "React Native", "Reanimated withSpring", ReactNativeGenerator::generate)
        platformRow("web", "Web", "Framer Motion, plus CSS linear()", WebGenerator::generate)
        platformRow("lottie", "Lottie", "A .json animation baked from the spring", LottieGenerator::generate, stamp = false)
        platformRow("compose2", "Compose (spring only)", "The neutral spec as a Compose snippet", ComposeGenericGenerator::generate)
        platformRow("neutral", "Motion spec", "Platform-neutral JSON every target above is built from", MotionSpec::toJson, stamp = false)

        Spacer(Modifier.height(28.dp))
        SectionLabel("Live sync")
        val host = LiveSync.addresses().firstOrNull() ?: "localhost"
        ListRow(
            title = if (LiveSync.running) "Live sync is on" else "Start live sync",
            subtitle = if (LiveSync.running) "Pairing code ${LiveSync.code}  \u00b7  http://$host:${LiveSync.port}" else "Serve this spring to the browser bridge, the Figma plugin or a dev build",
            onClick = { if (LiveSync.running) LiveSync.stop() else LiveSync.start(context) },
            trailing = { Text(if (LiveSync.running) "Stop" else "Start", style = MaterialTheme.typography.bodySmall, color = t.inkSoft) },
        )
        if (LiveSync.running) {
            ListRow(
                title = "Show QR code",
                subtitle = "Scan it on a laptop or phone on this Wi-Fi to open the bridge already paired",
                onClick = { qrLink = LiveSync.bridgeLink(host) },
                trailing = { AppIcon(IconKind.SHARE, tint = t.inkSoft, size = 18.dp) },
                modifier = Modifier.testTag("showQr"),
            )
            copyRow("bridge", "Copy bridge link", "Opens the browser bridge already paired; works on this Wi-Fi for 30 minutes") { LiveSync.bridgeLink(host) }
            copyRow("client", "Client snippet", "JavaScript, Kotlin and Swift code that follows the stream") { LiveSync.clientSnippet(host) }
        }

        Spacer(Modifier.height(28.dp))
        SectionLabel("Motion token")
        ListRow(
            title = if (copied == "token") "Saved" else "Save as motion token",
            subtitle = "Name this spring; reuse it in any editor and export the set",
            onClick = {
                tokenName = ""
                naming = true
            },
            trailing = { AppIcon(IconKind.PLUS, tint = t.inkSoft, size = 18.dp) },
        )
    }

    qrLink?.let { link ->
        AlertDialog(
            onDismissRequest = { qrLink = null },
            containerColor = t.surface, titleContentColor = t.ink, textContentColor = t.ink,
            title = { Text("Scan to open the bridge", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    QrImage(link, Modifier.testTag("qrImage"))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Same Wi-Fi only. Anyone who scans this can read the spec until sync stops or 30 minutes pass.",
                        style = MaterialTheme.typography.bodySmall, color = t.inkSoft,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { qrLink = null }) { Text("Done", color = t.ink, style = MaterialTheme.typography.labelLarge) } },
        )
    }

    if (naming) {
        AlertDialog(
            onDismissRequest = { naming = false },
            containerColor = t.surface,
            titleContentColor = t.ink,
            textContentColor = t.ink,
            title = { Text("Name this spring", style = MaterialTheme.typography.titleLarge) },
            text = {
                OutlinedTextField(value = tokenName, onValueChange = { tokenName = it }, singleLine = true, placeholder = { Text("e.g. snappy") })
            },
            confirmButton = {
                TextButton(onClick = {
                    current()?.let { saveToken(tokenName, it.spring.stiffness, it.spring.dampingRatio) }
                    copied = "token"
                    naming = false
                }) { Text("Save", color = t.ink, style = MaterialTheme.typography.labelLarge) }
            },
            dismissButton = {
                TextButton(onClick = { naming = false }) { Text("Cancel", color = t.inkSoft, style = MaterialTheme.typography.labelLarge) }
            },
        )
    }
}
