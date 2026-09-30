package com.klynstudios.hapture.export.platform

import com.klynstudios.hapture.core.spec.MotionSpec

object FlutterGenerator {
    fun generate(source: MotionSpec): String {
        val spec = source.resolved() // chained transitions become plain delays
        val p = pascal(spec.name)
        val s = spec.spring
        val body = spec.transitions.joinToString("\n") { t ->
            val v = "${n(t.from)} + (${n(t.to)} - ${n(t.from)}) * _c.value"
            val delay = if (t.delayMs > 0) "   // delay ${t.delayMs} ms: start this transition with Future.delayed" else ""
            when (t.property) {
                "scale" -> "        child = Transform.scale(scale: $v, child: child);$delay"
                "offsetY" -> "        child = Transform.translate(offset: Offset(0, $v), child: child);$delay"
                "opacity" -> "        child = Opacity(opacity: ($v).clamp(0.0, 1.0), child: child);$delay"
                "rotation" -> "        child = Transform.rotate(angle: ($v) * 3.1415926 / 180, child: child);$delay"
                else -> "        child = Transform.translate(offset: Offset($v, 0), child: child);$delay"
            }
        }
        return header(spec, "//") + """
            |import 'package:flutter/material.dart';
            |import 'package:flutter/physics.dart';
            |
            |const _spring = SpringDescription(mass: ${n(s.mass)}, stiffness: ${n(s.stiffness, 1)}, damping: ${n(s.damping, 2)});
            |// Reduced Motion: same stiffness, critically damped.
            |const _springReduced = SpringDescription(mass: ${n(s.mass)}, stiffness: ${n(s.stiffness, 1)}, damping: ${n(s.reduced().damping, 2)});
            |
            |/// Usage skeleton: tap toggles the state; wire it to your own trigger.
            |class ${p}Demo extends StatefulWidget {
            |  const ${p}Demo({super.key});
            |  @override
            |  State<${p}Demo> createState() => _${p}DemoState();
            |}
            |
            |class _${p}DemoState extends State<${p}Demo> with SingleTickerProviderStateMixin {
            |  late final AnimationController _c = AnimationController.unbounded(vsync: this);
            |  bool _active = false;
            |
            |  void _toggle() {
            |    _active = !_active;
            |    final reduce = MediaQuery.of(context).disableAnimations;
            |    _c.animateWith(SpringSimulation(reduce ? _springReduced : _spring, _c.value, _active ? 1.0 : 0.0, _c.velocity));
            |  }
            |
            |  @override
            |  void dispose() {
            |    _c.dispose();
            |    super.dispose();
            |  }
            |
            |  @override
            |  Widget build(BuildContext context) {
            |    return GestureDetector(
            |      onTap: _toggle,
            |      child: AnimatedBuilder(
            |        animation: _c,
            |        builder: (context, _) {
            |          Widget child = Container(width: 96, height: 96, decoration: BoxDecoration(color: const Color(0xFF141413), borderRadius: BorderRadius.circular(24)));
            |$body
            |          return child;
            |        },
            |      ),
            |    );
            |  }
            |}
            |
            |// Haptic: ${spec.haptic}. HapticFeedback.lightImpact() at the trigger.
        """.trimMargin() + "\n"
    }
}
