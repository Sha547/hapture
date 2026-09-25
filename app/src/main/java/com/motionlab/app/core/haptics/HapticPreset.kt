package com.motionlab.app.core.haptics

/** The physical sensations available (spec §24), lightest to heaviest, plus the two-beat success. */
enum class HapticEffect { SOFT, TICK, CLICK, IMPACT, HEAVY, SUCCESS }

/**
 * A feel for the whole interaction: which sensation each [HapticEvent] gets.
 * Haptics are tied to interaction state (spec §17), so a preset is a table from
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
