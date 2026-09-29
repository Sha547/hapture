package com.motionlab.app.feature.common

import androidx.compose.runtime.compositionLocalOf
import com.motionlab.app.data.MotionTokenEntity

/** The saved springs, available to every editor without threading a repository through each screen. */
val LocalMotionTokens = compositionLocalOf<List<MotionTokenEntity>> { emptyList() }

/** Saves the current spring under a name; a no-op outside the real app (previews, tests). */
val LocalSaveMotionToken = compositionLocalOf<(name: String, stiffness: Float, dampingRatio: Float) -> Unit> { { _, _, _ -> } }

/**
 * Opens Compare with the given spring already in lane A (and Back returning to the editor that asked).
 * Null outside the real app, and then editors hide their "Compare with" link.
 */
val LocalOpenCompare = compositionLocalOf<((name: String, spring: com.motionlab.app.core.spec.SpringSpec) -> Unit)?> { null }
