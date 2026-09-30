package com.klynstudios.hapture.ui.design

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap

/** A screenshot of the person's real app that [Stage] paints behind the moving object; [focusY] is which part of it shows. */
class Backdrop(val image: ImageBitmap, val focusY: Float)

/** Provided around an experiment's editor when it has a screenshot; null everywhere else. */
val LocalBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/** Set by the editor host to open the screenshot picker; [Stage] shows a small button for it in its own corner, so it scrolls with the page. Null elsewhere. */
val LocalBackdropAction = staticCompositionLocalOf<(() -> Unit)?> { null }

/** Whether a screenshot is currently showing, so the stage's button can say so. */
val LocalBackdropActive = staticCompositionLocalOf { false }
