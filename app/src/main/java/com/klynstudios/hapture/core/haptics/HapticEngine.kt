package com.klynstudios.hapture.core.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Device -> capability detection -> best supported effect (spec §19).
 * Never lets missing hardware make the app feel broken: every branch below
 * degrades to *something* rather than silently doing nothing, down to a
 * plain 1.13.0+ compatible legacy vibrate() as the last resort.
 */
class HapticEngine(context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private val hasHardware: Boolean = vibrator?.hasVibrator() == true

    /** Richer effect on capable hardware, simplified fallback otherwise. */
    private val supportsPredefinedEffects: Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    private val supportsAmplitudeControl: Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && vibrator?.hasAmplitudeControl() == true

    /**
     * How faithfully this phone plays [effect]. Predefined effects are asked about directly on
     * Android 11+ (`areEffectsSupported`); before that the answer isn't knowable. The two custom
     * one-shots (soft, heavy) only feel different from a plain buzz with amplitude control.
     */
    fun support(effect: HapticEffect): HapticSupport {
        val v = vibrator
        if (!hasHardware || v == null) return HapticSupport.NONE
        val predefined = when (effect) {
            HapticEffect.SOFT -> if (supportsAmplitudeControl) return HapticSupport.EXACT else VibrationEffect.EFFECT_TICK
            HapticEffect.HEAVY -> if (supportsAmplitudeControl) return HapticSupport.EXACT else VibrationEffect.EFFECT_HEAVY_CLICK
            HapticEffect.TICK -> VibrationEffect.EFFECT_TICK
            HapticEffect.CLICK -> VibrationEffect.EFFECT_CLICK
            HapticEffect.IMPACT -> VibrationEffect.EFFECT_HEAVY_CLICK
            HapticEffect.SUCCESS -> VibrationEffect.EFFECT_DOUBLE_CLICK
        }
        if (!supportsPredefinedEffects) return HapticSupport.APPROXIMATE
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return HapticSupport.UNKNOWN
        return when (v.areEffectsSupported(predefined).first()) {
            Vibrator.VIBRATION_EFFECT_SUPPORT_YES -> HapticSupport.EXACT
            Vibrator.VIBRATION_EFFECT_SUPPORT_NO -> HapticSupport.APPROXIMATE
            else -> HapticSupport.UNKNOWN
        }
    }

    /** The weakest support across every effect [preset] uses; null for Off, which plays nothing. */
    fun support(preset: HapticPreset): HapticSupport? {
        val effects = HapticEvent.all.mapNotNull { (_, e) -> preset.effectFor(e) }.distinct()
        if (effects.isEmpty()) return null
        return effects.map { support(it) }.minBy { it.ordinal }
    }

    /** Which feel [play] uses. Screens set this from the experiment's saved preset. */
    var preset: HapticPreset = HapticPreset.CRISP

    fun play(event: HapticEvent) {
        val effect = preset.effectFor(event) ?: return
        play(effect)
    }

    /** Lets you feel a preset the moment you pick it. */
    fun preview(preset: HapticPreset) {
        val effect = preset.effectFor(HapticEvent.Snap) ?: return
        play(effect)
    }

    /** Fires one specific effect, bypassing the preset table (timeline markers pick their own). */
    fun play(effect: HapticEffect) {
        if (!hasHardware) return
        val v = vibrator ?: return
        v.vibrate(build(v, effect))
    }

    private fun build(v: Vibrator, effect: HapticEffect): VibrationEffect = when (effect) {
        // No predefined "soft": a short low-amplitude pulse where the motor allows, else the lightest tick.
        HapticEffect.SOFT ->
            if (supportsAmplitudeControl) VibrationEffect.createOneShot(8, 40)
            else predefinedOr(v, VibrationEffect.EFFECT_TICK, fallbackMs = 6, fallbackAmp = 40)
        HapticEffect.TICK -> predefinedOr(v, VibrationEffect.EFFECT_TICK, fallbackMs = 8, fallbackAmp = 90)
        HapticEffect.CLICK -> predefinedOr(v, VibrationEffect.EFFECT_CLICK, fallbackMs = 14, fallbackAmp = 120)
        HapticEffect.IMPACT -> predefinedOr(v, VibrationEffect.EFFECT_HEAVY_CLICK, fallbackMs = 20, fallbackAmp = 200)
        HapticEffect.HEAVY ->
            if (supportsAmplitudeControl) VibrationEffect.createOneShot(32, 255)
            else predefinedOr(v, VibrationEffect.EFFECT_HEAVY_CLICK, fallbackMs = 30, fallbackAmp = 255)
        HapticEffect.SUCCESS -> predefinedOr(v, VibrationEffect.EFFECT_DOUBLE_CLICK, fallbackMs = 16, fallbackAmp = 150)
    }

    private fun predefinedOr(
        v: Vibrator,
        predefinedEffect: Int,
        fallbackMs: Long,
        fallbackAmp: Int,
    ): VibrationEffect {
        return if (supportsPredefinedEffects) {
            VibrationEffect.createPredefined(predefinedEffect)
        } else if (supportsAmplitudeControl) {
            VibrationEffect.createOneShot(fallbackMs, fallbackAmp.coerceIn(1, 255))
        } else {
            // Limited hardware: duration-only, default amplitude.
            VibrationEffect.createOneShot(fallbackMs, VibrationEffect.DEFAULT_AMPLITUDE)
        }
    }
}
