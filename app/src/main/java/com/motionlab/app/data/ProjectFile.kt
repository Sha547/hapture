package com.motionlab.app.data

import com.motionlab.app.core.haptics.HapticPreset
import com.motionlab.app.core.spec.TriggerSpecs
import com.motionlab.app.core.model.CustomShapeCodec
import com.motionlab.app.core.model.ObjectFill
import com.motionlab.app.core.model.ObjectShape
import org.json.JSONException
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * The `.motionlab` file: one experiment as plain JSON. It stores the slider
 * positions (the 0..1 values you actually set), not derived spring numbers,
 * so importing reproduces the experiment exactly, on any screen density.
 *
 * Importing is defensive: files come from anywhere, so anything malformed is
 * refused with a reason rather than half-loaded, numbers are clamped to their
 * valid range, and files from a newer format version are declined instead of
 * guessed at.
 */
object ProjectFile {

    const val FORMAT = "motionlab"
    const val VERSION = 1
    const val EXTENSION = "motionlab"
    const val MAX_BYTES = 64 * 1024

    sealed interface Result {
        data class Ok(val entity: ExperimentEntity) : Result
        data class Error(val reason: String) : Result
    }

    fun fileName(name: String): String {
        val slug = name.trim().replace(Regex("[^A-Za-z0-9]+"), "-").trim('-').ifEmpty { "experiment" }
        return "$slug.$EXTENSION"
    }

    fun encode(e: ExperimentEntity): String {
        val params = JSONObject()
            .put("stiffnessT", r(e.stiffnessT))
            .put("dampingT", r(e.dampingT))
        e.resistanceT?.let { params.put("resistanceT", r(it)) }
        e.magneticStrengthT?.let { params.put("magneticStrengthT", r(it)) }
        e.magneticThresholdT?.let { params.put("magneticThresholdT", r(it)) }
        e.flingDistanceT?.let { params.put("flingDistanceT", r(it)) }
        e.flingSensitivityT?.let { params.put("flingSensitivityT", r(it)) }
        e.flingFrictionT?.let { params.put("flingFrictionT", r(it)) }
        e.flingTiltT?.let { params.put("flingTiltT", r(it)) }
        e.sheetPeekT?.let { params.put("sheetPeekT", r(it)) }
        e.sheetMidT?.let { params.put("sheetMidT", r(it)) }
        e.sheetMomentumT?.let { params.put("sheetMomentumT", r(it)) }
        e.sheetDismissT?.let { params.put("sheetDismissT", r(it)) }
        e.pullTriggerT?.let { params.put("pullTriggerT", r(it)) }
        e.pullHoldT?.let { params.put("pullHoldT", r(it)) }
        e.zoomMinT?.let { params.put("zoomMinT", r(it)) }
        e.zoomMaxT?.let { params.put("zoomMaxT", r(it)) }
        e.reorderThresholdT?.let { params.put("reorderThresholdT", r(it)) }
        e.triggerAmountT?.let { params.put("triggerAmountT", r(it)) }
        e.triggerStaggerT?.let { params.put("triggerStaggerT", r(it)) }
        e.chainProperty?.let {
            params.put("chainProperty", it)
            e.chainAtT?.let { v -> params.put("chainAtT", r(v)) }
            e.chainLagT?.let { v -> params.put("chainLagT", r(v)) }
            e.chainStiffnessT?.let { v -> params.put("chainStiffnessT", r(v)) }
            e.chainDampingT?.let { v -> params.put("chainDampingT", r(v)) }
        }

        val objectJson = JSONObject()
            .put("shape", e.objectShape.name)
            .put("sizeT", r(e.objectSizeT))
            .put("fill", e.objectFill.name)
        // Compact "x,y;x,y;..." rather than a nested array: one already-tested
        // codec handles both this and the Room column, so there's one place
        // that decides what counts as a valid point, not two.
        if (e.customShapePath.isNotBlank()) objectJson.put("customPath", e.customShapePath)

        return JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("name", e.name)
            .put("type", e.type.name)
            .put("params", params)
            .put("object", objectJson)
            .put("haptics", e.hapticPreset.name)
            .toString(2)
    }

    /** [now] stamps createdAt/updatedAt; the caller's repository assigns a fresh id. */
    fun decode(text: String, now: Long = System.currentTimeMillis()): Result {
        if (text.length > MAX_BYTES) return Result.Error("That file is too large to be an experiment.")
        val root = try {
            JSONObject(text)
        } catch (_: JSONException) {
            return Result.Error("That file is not a Motion Lab experiment.")
        }
        if (root.optString("format") != FORMAT) return Result.Error("That file is not a Motion Lab experiment.")
        val version = root.optInt("version", -1)
        if (version < 1) return Result.Error("That file has no valid version.")
        if (version > VERSION) return Result.Error("That file was made by a newer version of Motion Lab.")

        val type = ExperimentType.entries.firstOrNull { it.name == root.optString("type") }
            ?: return Result.Error("That experiment uses an interaction this version doesn't have.")
        val params = root.optJSONObject("params") ?: return Result.Error("The file has no parameters.")

        fun req(key: String): Float? = t(params, key)
        val stiffness = req("stiffnessT") ?: return Result.Error("Missing stiffness.")
        val damping = req("dampingT") ?: return Result.Error("Missing damping.")

        val base = ExperimentEntity(
            type = type,
            name = root.optString("name").trim().take(60).ifEmpty { "Imported experiment" },
            createdAt = now,
            updatedAt = now,
            stiffnessT = stiffness,
            dampingT = damping,
        )
        val withType = when (type) {
            ExperimentType.SPRING_DRAG -> base.copy(
                resistanceT = req("resistanceT") ?: return Result.Error("Missing resistance."),
            )
            ExperimentType.MAGNETIC_SNAP -> base.copy(
                magneticStrengthT = req("magneticStrengthT") ?: return Result.Error("Missing magnetic strength."),
                magneticThresholdT = req("magneticThresholdT") ?: return Result.Error("Missing capture radius."),
            )
            ExperimentType.SWIPE_FLING -> base.copy(
                flingDistanceT = req("flingDistanceT") ?: return Result.Error("Missing dismiss distance."),
                flingSensitivityT = req("flingSensitivityT") ?: return Result.Error("Missing fling sensitivity."),
                flingFrictionT = req("flingFrictionT") ?: return Result.Error("Missing friction."),
                flingTiltT = req("flingTiltT") ?: return Result.Error("Missing tilt."),
            )
            ExperimentType.BOTTOM_SHEET -> base.copy(
                resistanceT = req("resistanceT") ?: return Result.Error("Missing resistance."),
                sheetPeekT = req("sheetPeekT") ?: return Result.Error("Missing peek height."),
                sheetMidT = req("sheetMidT") ?: return Result.Error("Missing middle height."),
                sheetMomentumT = req("sheetMomentumT") ?: return Result.Error("Missing momentum."),
                sheetDismissT = req("sheetDismissT") ?: return Result.Error("Missing dismiss ease."),
            )
            ExperimentType.PULL_REFRESH -> base.copy(
                resistanceT = req("resistanceT") ?: return Result.Error("Missing resistance."),
                pullTriggerT = req("pullTriggerT") ?: return Result.Error("Missing trigger distance."),
                pullHoldT = req("pullHoldT") ?: return Result.Error("Missing hold time."),
            )
            ExperimentType.PINCH_ZOOM -> base.copy(
                resistanceT = req("resistanceT") ?: return Result.Error("Missing resistance."),
                zoomMinT = req("zoomMinT") ?: return Result.Error("Missing minimum zoom."),
                zoomMaxT = req("zoomMaxT") ?: return Result.Error("Missing maximum zoom."),
            )
            ExperimentType.DRAG_REORDER -> base.copy(
                reorderThresholdT = req("reorderThresholdT") ?: return Result.Error("Missing swap threshold."),
            )
            ExperimentType.TOGGLE, ExperimentType.BUTTON_PRESS, ExperimentType.TAB_INDICATOR,
            ExperimentType.STAGGER_LIST, ExperimentType.LIKE_BURST -> base.copy(
                triggerAmountT = req("triggerAmountT") ?: 0.5f,
                triggerStaggerT = req("triggerStaggerT") ?: 0.4f,
            ).let { e ->
                // An optional follower; a property this version doesn't know is dropped rather than trusted.
                val prop = params.optString("chainProperty").takeIf { name -> TriggerSpecs.FOLLOWERS.any { it.first == name } }
                if (prop == null) e else e.copy(
                    chainProperty = prop,
                    chainAtT = req("chainAtT") ?: 0.5f, chainLagT = req("chainLagT") ?: 0.2f,
                    chainStiffnessT = req("chainStiffnessT") ?: 0.6f, chainDampingT = req("chainDampingT") ?: 0.55f,
                )
            }
        }

        val obj = root.optJSONObject("object")
        val styled = if (obj == null) withType else withType.copy(
            objectShape = ObjectShape.entries.firstOrNull { it.name == obj.optString("shape") } ?: ObjectShape.ROUNDED,
            objectSizeT = t(obj, "sizeT") ?: withType.objectSizeT,
            objectFill = ObjectFill.entries.firstOrNull { it.name == obj.optString("fill") } ?: ObjectFill.INK,
            // Round-tripped through the codec so a hand-edited or corrupted
            // file can't smuggle in a malformed point -- same defensive
            // policy as every other field here.
            customShapePath = CustomShapeCodec.encode(CustomShapeCodec.decode(obj.optString("customPath"))),
        )
        val haptic = HapticPreset.entries.firstOrNull { it.name == root.optString("haptics") } ?: HapticPreset.CRISP
        return Result.Ok(styled.copy(hapticPreset = haptic))
    }

    /** A 0..1 value, clamped; null if absent or not a finite number. */
    private fun t(o: JSONObject, key: String): Float? {
        if (!o.has(key)) return null
        val v = o.optDouble(key, Double.NaN)
        return if (v.isNaN() || v.isInfinite()) null else v.toFloat().coerceIn(0f, 1f)
    }

    private fun r(v: Float): Double = BigDecimal(v.toDouble()).setScale(4, RoundingMode.HALF_UP).toDouble()
}
