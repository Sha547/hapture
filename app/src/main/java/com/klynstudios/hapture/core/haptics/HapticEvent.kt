package com.klynstudios.hapture.core.haptics

/**
 * Haptics are events tied to interaction state, not a raw on/off toggle
 * (spec §17). Only the events Phase 1 actually triggers are implemented;
 * the rest of the sealed hierarchy from the spec is stubbed in for the
 * types the editor will grow into.
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
