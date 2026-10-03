package com.klynstudios.hapture.export.platform

import com.klynstudios.hapture.core.spec.MotionSpec

object SwiftUiGenerator {
    fun generate(source: MotionSpec): String {
        val spec = source.resolved() // chained transitions become plain delays
        val p = pascal(spec.name)
        val s = spec.spring
        val mods = spec.transitions.joinToString("\n") { t ->
            val dl = if (t.delayMs > 0) ".delay(${n(t.delayMs / 1000f)})" else ""
            val anim = ".animation((reduceMotion ? .hapture${p}Reduced : .hapture$p)$dl, value: active)"
            val effect = when (t.property) {
                "scale" -> ".scaleEffect(active ? ${n(t.to)} : ${n(t.from)})"
                "offsetY" -> ".offset(y: active ? ${n(t.to)} : ${n(t.from)})"
                "opacity" -> ".opacity(active ? ${n(t.to)} : ${n(t.from)})"
                "rotation" -> ".rotationEffect(.degrees(active ? ${n(t.to)} : ${n(t.from)}))"
                else -> ".offset(x: active ? ${n(t.to)} : ${n(t.from)})"
            }
            "            $effect\n            $anim"
        }
        return header(spec, "//") + """
            |import SwiftUI
            |
            |extension Animation {
            |    /// The tuned spring. `interpolatingSpring` takes absolute damping, not a ratio.
            |    static let hapture$p = Animation.interpolatingSpring(mass: ${n(s.mass)}, stiffness: ${n(s.stiffness, 1)}, damping: ${n(s.damping, 2)})
            |    /// Reduced Motion: same stiffness, critically damped, so nothing overshoots.
            |    static let hapture${p}Reduced = Animation.interpolatingSpring(mass: ${n(s.mass)}, stiffness: ${n(s.stiffness, 1)}, damping: ${n(s.reduced().damping, 2)})
            |}
            |
            |/// Usage skeleton: tap toggles `active`; wire it to your own trigger.
            |struct ${p}Demo: View {
            |    @Environment(\.accessibilityReduceMotion) private var reduceMotion
            |    @State private var active = false
            |
            |    var body: some View {
            |        RoundedRectangle(cornerRadius: 24)
            |            .frame(width: 96, height: 96)
            |$mods
            |            .onTapGesture { active.toggle() }
            |    }
            |}
            |
            |// Haptic: ${spec.haptic}. Use UIImpactFeedbackGenerator(style: .light).impactOccurred() at the trigger.
        """.trimMargin() + "\n"
    }
}
