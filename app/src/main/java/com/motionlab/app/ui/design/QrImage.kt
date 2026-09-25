package com.motionlab.app.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.motionlab.app.core.qr.QrCode

/** A QR code for [text]. Always black on white with a quiet zone, whatever the theme, because scanners need the contrast. */
@Composable
fun QrImage(text: String, modifier: Modifier = Modifier, size: Dp = 240.dp) {
    val code = remember(text) { QrCode.encode(text) }
    Canvas(modifier.size(size).background(Color.White)) {
        val c = code ?: return@Canvas
        val quiet = 4
        val cell = this.size.minDimension / (c.size + 2 * quiet)
        for (y in 0 until c.size) for (x in 0 until c.size) {
            if (c.dark[y][x]) drawRect(Color.Black, Offset((x + quiet) * cell, (y + quiet) * cell), Size(cell + 0.5f, cell + 0.5f))
        }
    }
}
