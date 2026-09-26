package com.motionlab.app.feature.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.motionlab.app.data.BackdropRepository
import com.motionlab.app.ui.design.Backdrop
import com.motionlab.app.ui.design.Chip
import com.motionlab.app.ui.design.LocalBackdrop
import com.motionlab.app.ui.design.LocalBackdropAction
import com.motionlab.app.ui.design.LocalBackdropActive
import com.motionlab.app.ui.design.LocalTokens
import com.motionlab.app.ui.design.ValueSlider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Wraps an experiment's editor so its stage can show a screenshot of the person's own app behind the
 * moving object. Every editor draws through the shared Stage, so none of them changes: this loads the
 * image, provides it, and the stage shows one small button for it in its own corner.
 */
@Composable
fun BackdropHost(experimentId: Long, repository: BackdropRepository, content: @Composable () -> Unit) {
    val t = LocalTokens.current
    val scope = rememberCoroutineScope()
    val row by repository.observe(experimentId).collectAsState(initial = null)
    val image by produceState<ImageBitmap?>(null, row?.path) {
        value = row?.path?.let { path -> withContext(Dispatchers.IO) { repository.load(path)?.asImageBitmap() } }
    }
    var focus by remember(experimentId) { mutableFloatStateOf(0.35f) }
    LaunchedEffect(row?.focusY) { row?.focusY?.let { focus = it } }
    var open by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { message = if (repository.set(experimentId, uri)) null else "That isn't an image Motion Lab can read." }
    }
    val backdrop = image?.let { Backdrop(it, focus) }

    CompositionLocalProvider(
        LocalBackdrop provides backdrop,
        LocalBackdropAction provides { open = true },
        LocalBackdropActive provides (backdrop != null),
    ) {
        content()
    }

    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            containerColor = t.surface, titleContentColor = t.ink, textContentColor = t.ink,
            title = { Text("Preview on your app", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column {
                    Text(
                        "Pick a screenshot of your real UI and the object moves over it, so you judge the feel in context. " +
                            "The image stays on this phone and isn't part of the exported spec.",
                        style = MaterialTheme.typography.bodyMedium, color = t.inkSoft,
                    )
                    if (backdrop != null) {
                        Spacer(Modifier.height(14.dp))
                        ValueSlider("Position", focus, { focus = it }, { scope.launch { repository.setFocus(experimentId, focus) } })
                    }
                    message?.let { Spacer(Modifier.height(10.dp)); Text(it, style = MaterialTheme.typography.bodySmall, color = t.ink) }
                }
            },
            confirmButton = {
                TextButton(onClick = { picker.launch(arrayOf("image/*")) }, modifier = Modifier.testTag("backdropPick")) {
                    Text(if (backdrop == null) "Choose a screenshot" else "Choose another", color = t.ink, style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                Column {
                    if (backdrop != null) TextButton(onClick = { scope.launch { repository.clear(experimentId) }; open = false }) {
                        Text("Remove", color = t.inkSoft, style = MaterialTheme.typography.labelLarge)
                    }
                    TextButton(onClick = { open = false }) { Text("Close", color = t.inkSoft, style = MaterialTheme.typography.labelLarge) }
                }
            },
        )
    }
}
