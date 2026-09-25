package com.motionlab.app.ui.design

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap

/** A screenshot of the person's real app that [Stage] paints behind the moving object; [focusY] is which part of it shows. */
class Backdrop(val image: ImageBitmap, val focusY: Float)

/** Provided around an experiment's editor when it has a screenshot; null everywhere else. */
val LocalBackdrop = staticCompositionLocalOf<Backdrop?> { null }
