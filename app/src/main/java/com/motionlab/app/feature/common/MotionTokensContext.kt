package com.motionlab.app.feature.common

import androidx.compose.runtime.compositionLocalOf
import com.motionlab.app.data.MotionTokenEntity

/** The saved springs, available to every editor without threading a repository through each screen. */
val LocalMotionTokens = compositionLocalOf<List<MotionTokenEntity>> { emptyList() }

/** Saves the current spring under a name; a no-op outside the real app (previews, tests). */
val LocalSaveMotionToken = compositionLocalOf<(name: String, stiffness: Float, dampingRatio: Float) -> Unit> { { _, _, _ -> } }
