package com.klynstudios.hapture.export

/**
 * The imports the gesture snippets need, emitted at the top of each so a paste compiles in a fresh file.
 * Wildcards on purpose: the snippet is meant to be pasted into a file that has its own imports, and a
 * wildcard never duplicates or omits one the snippet uses. Interpolate into a `trimMargin` template.
 */
internal object ComposeImports {
    private val LINES = listOf(
        "import androidx.compose.animation.core.*",
        "import androidx.compose.foundation.*",
        "import androidx.compose.foundation.gestures.*",
        "import androidx.compose.foundation.layout.*",
        "import androidx.compose.foundation.shape.*",
        "import androidx.compose.runtime.*",
        "import androidx.compose.ui.*",
        "import androidx.compose.ui.draw.*",
        "import androidx.compose.ui.geometry.*",
        "import androidx.compose.ui.graphics.*",
        "import androidx.compose.ui.input.pointer.*",
        "import androidx.compose.ui.input.pointer.util.VelocityTracker",
        "import androidx.compose.ui.layout.*",
        "import androidx.compose.ui.platform.*",
        "import androidx.compose.ui.unit.*",
        "import kotlinx.coroutines.*",
        "import kotlin.math.roundToInt",
    )

    /** Ready to drop after a `|` in a trimMargin template: every line after the first carries its own margin. */
    val GESTURE: String = LINES.joinToString("\n|")
}
