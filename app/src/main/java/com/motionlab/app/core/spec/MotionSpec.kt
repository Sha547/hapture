package com.motionlab.app.core.spec

import com.motionlab.app.export.SpringMath
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** One spring in engineering terms. [damping] is the absolute coefficient most platforms (Figma, SwiftUI, Flutter, Reanimated) take. */
data class SpringSpec(val stiffness: Float, val dampingRatio: Float, val mass: Float = 1f) {
    val damping: Float get() = SpringMath.damping(stiffness, dampingRatio, mass)
    val settleMs: Int get() = SpringMath.settleMs(stiffness, dampingRatio)
    val overshootPercent: Float get() = SpringMath.overshoot(dampingRatio) * 100f

    /** Same stiffness, critically damped: settles without overshoot. */
    fun reduced(): SpringSpec = copy(dampingRatio = 1f)
}

/**
 * One property moving between two values when the trigger fires. [property] is
 * one of [PROPERTIES]; [from]/[to] are in that property's own unit (scale: x,
 * offset: dp, opacity: 0..1, rotation: degrees, progress: 0..1).
 */
data class MotionTransition(
    val property: String,
    val from: Float,
    val to: Float,
    val delayMs: Int = 0,
    val spring: SpringSpec,
    val follows: Follow? = null,
)

/**
 * A transition that starts by reference to another rather than at a fixed time: it begins [lagMs]
 * after transition number [source] (an earlier one) has done [atProgress] of its move. Retune the
 * source spring and the follower moves with it. [MotionSpec.resolved] turns this into a plain delay.
 */
data class Follow(val source: Int, val atProgress: Float, val lagMs: Int)

/**
 * The platform-neutral description of a motion: what triggers it, what moves,
 * with which spring, with what haptic, and the reduced-motion alternative.
 * Every export target (Compose, SwiftUI, Flutter, React Native, web, Lottie,
 * Figma) is generated from this, so they cannot drift apart.
 */
data class MotionSpec(
    val name: String,
    val interaction: String,
    val trigger: String,
    val spring: SpringSpec,
    val transitions: List<MotionTransition>,
    val parameters: Map<String, Float> = emptyMap(),
    val haptic: String = "crisp",
) {
    /**
     * The same spec with every [MotionTransition.follows] turned into a concrete [MotionTransition.delayMs]
     * (start of the source + time for its spring to reach the chosen progress + lag), so any exporter
     * that understands a delay understands a chain. A reference to itself or a later transition is ignored.
     */
    fun resolved(): MotionSpec {
        if (transitions.none { it.follows != null }) return this
        val start = IntArray(transitions.size)
        val out = transitions.mapIndexed { i, t ->
            val f = t.follows
            val delay = if (f != null && f.source in 0 until i) {
                val src = transitions[f.source].spring
                start[f.source] + SpringMath.timeToReachMs(src.stiffness, src.dampingRatio, f.atProgress) + f.lagMs
            } else t.delayMs
            start[i] = delay
            t.copy(delayMs = delay)
        }
        return copy(transitions = out)
    }

    val staggerTotalMs: Int
        get() = (resolved().transitions.maxOfOrNull { it.delayMs } ?: 0) +
            ((parameters["staggerMs"] ?: 0f) * ((parameters["itemCount"] ?: 1f) - 1f).coerceAtLeast(0f)).toInt()

    fun toJson(): String {
        val root = JSONObject()
        root.put("tool", "Motion Lab")
        root.put("specVersion", if (transitions.any { it.follows != null }) 3 else 2)
        root.put("name", name)
        root.put("interaction", interaction)
        root.put("trigger", trigger)
        root.put("spring", springJson(spring))
        root.put("transitions", JSONArray().also { arr ->
            // delayMs is always the effective start time; "follows" says where it came from, for tools that want the relationship.
            resolved().transitions.forEach { t ->
                val o = JSONObject().put("property", t.property).put("from", r(t.from)).put("to", r(t.to))
                    .put("delayMs", t.delayMs).put("spring", springJson(t.spring))
                t.follows?.let { o.put("follows", JSONObject().put("source", it.source).put("atProgress", r(it.atProgress)).put("lagMs", it.lagMs)) }
                arr.put(o)
            }
        })
        root.put("parameters", JSONObject().also { o -> parameters.forEach { (k, v) -> o.put(k, r(v)) } })
        root.put("haptic", haptic)
        root.put(
            "reducedMotion",
            JSONObject().put("strategy", "critically-damped").put("spring", springJson(spring.reduced()))
                .put("altCrossfadeMs", 150)
        )
        root.put(
            "curve",
            JSONObject().put("durationMs", spring.settleMs)
                .put("samples", JSONArray().also { a -> SpringMath.samples(spring.stiffness, spring.dampingRatio).forEach { a.put(r(it, 3)) } })
        )
        return root.toString(2)
    }

    companion object {
        val PROPERTIES = listOf("scale", "offsetX", "offsetY", "opacity", "rotation", "progress")

        private fun springJson(s: SpringSpec) = JSONObject()
            .put("mass", r(s.mass)).put("stiffness", r(s.stiffness)).put("dampingRatio", r(s.dampingRatio))
            .put("damping", r(s.damping)).put("settleMs", s.settleMs).put("overshootPercent", r(s.overshootPercent))

        private fun r(v: Float, places: Int = 3): Double {
            val f = Math.pow(10.0, places.toDouble())
            return Math.round(v * f) / f
        }

        /** Round-trips [toJson]. Null if it isn't a Motion Lab spec. */
        fun fromJson(text: String): MotionSpec? = try {
            val o = JSONObject(text)
            val sp = o.getJSONObject("spring")
            val spring = SpringSpec(sp.getDouble("stiffness").toFloat(), sp.getDouble("dampingRatio").toFloat(), sp.optDouble("mass", 1.0).toFloat())
            val ts = o.optJSONArray("transitions") ?: JSONArray()
            val transitions = (0 until ts.length()).map { i ->
                val t = ts.getJSONObject(i)
                val ts2 = t.optJSONObject("spring")
                val fo = t.optJSONObject("follows")
                MotionTransition(
                    t.getString("property"), t.getDouble("from").toFloat(), t.getDouble("to").toFloat(), t.optInt("delayMs", 0),
                    if (ts2 != null) SpringSpec(ts2.getDouble("stiffness").toFloat(), ts2.getDouble("dampingRatio").toFloat(), ts2.optDouble("mass", 1.0).toFloat()) else spring,
                    fo?.let { Follow(it.getInt("source"), it.getDouble("atProgress").toFloat(), it.optInt("lagMs", 0)) },
                )
            }
            val po = o.optJSONObject("parameters")
            val params = po?.keys()?.asSequence()?.associateWith { po.getDouble(it).toFloat() } ?: emptyMap()
            MotionSpec(o.getString("name"), o.getString("interaction"), o.optString("trigger", "release"), spring, transitions, params, o.optString("haptic", "crisp"))
        } catch (_: JSONException) {
            null
        }

        /**
         * Builds the neutral spec from the design JSON every gesture editor already
         * exports ([com.motionlab.app.export.DesignSpec.json]) -- one place to derive
         * it, so the editors don't each need to know about every export target.
         * A gesture interaction's transition is its release/settle spring.
         */
        fun fromDesignJson(text: String): MotionSpec? = try {
            val o = JSONObject(text)
            val sp = o.getJSONObject("spring")
            val spring = SpringSpec(sp.getDouble("stiffness").toFloat(), sp.getDouble("dampingRatio").toFloat())
            val interaction = o.getString("interaction")
            val po = o.optJSONObject("parameters")
            val params = po?.keys()?.asSequence()?.associateWith { po.getDouble(it).toFloat() } ?: emptyMap()
            val (property, from) = releaseTransition(interaction)
            MotionSpec(
                o.getString("name"), interaction, "release",
                spring, listOf(MotionTransition(property, from, if (property == "scale") 1f else 0f, 0, spring)),
                params, o.optJSONObject("haptics")?.optString("preset", "crisp") ?: "crisp",
            )
        } catch (_: JSONException) {
            null
        }

        /** A gesture interaction's neutral spec: its release/settle spring, from the numbers an editor already has. */
        fun forRelease(name: String, interaction: String, spring: SpringSpec, parameters: Map<String, Float>, haptic: String): MotionSpec {
            val (property, from) = releaseTransition(interaction)
            return MotionSpec(
                name, interaction, "release", spring,
                listOf(MotionTransition(property, from, if (property == "scale") 1f else 0f, 0, spring)),
                parameters, haptic,
            )
        }

        private fun releaseTransition(interaction: String): Pair<String, Float> = when (interaction) {
            "pinch_zoom" -> "scale" to 1.6f
            "bottom_sheet", "pull_refresh" -> "offsetY" to 120f
            else -> "offsetX" to 96f
        }
    }
}
