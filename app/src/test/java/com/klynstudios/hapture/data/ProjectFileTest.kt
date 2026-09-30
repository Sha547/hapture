package com.klynstudios.hapture.data

import com.klynstudios.hapture.core.haptics.HapticPreset
import com.klynstudios.hapture.core.model.CustomPoint
import com.klynstudios.hapture.core.model.CustomShapeCodec
import com.klynstudios.hapture.core.model.ObjectFill
import com.klynstudios.hapture.core.model.ObjectShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectFileTest {

    private fun spring() = ExperimentEntity(
        id = 7, type = ExperimentType.SPRING_DRAG, name = "Spring \"Drag\" 2", createdAt = 1, updatedAt = 2,
        stiffnessT = 0.8f, dampingT = 0.3f, resistanceT = 0.5f,
        objectShape = ObjectShape.PILL, objectSizeT = 0.7f, objectFill = ObjectFill.MARKER, hapticPreset = HapticPreset.FIRM,
    )

    private fun magnet() = ExperimentEntity(
        type = ExperimentType.MAGNETIC_SNAP, name = "Magnet", createdAt = 1, updatedAt = 2,
        stiffnessT = 0.4f, dampingT = 0.6f, magneticStrengthT = 0.46f, magneticThresholdT = 0.6f,
    )

    private fun swipe() = ExperimentEntity(
        type = ExperimentType.SWIPE_FLING, name = "Swipe", createdAt = 1, updatedAt = 2,
        stiffnessT = 0.5f, dampingT = 0.6f,
        flingDistanceT = 0.4f, flingSensitivityT = 0.5f, flingFrictionT = 0.35f, flingTiltT = 0.4f,
        hapticPreset = HapticPreset.OFF,
    )

    private fun sheet() = ExperimentEntity(
        type = ExperimentType.BOTTOM_SHEET, name = "Sheet", createdAt = 1, updatedAt = 2,
        stiffnessT = 0.45f, dampingT = 0.65f, resistanceT = 0.5f,
        sheetPeekT = 0.4f, sheetMidT = 0.5f, sheetMomentumT = 0.5f, sheetDismissT = 0.5f,
        objectShape = ObjectShape.CIRCLE, objectFill = ObjectFill.MARKER,
    )

    @Test
    fun aFileSavedBeforeTheRenameStillImports() {
        val old = ProjectFile.encode(spring()).replace("\"format\": \"hapture\"", "\"format\": \"motionlab\"")
        assertTrue(old.contains("\"motionlab\""))
        assertEquals(spring().stiffnessT, ok(old).stiffnessT, 1e-4f)
    }

    private fun ok(text: String): ExperimentEntity =
        (ProjectFile.decode(text, now = 99) as ProjectFile.Result.Ok).entity

    private fun err(text: String): String =
        (ProjectFile.decode(text) as ProjectFile.Result.Error).reason

    @Test fun `every interaction type survives a round trip`() {
        for (e in listOf(spring(), magnet(), swipe(), sheet())) {
            val back = ok(ProjectFile.encode(e))
            assertEquals(e.copy(id = 0, createdAt = 99, updatedAt = 99), back)
        }
    }

    @Test fun `decode stamps fresh times and leaves the id for the database to assign`() {
        val back = ok(ProjectFile.encode(spring()))
        assertEquals(0L, back.id)
        assertEquals(99L, back.createdAt)
        assertEquals(99L, back.updatedAt)
    }

    @Test fun `encoded file is stable and self describing`() {
        val text = ProjectFile.encode(spring())
        assertEquals(text, ProjectFile.encode(spring()))
        assertTrue(text.contains("\"format\": \"hapture\""))
        assertTrue(text.contains("\"version\": 1"))
    }

    @Test fun `things that are not experiments are refused with a reason`() {
        assertTrue(err("hello").contains("not a Hapture"))
        assertTrue(err("").contains("not a Hapture"))
        assertTrue(err("[1,2,3]").contains("not a Hapture"))
        assertTrue(err("""{"format":"something-else","version":1}""").contains("not a Hapture"))
    }

    @Test fun `newer or invalid versions are declined not guessed at`() {
        val base = ProjectFile.encode(magnet())
        assertTrue(err(base.replace("\"version\": 1", "\"version\": 2")).contains("newer version"))
        assertTrue(err(base.replace("\"version\": 1", "\"version\": 0")).contains("version"))
        assertTrue(err(base.replace("\"version\": 1,", "")).contains("version"))
    }

    @Test fun `unknown interaction types and missing parameters are refused`() {
        val base = ProjectFile.encode(swipe())
        assertTrue(err(base.replace("SWIPE_FLING", "TELEPORT")).contains("interaction"))
        assertTrue(err(base.replace("flingFrictionT", "renamed")).contains("friction"))
        assertTrue(err(base.replace("stiffnessT", "renamed")).contains("stiffness"))
        assertTrue(err("""{"format":"hapture","version":1,"type":"SPRING_DRAG"}""").contains("parameters"))
    }

    @Test fun `a sheet file missing any of its own parameters is refused`() {
        val base = ProjectFile.encode(sheet())
        for ((key, word) in listOf("sheetPeekT" to "peek", "sheetMidT" to "middle", "sheetMomentumT" to "momentum", "sheetDismissT" to "dismiss")) {
            assertTrue(key, err(base.replace(key, "renamed")).contains(word))
        }
    }

    @Test fun `out of range and non numeric values are clamped or refused`() {
        val wild = ProjectFile.encode(spring()).replace("\"stiffnessT\": 0.8", "\"stiffnessT\": 7.5")
        assertEquals(1f, ok(wild).stiffnessT, 0f)
        val negative = ProjectFile.encode(spring()).replace("\"dampingT\": 0.3", "\"dampingT\": -4")
        assertEquals(0f, ok(negative).dampingT, 0f)
        val nan = ProjectFile.encode(spring()).replace("\"stiffnessT\": 0.8", "\"stiffnessT\": \"NaN\"")
        assertTrue(err(nan).contains("stiffness"))
    }

    @Test fun `oversized files are refused before parsing`() {
        val big = ProjectFile.encode(spring()).replace("Spring", "x".repeat(ProjectFile.MAX_BYTES))
        assertTrue(err(big).contains("too large"))
    }

    @Test fun `names are trimmed, capped and never empty`() {
        val named = ProjectFile.encode(spring()).replace("Spring \\\"Drag\\\" 2", "   ${"n".repeat(200)}   ")
        assertEquals(60, ok(named).name.length)
        val blank = ProjectFile.encode(spring()).replace("Spring \\\"Drag\\\" 2", "   ")
        assertEquals("Imported experiment", ok(blank).name)
        assertEquals("Spring \"Drag\" 2", ok(ProjectFile.encode(spring())).name)
    }

    @Test fun `unknown look values fall back and a missing object block uses defaults`() {
        val old = ProjectFile.encode(spring()).replace("PILL", "STAR").replace("MARKER", "NEON").replace("FIRM", "LOUD")
        val e = ok(old)
        assertEquals(ObjectShape.ROUNDED, e.objectShape)
        assertEquals(ObjectFill.INK, e.objectFill)
        assertEquals(HapticPreset.CRISP, e.hapticPreset)

        val minimal = """{"format":"hapture","version":1,"type":"SPRING_DRAG","params":{"stiffnessT":0.5,"dampingT":0.5,"resistanceT":0.5}}"""
        val m = ok(minimal)
        assertEquals(ObjectShape.ROUNDED, m.objectShape)
        assertEquals("Imported experiment", m.name)
    }

    @Test fun `a custom drawn shape round trips through the file`() {
        val drawn = listOf(CustomPoint(0.1f, 0.2f), CustomPoint(0.8f, 0.15f), CustomPoint(0.5f, 0.9f))
        val e = spring().copy(objectShape = ObjectShape.CUSTOM, customShapePath = CustomShapeCodec.encode(drawn))
        val text = ProjectFile.encode(e)
        assertTrue(text.contains("\"customPath\""))

        val back = ok(text)
        assertEquals(ObjectShape.CUSTOM, back.objectShape)
        val decoded = CustomShapeCodec.decode(back.customShapePath)
        assertEquals(drawn.size, decoded.size)
        for (i in drawn.indices) {
            assertEquals(drawn[i].x, decoded[i].x, 1e-3f)
            assertEquals(drawn[i].y, decoded[i].y, 1e-3f)
        }
    }

    @Test fun `a corrupted custom path is sanitized rather than refused`() {
        val e = spring().copy(objectShape = ObjectShape.CUSTOM, customShapePath = "0.1,0.2;0.9,0.1")
        val corrupted = ProjectFile.encode(e).replace("0.1,0.2;0.9,0.1", "0.1,0.2;garbage;9,-9")
        val back = ok(corrupted)
        val decoded = CustomShapeCodec.decode(back.customShapePath)
        assertEquals(listOf(CustomPoint(0.1f, 0.2f), CustomPoint(1f, 0f)), decoded)
    }

    @Test fun `no custom path is omitted from the file entirely`() {
        assertTrue(!ProjectFile.encode(spring()).contains("customPath"))
    }

    @Test fun `file names are safe and carry the extension`() {
        assertEquals("Spring-Drag-2.hapture", ProjectFile.fileName("Spring Drag 2"))
        assertEquals("a-b.hapture", ProjectFile.fileName("../a/b\\"))
        assertEquals("experiment.hapture", ProjectFile.fileName("   "))
    }

    private fun chained() = ExperimentEntity(
        type = ExperimentType.BUTTON_PRESS, name = "Press", createdAt = 1, updatedAt = 2, stiffnessT = 0.5f, dampingT = 0.6f,
        triggerAmountT = 0.5f, triggerStaggerT = 0.4f,
        chainProperty = "opacity", chainAtT = 0.3f, chainLagT = 0.25f, chainStiffnessT = 0.7f, chainDampingT = 0.4f,
    )

    @Test fun `a chained follower survives export and import`() {
        val back = (ProjectFile.decode(ProjectFile.encode(chained())) as ProjectFile.Result.Ok).entity
        assertEquals("opacity", back.chainProperty)
        assertEquals(0.3f, back.chainAtT!!, 1e-4f)
        assertEquals(0.25f, back.chainLagT!!, 1e-4f)
        assertEquals(0.7f, back.chainStiffnessT!!, 1e-4f)
        assertEquals(0.4f, back.chainDampingT!!, 1e-4f)
    }

    @Test fun `an unchained experiment and an unknown follower property both come back without a chain`() {
        val plain = (ProjectFile.decode(ProjectFile.encode(chained().copy(chainProperty = null))) as ProjectFile.Result.Ok).entity
        assertEquals(null, plain.chainProperty)
        val odd = ProjectFile.encode(chained()).replace("\"chainProperty\": \"opacity\"", "\"chainProperty\": \"glow\"")
        assertEquals(null, (ProjectFile.decode(odd) as ProjectFile.Result.Ok).entity.chainProperty)
    }
}
