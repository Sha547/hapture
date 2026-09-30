package com.klynstudios.hapture.export

import java.security.MessageDigest
import kotlin.math.abs

/**
 * Lets Hapture tell, later, whether code it generated is still what it generated.
 *
 * Every code export is wrapped in two comment lines. The first carries the springs the code was
 * made from and a short signature of everything between the two lines; the second closes it:
 *
 *     // hapture:v1 springs=t~snappy~500~0.6 sig=1a2b3c4d
 *     ...generated code...
 *     // hapture:end
 *
 * Paste code back and [check] answers one of three things: untouched and current, edited by hand
 * since export (the signature no longer matches), or untouched but the token has since changed.
 * The signature ignores trailing whitespace and line endings, but a formatter that rewrites
 * lines counts as an edit. JSON exports can't carry comments, so they aren't stamped.
 */
object ExportStamp {

    enum class Style(val open: String, val close: String) { SLASH("// ", ""), BLOCK("/* ", " */") }

    /** [kind] is "t" for a motion token and "x" for an experiment, so equal names in the two can't be confused. */
    data class Spring(val kind: String, val name: String, val stiffness: Float, val dampingRatio: Float)

    data class Change(val name: String, val shipped: Spring, val now: Spring)

    sealed interface Verdict {
        data object NoStamp : Verdict
        /** The code is exactly as exported and the springs still match; [missing] are names no longer in the app. */
        data class Current(val springs: List<Spring>, val missing: List<String>) : Verdict
        data class Edited(val springs: List<Spring>) : Verdict
        data class Outdated(val changes: List<Change>) : Verdict
    }

    private const val TAG = "hapture:v1"
    private const val END = "hapture:end"

    /** What stamps said before the app was renamed; still read, never written. */
    private const val LEGACY_TAG = "motionlab:v1"
    private const val LEGACY_END = "motionlab:end"

    fun wrap(code: String, style: Style, springs: List<Spring>): String {
        val body = code.trimEnd('\n')
        val line = "$TAG springs=${springs.joinToString("|") { encode(it) }} sig=${sign(body)}"
        return "${style.open}$line${style.close}\n$body\n${style.open}$END${style.close}\n"
    }

    fun check(pasted: String, current: List<Spring>): Verdict {
        val lines = pasted.replace("\r\n", "\n").lines()
        val start = lines.indexOfFirst { it.contains(TAG) || it.contains(LEGACY_TAG) }
        if (start < 0) return Verdict.NoStamp
        val endTag = if (lines[start].contains(TAG)) END else LEGACY_END
        val end = (start + 1 until lines.size).firstOrNull { lines[it].contains(endTag) } ?: return Verdict.NoStamp
        val header = lines[start]
        val springsField = Regex("""springs=(\S*)""").find(header)?.groupValues?.get(1) ?: return Verdict.NoStamp
        val sig = Regex("""sig=([0-9a-f]{8})""").find(header)?.groupValues?.get(1) ?: return Verdict.NoStamp
        val shipped = springsField.split("|").filter { it.isNotEmpty() }.mapNotNull { decode(it) }
        if (shipped.isEmpty()) return Verdict.NoStamp

        if (sign(lines.subList(start + 1, end).joinToString("\n")) != sig) return Verdict.Edited(shipped)

        val changes = ArrayList<Change>()
        val missing = ArrayList<String>()
        shipped.forEach { s ->
            val now = current.firstOrNull { it.kind == s.kind && it.name.equals(s.name, ignoreCase = true) }
            when {
                now == null -> missing += s.name
                abs(now.stiffness - s.stiffness) > s.stiffness * 0.005f + 0.05f || abs(now.dampingRatio - s.dampingRatio) > 0.005f ->
                    changes += Change(s.name, s, now)
            }
        }
        return if (changes.isNotEmpty()) Verdict.Outdated(changes) else Verdict.Current(shipped, missing)
    }

    private fun sign(body: String): String {
        val normal = body.replace("\r\n", "\n").lines().joinToString("\n") { it.trimEnd() }.trim('\n')
        val digest = MessageDigest.getInstance("SHA-256").digest(normal.toByteArray())
        return digest.take(4).joinToString("") { "%02x".format(it) }
    }

    private fun num(v: Float) = DesignSpec.num(v, 3)

    private fun encode(s: Spring) = listOf(s.kind, s.name.replace(Regex("[\\s|~=]+"), "_"), num(s.stiffness), num(s.dampingRatio)).joinToString("~")

    private fun decode(text: String): Spring? {
        val p = text.split("~")
        if (p.size != 4) return null
        return Spring(p[0], p[1], p[2].toFloatOrNull() ?: return null, p[3].toFloatOrNull() ?: return null)
    }
}
