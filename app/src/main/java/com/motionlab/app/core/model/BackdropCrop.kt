package com.motionlab.app.core.model

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Which part of a screenshot the stage shows. The stage is a wide, short window and a phone
 * screenshot is tall, so the image is scaled to cover the window's width and the window slides
 * up and down it: [focusY] 0 shows the top of the screenshot, 1 the bottom.
 */
object BackdropCrop {

    data class Window(val x: Int, val y: Int, val width: Int, val height: Int)

    fun window(imageW: Int, imageH: Int, stageW: Int, stageH: Int, focusY: Float): Window {
        if (imageW <= 0 || imageH <= 0 || stageW <= 0 || stageH <= 0) return Window(0, 0, max(imageW, 1), max(imageH, 1))
        val scale = max(stageW.toFloat() / imageW, stageH.toFloat() / imageH)
        val w = min(imageW, (stageW / scale).roundToInt())
        val h = min(imageH, (stageH / scale).roundToInt())
        return Window((imageW - w) / 2, ((imageH - h) * focusY.coerceIn(0f, 1f)).roundToInt(), w, h)
    }
}
