package com.klynstudios.hapture.feature.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.klynstudios.hapture.ui.design.SectionLabel
import com.klynstudios.hapture.ui.design.reveal

/** A titled block of controls. [index] staggers its entrance. */
@Composable
internal fun EditorSection(
    title: String,
    index: Int,
    trailing: String? = null,
    gap: Dp = 16.dp,
    content: @Composable () -> Unit,
) {
    Column(Modifier.reveal(index)) {
        SectionLabel(title, trailing = trailing)
        Spacer(Modifier.height(gap))
        content()
        Spacer(Modifier.height(40.dp))
    }
}
