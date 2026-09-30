package com.klynstudios.hapture.export.platform

import com.klynstudios.hapture.core.spec.MotionSpec
import com.klynstudios.hapture.export.DesignSpec

object WebGenerator {
    fun generate(source: MotionSpec): String {
        val spec = source.resolved() // chained transitions become plain delays
        val p = pascal(spec.name)
        val s = spec.spring
        val prop = { name: String -> when (name) { "offsetX" -> "x"; "offsetY" -> "y"; "rotation" -> "rotate"; else -> name } }
        val animate = spec.transitions.joinToString(", ") { t -> "${prop(t.property)}: active ? ${n(t.to)} : ${n(t.from)}" }
        val perProp = spec.transitions.joinToString(", ") { t ->
            "${prop(t.property)}: { ...(reduce ? reduced : spring)${if (t.delayMs > 0) ", delay: ${n(t.delayMs / 1000f)}" else ""} }"
        }
        val cssProps = spec.transitions.joinToString(" ") { t ->
            when (t.property) {
                "scale" -> "transform: scale(var(--motion-active-scale));"
                else -> ""
            }
        }.trim()
        return header(spec, "//") + """
            |// --- Framer Motion (React) -------------------------------------------
            |import { useState } from "react";
            |import { motion, useReducedMotion } from "motion/react"; // or "framer-motion"
            |
            |const spring = { type: "spring", stiffness: ${n(s.stiffness, 1)}, damping: ${n(s.damping, 2)}, mass: ${n(s.mass)} };
            |// Reduced Motion: same stiffness, critically damped.
            |const reduced = { type: "spring", stiffness: ${n(s.stiffness, 1)}, damping: ${n(s.reduced().damping, 2)}, mass: ${n(s.mass)} };
            |
            |// Usage skeleton: click toggles; wire it to your own trigger.
            |export function ${p}Demo() {
            |  const [active, setActive] = useState(false);
            |  const reduce = useReducedMotion();
            |  return (
            |    <motion.div
            |      onClick={() => setActive(!active)}
            |      animate={{ $animate }}
            |      transition={{ $perProp }}
            |      style={{ width: 96, height: 96, borderRadius: 24, background: "#141413" }}
            |    />
            |  );
            |}
            |
            |/* --- Plain CSS: the same spring as a linear() easing ---------------------- */
            |:root {
            |  --${camel(spec.name)}-duration: ${s.settleMs}ms;
            |  --${camel(spec.name)}-easing: ${DesignSpec.cssLinear(s.stiffness, s.dampingRatio)};
            |}
            |.${camel(spec.name)} { transition: transform var(--${camel(spec.name)}-duration) var(--${camel(spec.name)}-easing); }
            |@media (prefers-reduced-motion: reduce) {
            |  .${camel(spec.name)} { transition-duration: 150ms; transition-timing-function: ease-out; }
            |}
            |/* Haptic: ${spec.haptic}. navigator.vibrate(10) where supported. */
        """.trimMargin() + "\n"
    }
}
