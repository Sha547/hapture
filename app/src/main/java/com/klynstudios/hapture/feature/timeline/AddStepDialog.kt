package com.klynstudios.hapture.feature.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.klynstudios.hapture.data.ExperimentEntity
import com.klynstudios.hapture.data.nominalDurationMs
import com.klynstudios.hapture.feature.common.icon
import com.klynstudios.hapture.feature.common.label
import com.klynstudios.hapture.ui.design.Hairline
import com.klynstudios.hapture.ui.design.ListRow
import com.klynstudios.hapture.ui.design.LocalTokens
import com.klynstudios.hapture.ui.design.TextLink

/** Full-screen picker: which saved experiment becomes the next step. The list scrolls; the header and Cancel don't. */
@Composable
fun AddStepDialog(
    experiments: List<ExperimentEntity>,
    onPick: (ExperimentEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    val t = LocalTokens.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // An opaque page, like the shape drawer: without it the timeline behind shows through the list.
        Column(Modifier.fillMaxSize().background(t.canvas).navigationBarsPadding().padding(24.dp)) {
            Text("Add a step", style = MaterialTheme.typography.titleLarge, color = t.ink)
            Spacer(Modifier.height(8.dp))
            Text(
                "Pick a saved experiment; it plays with its own tuning.",
                style = MaterialTheme.typography.bodyMedium,
                color = t.inkSoft,
            )
            Spacer(Modifier.height(20.dp))

            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                if (experiments.isEmpty()) {
                    Text("No saved experiments yet.", style = MaterialTheme.typography.bodyMedium, color = t.inkSoft)
                } else {
                    Hairline()
                    experiments.forEach { e ->
                        ListRow(
                            title = e.name,
                            subtitle = "${e.type.label}  /  ${e.nominalDurationMs} ms",
                            leading = e.type.icon,
                            onClick = { onPick(e) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            TextLink(label = "Cancel", onClick = onDismiss)
        }
    }
}
