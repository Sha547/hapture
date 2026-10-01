package com.klynstudios.hapture.core.haptics

/** The physical sensations available, lightest to heaviest, plus the two-beat success. */
enum class HapticEffect { SOFT, TICK, CLICK, IMPACT, HEAVY, SUCCESS }

/** How faithfully this phone can play an effect, worst first (so the weakest of several is the minimum). */
enum class HapticSupport(val note: String) {
    NONE("This phone has no vibration motor, so you won't feel it here. It's still saved and exported."),
    APPROXIMATE("This phone can't play these exact effects; you'll feel a plainer buzz. Phones with better motors will feel the real thing."),
    UNKNOWN("This phone doesn't report which effects it supports, so what you feel may be a plain buzz."),
    EXACT("This phone plays this feel as designed."),
}

/**
 * A feel for the whole interaction: which sensation each [HapticEvent] gets.
 * Haptics are tied to interaction state, so a preset is a table from
 * event to effect, not a single buzz. [CRISP] is what the app has always done.
 */
enum class HapticPreset(val label: String, val description: String) {
    OFF("Off", "Silent."),
    SOFT("Soft", "Feather-light pulses that stay out of the way."),
    CRISP("Crisp", "Light ticks, with a click on the moment that matters."),
    FIRM("Firm", "Solid clicks and a heavy hit at the end.");

    /** null means silent. */
    fun effectFor(event: HapticEvent): HapticEffect? = when (this) {
        OFF -> null
        SOFT -> when (event) {
            HapticEvent.Press, HapticEvent.Release, HapticEvent.Threshold -> HapticEffect.SOFT
            HapticEvent.Snap, HapticEvent.Success -> HapticEffect.TICK
            HapticEvent.Impact -> HapticEffect.CLICK
        }
        CRISP -> when (event) {
            HapticEvent.Press, HapticEvent.Release, HapticEvent.Threshold -> HapticEffect.TICK
            HapticEvent.Snap -> HapticEffect.CLICK
            HapticEvent.Impact -> HapticEffect.IMPACT
            HapticEvent.Success -> HapticEffect.SUCCESS
        }
        FIRM -> when (event) {
            HapticEvent.Press, HapticEvent.Release, HapticEvent.Threshold -> HapticEffect.CLICK
            HapticEvent.Snap -> HapticEffect.IMPACT
            HapticEvent.Impact, HapticEvent.Success -> HapticEffect.HEAVY
        }
    }

    /** The whole table as (event, effect) names, for the design spec. Silent events read "none". */
    fun table(): List<Pair<String, String>> = HapticEvent.all.map { (name, event) ->
        name to (effectFor(event)?.name?.lowercase() ?: "none")
    }
}
