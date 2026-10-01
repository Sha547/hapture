package com.klynstudios.hapture.core.haptics

/**
 * The moments in an interaction that can carry a haptic: press, release,
 * crossing a threshold, snapping into place, an impact, a success.
 */
sealed class HapticEvent {
    companion object {
        /** Every event with its spec name, in a stable order. */
        val all: List<Pair<String, HapticEvent>> by lazy {
            listOf(
                "press" to Press, "release" to Release, "threshold" to Threshold,
                "snap" to Snap, "impact" to Impact, "success" to Success,
            )
        }
    }

    data object Press : HapticEvent()
    data object Release : HapticEvent()
    data object Threshold : HapticEvent()
    data object Snap : HapticEvent()
    data object Impact : HapticEvent()
    data object Success : HapticEvent()
}
