package com.motionlab.app.export

import com.motionlab.app.core.spec.MotionSpec
import com.motionlab.app.export.platform.ComposeGenericGenerator
import com.motionlab.app.export.platform.FlutterGenerator
import com.motionlab.app.export.platform.LottieGenerator
import com.motionlab.app.export.platform.ReactNativeGenerator
import com.motionlab.app.export.platform.SwiftUiGenerator
import com.motionlab.app.export.platform.WebGenerator
import org.json.JSONObject

/**
 * Every export the editor offers, as one JSON object the browser bridge shows:
 * `{ "swift": { "label": "SwiftUI", "ext": "swift", "code": "..." }, ... }`.
 * Built from the same generators as the Export list, so the bridge cannot differ from the app.
 */
object ExportBundle {

    fun json(compose: String, designSpec: String, css: String, motion: MotionSpec?): String {
        val o = JSONObject()
        // Code exports are stamped exactly like the app's own copy buttons, so code copied from the bridge can be checked too.
        val springs = motion?.let { listOf(ExportStamp.Spring("x", it.name, it.spring.stiffness, it.spring.dampingRatio)) }
        fun add(key: String, label: String, ext: String, code: String, style: ExportStamp.Style? = null) {
            val text = if (style != null && springs != null) ExportStamp.wrap(code, style, springs) else code
            o.put(key, JSONObject().put("label", label).put("ext", ext).put("code", text))
        }
        add("compose", "Compose", "kt", compose, ExportStamp.Style.SLASH)
        if (motion != null) {
            add("swift", "SwiftUI", "swift", SwiftUiGenerator.generate(motion), ExportStamp.Style.SLASH)
            add("flutter", "Flutter", "dart", FlutterGenerator.generate(motion), ExportStamp.Style.SLASH)
            add("rn", "React Native", "tsx", ReactNativeGenerator.generate(motion), ExportStamp.Style.SLASH)
            add("web", "Web", "txt", WebGenerator.generate(motion), ExportStamp.Style.SLASH)
        }
        add("css", "CSS easing", "css", css, ExportStamp.Style.BLOCK)
        if (motion != null) add("lottie", "Lottie", "json", LottieGenerator.generate(motion))
        add("spec", "Design spec", "json", designSpec)
        if (motion != null) {
            add("compose2", "Compose (spring only)", "kt", ComposeGenericGenerator.generate(motion), ExportStamp.Style.SLASH)
            add("neutral", "Motion spec", "json", motion.toJson())
        }
        return o.toString()
    }
}
