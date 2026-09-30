package com.klynstudios.hapture.export.platform

import com.klynstudios.hapture.core.spec.MotionSpec

object ReactNativeGenerator {
    fun generate(source: MotionSpec): String {
        val spec = source.resolved() // chained transitions become plain delays
        val p = pascal(spec.name)
        val s = spec.spring
        val values = spec.transitions.indices.joinToString("\n") { "  const p$it = useSharedValue(0);" }
        val targets = spec.transitions.mapIndexed { i, t ->
            val delay = if (t.delayMs > 0) "withDelay(${t.delayMs}, withSpring(to, cfg))" else "withSpring(to, cfg)"
            "    p$i.value = $delay;"
        }.joinToString("\n")
        val styles = spec.transitions.mapIndexed { i, t ->
            val v = "${n(t.from)} + (${n(t.to)} - ${n(t.from)}) * p$i.value"
            when (t.property) {
                "scale" -> "{ scale: $v }"
                "offsetY" -> "{ translateY: $v }"
                "rotation" -> "{ rotate: `\${$v}deg` }"
                "opacity" -> "opacity: $v"
                else -> "{ translateX: $v }"
            }
        }
        val opacity = styles.filter { it.startsWith("opacity") }.joinToString(", ") { it }
        val transforms = styles.filterNot { it.startsWith("opacity") }.joinToString(", ")
        val styleBody = listOfNotNull(
            if (transforms.isNotEmpty()) "transform: [$transforms]" else null,
            opacity.ifEmpty { null },
        ).joinToString(",\n      ")
        return header(spec, "//") + """
            |import React, { useState } from 'react';
            |import { Pressable } from 'react-native';
            |import Animated, { useSharedValue, useAnimatedStyle, withSpring, withDelay, useReducedMotion } from 'react-native-reanimated';
            |
            |const SPRING = { mass: ${n(s.mass)}, stiffness: ${n(s.stiffness, 1)}, damping: ${n(s.damping, 2)} };
            |// Reduced Motion: same stiffness, critically damped.
            |const SPRING_REDUCED = { mass: ${n(s.mass)}, stiffness: ${n(s.stiffness, 1)}, damping: ${n(s.reduced().damping, 2)} };
            |
            |// Usage skeleton: press toggles; wire it to your own trigger.
            |export function ${p}Demo() {
            |  const reduce = useReducedMotion();
            |  const [active, setActive] = useState(false);
            |$values
            |
            |  const style = useAnimatedStyle(() => ({
            |      $styleBody,
            |  }));
            |
            |  const toggle = () => {
            |    const to = active ? 0 : 1;
            |    const cfg = reduce ? SPRING_REDUCED : SPRING;
            |$targets
            |    setActive(!active);
            |  };
            |
            |  return (
            |    <Pressable onPress={toggle}>
            |      <Animated.View style={[{ width: 96, height: 96, borderRadius: 24, backgroundColor: '#141413' }, style]} />
            |    </Pressable>
            |  );
            |}
            |
            |// Haptic: ${spec.haptic}. Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light) (expo-haptics) at the trigger.
        """.trimMargin() + "\n"
    }
}
