package com.klynstudios.hapture.core.spec

import com.klynstudios.hapture.core.physics.ParameterMapping
import kotlin.math.roundToInt

/** The interactions decided by a discrete trigger (a tap, a press) rather than a continuous drag. */
enum class TriggerKind(val interaction: String, val trigger: String, val amountLabel: String) {
    TOGGLE("toggle", "tap", "Thumb stretch"),
    BUTTON_PRESS("button_press", "press", "Press depth"),
    TAB_INDICATOR("tab_indicator", "tap", "Indicator stretch"),
    STAGGER_LIST("stagger_list", "appear", "Rise distance"),
    LIKE_BURST("like_burst", "tap", "Burst size"),
}

/** A follower property that starts once the first transition has done part of its move (slider positions, 0..1). */
data class Chain(val property: String, val atT: Float, val lagT: Float, val stiffnessT: Float, val dampingT: Float)

object TriggerSpecs {
    /** Properties a follower can animate, with the from/to it settles between (from is where it starts, to is rest). */
    val FOLLOWERS: List<Triple<String, Float, Float>> = listOf(
        Triple("opacity", 0.4f, 1f), Triple("scale", 0.9f, 1f), Triple("offsetY", 8f, 0f), Triple("rotation", -6f, 0f), Triple("progress", 0f, 1f),
    )
    fun chainAtProgress(t: Float) = 0.1f + 0.8f * t.coerceIn(0f, 1f)
    fun chainLagMs(t: Float) = (300f * t.coerceIn(0f, 1f)).roundToInt()

    const val TOGGLE_TRAVEL_DP = 22f
    const val TAB_WIDTH_DP = 96f
    const val LIST_ITEMS = 4
    const val PARTICLES = 8

    fun pressScale(t: Float) = 0.98f - 0.15f * t.coerceIn(0f, 1f)
    fun thumbStretch(t: Float) = 0.5f * t.coerceIn(0f, 1f)
    fun staggerMs(t: Float) = 20f + 100f * t.coerceIn(0f, 1f)

    /** The slider position whose stagger is closest to [ms] (clamped to what the slider can express). */
    fun staggerT(ms: Float) = ((ms - 20f) / 100f).coerceIn(0f, 1f)
    fun riseDp(t: Float) = 8f + 32f * t.coerceIn(0f, 1f)
    fun burstRadiusDp(t: Float) = 24f + 32f * t.coerceIn(0f, 1f)

    fun build(
        kind: TriggerKind,
        name: String,
        stiffnessT: Float,
        dampingT: Float,
        amountT: Float,
        staggerT: Float,
        haptic: String,
        chain: Chain? = null,
    ): MotionSpec {
        val spring = SpringSpec(ParameterMapping.stiffness(stiffnessT), ParameterMapping.dampingRatio(dampingT))
        fun tr(property: String, from: Float, to: Float) = MotionTransition(property, from, to, 0, spring)
        val (transitions, params) = when (kind) {
            TriggerKind.TOGGLE -> listOf(tr("offsetX", 0f, TOGGLE_TRAVEL_DP), tr("progress", 0f, 1f)) to
                mapOf("thumbStretch" to thumbStretch(amountT))
            TriggerKind.BUTTON_PRESS -> listOf(tr("scale", 1f, pressScale(amountT))) to
                mapOf("pressDepth" to 1f - pressScale(amountT))
            TriggerKind.TAB_INDICATOR -> listOf(tr("offsetX", 0f, TAB_WIDTH_DP)) to mapOf("tabWidthDp" to TAB_WIDTH_DP)
            TriggerKind.STAGGER_LIST -> listOf(tr("opacity", 0f, 1f), tr("offsetY", riseDp(amountT), 0f)) to
                mapOf("staggerMs" to staggerMs(staggerT), "itemCount" to LIST_ITEMS.toFloat())
            TriggerKind.LIKE_BURST -> listOf(tr("scale", 0.6f, 1f)) to
                mapOf("burstRadiusDp" to burstRadiusDp(amountT), "particleCount" to PARTICLES.toFloat())
        }
        // A follower can't animate a property the leader already owns.
        val follower = chain?.let { c -> FOLLOWERS.firstOrNull { it.first == c.property && transitions.none { t -> t.property == c.property } }?.let { c to it } }
        val all = if (follower == null) transitions else transitions + MotionTransition(
            follower.second.first, follower.second.second, follower.second.third, 0,
            SpringSpec(ParameterMapping.stiffness(follower.first.stiffnessT), ParameterMapping.dampingRatio(follower.first.dampingT)),
            Follow(0, chainAtProgress(follower.first.atT), chainLagMs(follower.first.lagT)),
        )
        return MotionSpec(name, kind.interaction, kind.trigger, spring, all, params, haptic)
    }
}
