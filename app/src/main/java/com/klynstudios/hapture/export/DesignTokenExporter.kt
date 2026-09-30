package com.klynstudios.hapture.export

import com.klynstudios.hapture.ui.design.DesignTokens

/**
 * The active theme as W3C design tokens (the `$value` / `$type` format that
 * Figma variables, Tokens Studio and Style Dictionary import). The app reads
 * these same tokens to draw itself, so what you export is what you see.
 */
object DesignTokenExporter {

    fun json(tokens: DesignTokens): String {
        fun color(name: String, value: androidx.compose.ui.graphics.Color, last: Boolean = false) =
            "    \"$name\": { \"\$value\": \"${DesignSpec.hex(value)}\", \"\$type\": \"color\" }${if (last) "" else ","}"

        return """
            |{
            |  "motionLab": {
            |    "theme": { "${'$'}value": "${tokens.id.name.lowercase()}", "${'$'}type": "string" },
            |    "color": {
            |${color("canvas", tokens.canvas)}
            |${color("surface", tokens.surface)}
            |${color("ink", tokens.ink)}
            |${color("inkSoft", tokens.inkSoft)}
            |${color("line", tokens.line)}
            |${color("inkFaint", tokens.inkFaint)}
            |${color("accent", tokens.accent, last = true)}
            |    },
            |    "radius": {
            |      "card": { "${'$'}value": "${DesignSpec.num(tokens.radiusCard.value, 0)}px", "${'$'}type": "dimension" },
            |      "control": { "${'$'}value": "${DesignSpec.num(tokens.radiusControl.value, 0)}px", "${'$'}type": "dimension" }
            |    },
            |    "hairline": { "${'$'}value": "${DesignSpec.num(tokens.hairline.value, 0)}px", "${'$'}type": "dimension" },
            |    "font": {
            |      "body": { "${'$'}value": "Manrope", "${'$'}type": "fontFamily" },
            |      "mono": { "${'$'}value": "Geist Mono", "${'$'}type": "fontFamily" }
            |    }
            |  }
            |}
        """.trimMargin()
    }
}
