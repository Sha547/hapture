package com.motionlab.app.core

import com.motionlab.app.core.spec.Archetypes
import com.motionlab.app.core.spec.MotionGrade
import com.motionlab.app.core.spec.SpringSpec
import com.motionlab.app.export.ExportStamp
import com.motionlab.app.export.ExportStamp.Style
import com.motionlab.app.export.ExportStamp.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchetypesAndStampTest {
    private val snappy = ExportStamp.Spring("t", "snappy", 500f, 0.6f)
    private val code = "val snappy = spring<Float>(dampingRatio = 0.6f, stiffness = 500f)\nval other = 1\n"

    // --- Archetypes ---

    @Test fun `archetypes are complete and unambiguous`() {
        assertTrue(Archetypes.all.size >= 4)
        Archetypes.all.forEach { a ->
            assertTrue(a.springs.size >= 4)
            assertEquals("${a.id} names unique", a.springs.size, a.springs.map { it.name }.toSet().size)
            a.springs.forEach { assertTrue(it.stiffness > 0f && it.dampingRatio in 0.1f..2f) }
        }
        assertEquals(Archetypes.all.size, Archetypes.all.map { it.id }.toSet().size)
    }

    @Test fun `the restrained set never overshoots and the playful one does`() {
        fun maxOvershoot(id: String) = Archetypes.all.first { it.id == id }.springs.maxOf { SpringSpec(it.stiffness, it.dampingRatio).overshootPercent }
        assertEquals(0f, maxOvershoot("fintech"), 1f)
        assertTrue(maxOvershoot("social") > 15f)
    }

    @Test fun `every control spring in the restrained set grades A`() {
        Archetypes.all.first { it.id == "fintech" }.springs.forEach {
            assertEquals(it.name, "A", MotionGrade.ofSpring(it.name, SpringSpec(it.stiffness, it.dampingRatio)).letter)
        }
    }

    @Test fun `plan counts what would be added and what overwritten`() {
        val springs = Archetypes.all.first().springs
        assertEquals(Archetypes.Plan(springs.size, 0), Archetypes.plan(emptyList(), springs))
        assertEquals(Archetypes.Plan(springs.size - 1, 1), Archetypes.plan(listOf("TAP", "unrelated"), springs))
    }

    // --- Drift stamp ---

    @Test fun `untouched code matches and is current`() {
        val v = ExportStamp.check(ExportStamp.wrap(code, Style.SLASH, listOf(snappy)), listOf(snappy))
        assertTrue(v is Verdict.Current)
        assertTrue((v as Verdict.Current).missing.isEmpty())
    }

    @Test fun `a hand edit is caught even if the numbers are still right in the header`() {
        val edited = ExportStamp.wrap(code, Style.SLASH, listOf(snappy)).replace("stiffness = 500f", "stiffness = 560f")
        assertTrue(ExportStamp.check(edited, listOf(snappy)) is Verdict.Edited)
    }

    @Test fun `unchanged code but a token edited since is reported as outdated with both values`() {
        val stamped = ExportStamp.wrap(code, Style.SLASH, listOf(snappy))
        val v = ExportStamp.check(stamped, listOf(snappy.copy(stiffness = 650f))) as Verdict.Outdated
        assertEquals(500f, v.changes.single().shipped.stiffness, 0f)
        assertEquals(650f, v.changes.single().now.stiffness, 0f)
    }

    @Test fun `line endings, trailing spaces and surrounding file content do not count as edits`() {
        val stamped = ExportStamp.wrap(code, Style.SLASH, listOf(snappy))
        val inFile = "package app\n\n" + stamped.replace("\n", "  \r\n") + "\nfun unrelated() {}\n"
        assertTrue(ExportStamp.check(inFile, listOf(snappy)) is Verdict.Current)
    }

    @Test fun `css block comments work and code without a stamp says so`() {
        val css = ExportStamp.wrap(":root { --a: 1; }", Style.BLOCK, listOf(snappy))
        assertTrue(css.startsWith("/* motionlab:v1"))
        assertTrue(ExportStamp.check(css, listOf(snappy)) is Verdict.Current)
        assertTrue(ExportStamp.check("just some code", listOf(snappy)) is Verdict.NoStamp)
        assertTrue(ExportStamp.check("// motionlab:v1 springs=t~a~1~1 sig=00000000\nno end marker", emptyList()) is Verdict.NoStamp)
    }

    @Test fun `sets carry several springs and names no longer in the app are listed`() {
        val other = ExportStamp.Spring("t", "gentle", 200f, 1f)
        val stamped = ExportStamp.wrap(code, Style.SLASH, listOf(snappy, other))
        val v = ExportStamp.check(stamped, listOf(snappy)) as Verdict.Current
        assertEquals(listOf("gentle"), v.missing)
        assertEquals(2, v.springs.size)
    }

    @Test fun `a token and an experiment with the same name are not mixed up`() {
        val stamped = ExportStamp.wrap(code, Style.SLASH, listOf(snappy))
        val v = ExportStamp.check(stamped, listOf(snappy.copy(kind = "x", stiffness = 999f))) as Verdict.Current
        assertEquals(listOf("snappy"), v.missing)
    }
}
