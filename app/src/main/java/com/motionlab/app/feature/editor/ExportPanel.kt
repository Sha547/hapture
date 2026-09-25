package com.motionlab.app.feature.editor

import android.content.Intent
import androidx.compose.foundation.layout.Column
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
import com.motionlab.app.core.sync.LiveSync
import com.motionlab.app.export.ExportBundle
import com.motionlab.app.export.ExportStamp
import com.motionlab.app.core.spec.LintIssue
import com.motionlab.app.core.spec.MotionLint
import com.motionlab.app.core.spec.MotionSpec
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import com.motionlab.app.feature.common.LocalSaveMotionToken
import com.motionlab.app.export.platform.ComposeGenericGenerator
import com.motionlab.app.export.platform.FlutterGenerator
import com.motionlab.app.export.platform.LottieGenerator
import com.motionlab.app.export.platform.ReactNativeGenerator
import com.motionlab.app.export.platform.SwiftUiGenerator
import com.motionlab.app.export.platform.WebGenerator
import com.motionlab.app.ui.design.AppIcon
import com.motionlab.app.ui.design.IconKind
import com.motionlab.app.ui.design.ListRow
import com.motionlab.app.ui.design.LocalTokens
import com.motionlab.app.ui.design.SectionLabel
import kotlinx.coroutines.delay

/**
 * Every way out of the app, one row each. Generators are lambdas so nothing is
 * built until a row is tapped.
 *
 * - Checks: what a motion reviewer would flag (see [MotionLint])
 * - Compose / Design spec / CSS: the original three
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

    fun current(): MotionSpec? = motion?.invoke() ?: MotionSpec.fromDesignJson(spec())

    // While live sync is on, every change to the sliders is pushed to connected clients.
    LaunchedEffect(LiveSync.running) {
        if (LiveSync.running) snapshotFlow { current()?.toJson() ?: "" }.collect {
            LiveSync.publish(it, ExportBundle.json(compose(), spec(), css(), current()))
        }
    }
    val issues = current()?.let { MotionLint.check(it) }.orEmpty()

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
        Text(
            if (issues.isEmpty()) "Checks: no issues found." else "Checks",
            style = MaterialTheme.typography.bodySmall,
            color = t.inkSoft,
        )
        issues.forEach {
            Text(
                (if (it.level == LintIssue.Level.WARN) "Warning: " else "Note: ") + it.message,
                style = MaterialTheme.typography.bodySmall,
                color = t.ink,
            )
        }
        Spacer(Modifier.height(10.dp))

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
