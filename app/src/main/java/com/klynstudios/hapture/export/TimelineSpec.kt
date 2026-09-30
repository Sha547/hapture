package com.klynstudios.hapture.export

/**
 * A timeline as one JSON document: each step's own name, type, gap and
 * duration, plus the cumulative start time a player would need -- the export
 * a designer or a hand-rolled coroutine sequence would work from. No motion
 * numbers are repeated here; each step already has its own full design spec
 * (see [DesignSpec]) from its own experiment's Export section.
 */
object TimelineSpec {

    data class Step(val name: String, val type: String, val gapBeforeMs: Int, val durationMs: Int)

    data class Haptic(val timeMs: Int, val effect: String)

    fun json(name: String, steps: List<Step>, haptics: List<Haptic> = emptyList()): String {
        var t = 0
        val stepsJson = steps.joinToString(",") { step ->
            t += step.gapBeforeMs
            val startMs = t
            t += step.durationMs
            """
            |    {
            |      "name": ${DesignSpec.str(step.name)},
            |      "type": ${DesignSpec.str(step.type)},
            |      "gapBeforeMs": ${step.gapBeforeMs},
            |      "startMs": $startMs,
            |      "durationMs": ${step.durationMs}
            |    }""".trimMargin()
        }

        val hapticsJson = haptics.joinToString(",") { h ->
            "\n    { \"timeMs\": ${h.timeMs}, \"effect\": ${DesignSpec.str(h.effect)} }"
        }
        val total = maxOf(t, haptics.maxOfOrNull { it.timeMs } ?: 0)

        return """
            |{
            |  "tool": "Hapture",
            |  "specVersion": 1,
            |  "name": ${DesignSpec.str(name)},
            |  "totalMs": $total,
            |  "steps": [$stepsJson
            |  ],
            |  "haptics": [$hapticsJson
            |  ]
            |}
        """.trimMargin()
    }
}
