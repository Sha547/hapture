package com.klynstudios.hapture.export.platform

import com.klynstudios.hapture.core.spec.MotionSpec
import com.klynstudios.hapture.core.spec.MotionTransition
import com.klynstudios.hapture.core.spec.SpringSpec
import com.klynstudios.hapture.export.SpringMath
import org.json.JSONArray
import org.json.JSONObject

/**
 * A real Lottie file: a rounded square whose transitions (one per property, each
 * starting at its own delay, so a chain shows up) are baked into per-frame keyframes sampled from the spring's exact step response, so any
 * Lottie player (web, iOS, Android, After Effects import) reproduces the same
 * motion. It is the spring's curve, not a re-simulation: no player-side physics.
 */
object LottieGenerator {
    private const val FPS = 60

    fun generate(source: MotionSpec): String {
        val spec = source.resolved() // chained transitions become plain delays
        if (spec.transitions.isEmpty()) return "{}"
        val hold = FPS / 2
        val cx = 200.0
        val cy = 200.0

        fun still(v: List<Double>) = JSONObject().put("a", 0).put("k", JSONArray(v))

        /** Keyframes for one transition: rests at [MotionTransition.from] until its delay, then follows the spring's exact step response. */
        fun keyframes(t: MotionTransition, map: (Double) -> List<Double>): JSONObject {
            val sp: SpringSpec = t.spring
            val delayFrames = (t.delayMs / 1000f * FPS).toInt()
            val settleFrames = (sp.settleMs / 1000f * FPS).toInt().coerceAtLeast(2)
            fun value(frame: Int): Double {
                val x = SpringMath.stepResponse(frame / FPS.toDouble(), sp.stiffness.toDouble(), sp.dampingRatio.toDouble(), sp.mass.toDouble())
                return t.from + (t.to - t.from) * x
            }
            fun kf(time: Int, v: Double) = JSONObject().put("t", time).put("s", JSONArray(map(v))).apply {
                put("i", JSONObject().put("x", JSONArray(listOf(1.0))).put("y", JSONArray(listOf(1.0))))
                put("o", JSONObject().put("x", JSONArray(listOf(0.0))).put("y", JSONArray(listOf(0.0))))
            }
            val arr = JSONArray()
            if (delayFrames > 0) arr.put(kf(0, t.from.toDouble()))
            for (f in 0..settleFrames) arr.put(kf(delayFrames + f, value(f)))
            return JSONObject().put("a", 1).put("k", arr)
        }

        var position: JSONObject = still(listOf(cx, cy, 0.0))
        var scale: JSONObject = still(listOf(100.0, 100.0, 100.0))
        var opacity: JSONObject = still(listOf(100.0))
        var rotation: JSONObject = still(listOf(0.0))
        val used = HashSet<String>()
        var last = 0
        spec.transitions.forEach { t ->
            val slot = when (t.property) { "scale" -> "scale"; "opacity" -> "opacity"; "rotation" -> "rotation"; "offsetY" -> "y"; else -> "x" }
            if (!used.add(slot)) return@forEach // one layer, one animation per property: the first transition wins
            last = maxOf(last, (t.delayMs / 1000f * FPS).toInt() + (t.spring.settleMs / 1000f * FPS).toInt().coerceAtLeast(2))
            when (slot) {
                "scale" -> scale = keyframes(t) { listOf(it * 100, it * 100, 100.0) }
                "y" -> position = keyframes(t) { listOf(cx, cy + it, 0.0) }
                "opacity" -> opacity = keyframes(t) { listOf(it * 100) }
                "rotation" -> rotation = keyframes(t) { listOf(it) }
                else -> position = keyframes(t) { listOf(cx + it, cy, 0.0) }
            }
        }
        val total = last + hold

        val shape = JSONObject().put("ty", "gr").put(
            "it",
            JSONArray()
                .put(JSONObject().put("ty", "rc").put("d", 1).put("s", still(listOf(120.0, 120.0))).put("p", still(listOf(0.0, 0.0))).put("r", still(listOf(28.0))))
                .put(JSONObject().put("ty", "fl").put("c", still(listOf(0.078, 0.078, 0.075, 1.0))).put("o", still(listOf(100.0))))
                .put(
                    JSONObject().put("ty", "tr").put("p", still(listOf(0.0, 0.0))).put("a", still(listOf(0.0, 0.0)))
                        .put("s", still(listOf(100.0, 100.0))).put("r", still(listOf(0.0))).put("o", still(listOf(100.0)))
                )
        )
        val layer = JSONObject().put("ddd", 0).put("ind", 1).put("ty", 4).put("nm", "shape").put("sr", 1)
            .put("ks", JSONObject().put("o", opacity).put("r", rotation).put("p", position).put("a", still(listOf(0.0, 0.0, 0.0))).put("s", scale))
            .put("ao", 0).put("shapes", JSONArray().put(shape)).put("ip", 0).put("op", total).put("st", 0).put("bm", 0)

        return JSONObject().put("v", "5.7.0").put("fr", FPS).put("ip", 0).put("op", total).put("w", 400).put("h", 400)
            .put("nm", spec.name).put("ddd", 0).put("assets", JSONArray()).put("layers", JSONArray().put(layer)).toString(2)
    }
}
