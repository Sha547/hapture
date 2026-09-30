package com.klynstudios.hapture.core.doodle

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode

/** Strokes <-> compact JSON. Decoding never throws: anything malformed just yields fewer strokes. */
object DoodleCodec {

    fun encode(strokes: List<DoodleStroke>): String {
        val arr = JSONArray()
        strokes.forEach { s ->
            val pts = JSONArray()
            s.points.forEach { pts.put(round(it.x)); pts.put(round(it.y)) }
            arr.put(
                JSONObject().put("b", s.brush.name).put("c", s.color).put("w", round(s.width)).put("p", pts)
            )
        }
        return JSONObject().put("v", 1).put("strokes", arr).toString()
    }

    fun decode(text: String): List<DoodleStroke> {
        if (text.isBlank()) return emptyList()
        return try {
            val arr = JSONObject(text).optJSONArray("strokes") ?: return emptyList()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val brush = Brush.entries.firstOrNull { it.name == o.optString("b") } ?: return@mapNotNull null
                val p = o.optJSONArray("p") ?: return@mapNotNull null
                val pts = (0 until p.length() / 2).map { Pt(p.optDouble(it * 2).toFloat(), p.optDouble(it * 2 + 1).toFloat()) }
                if (pts.isEmpty() || pts.any { it.x.isNaN() || it.y.isNaN() }) return@mapNotNull null
                DoodleStroke(brush, o.optInt("c"), o.optDouble("w", 0.01).toFloat().coerceIn(0.001f, 0.5f), pts)
            }
        } catch (_: JSONException) {
            emptyList()
        }
    }

    private fun round(v: Float): Double = BigDecimal(v.toDouble()).setScale(4, RoundingMode.HALF_UP).toDouble()
}
