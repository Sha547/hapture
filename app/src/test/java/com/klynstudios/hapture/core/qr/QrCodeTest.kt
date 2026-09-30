package com.klynstudios.hapture.core.qr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class QrCodeTest {
    private val link = "http://192.168.100.100:8787/?t=0123456789abcdef0123456789abcdef"

    @Test fun `size follows the version and short text stays small`() {
        val q = QrCode.encode("hi")!!
        assertEquals(1, q.version)
        assertEquals(21, q.size)
        val l = QrCode.encode(link)!!
        assertEquals(17 + 4 * l.version, l.size)
    }

    @Test fun `a longer payload picks a bigger version and too much is refused`() {
        assertTrue(QrCode.encode("x".repeat(150))!!.version > QrCode.encode(link)!!.version)
        assertNull(QrCode.encode("x".repeat(400)))
    }

    @Test fun `the three finder patterns and the timing lines are where they must be`() {
        val q = QrCode.encode(link)!!
        val d = q.dark
        for ((ox, oy) in listOf(0 to 0, q.size - 7 to 0, 0 to q.size - 7)) {
            for (i in 0 until 7) for (j in 0 until 7) {
                val ring = maxOf(Math.abs(i - 3), Math.abs(j - 3))
                assertEquals("finder ($ox,$oy) $i,$j", ring != 2, d[oy + j][ox + i])
            }
        }
        for (i in 8 until q.size - 8) assertEquals(i % 2 == 0, d[6][i])
        assertTrue("dark module", d[q.size - 8][8])
    }

    @Test fun `same text gives the same code`() {
        assertTrue(QrCode.encode(link)!!.dark.contentDeepEquals(QrCode.encode(link)!!.dark))
    }

    /** Writes real images so an independent decoder can confirm they scan (see the build folder). */
    @Test fun `write sample images for an external decoder`() {
        val dir = File("build/qr-check").apply { mkdirs() }
        val samples = listOf(
            "short" to "hello", "link" to link, "long-ip" to "http://10.20.30.40:8787/?t=" + "ab".repeat(16),
            "v7plus" to "https://example.com/" + "q".repeat(110), "v10" to "z".repeat(190) + "é", "low-max" to "m".repeat(200),
        )
        samples.forEach { (name, text) ->
            listOf(QrCode.Companion.Ecc.LOW, QrCode.Companion.Ecc.MEDIUM).forEach { ecc ->
                val q = QrCode.encode(text, ecc) ?: return@forEach
                val scale = 8; val quiet = 4
                val px = (q.size + 2 * quiet) * scale
                val sb = StringBuilder("P1\n$px $px\n")
                for (y in 0 until px) {
                    for (x in 0 until px) {
                        val mx = x / scale - quiet; val my = y / scale - quiet
                        sb.append(if (mx in 0 until q.size && my in 0 until q.size && q.dark[my][mx]) '1' else '0').append(' ')
                    }
                    sb.append('\n')
                }
                File(dir, "$name-${ecc.name}-v${q.version}.pbm").writeText(sb.toString())
                File(dir, "$name-${ecc.name}-v${q.version}.txt").writeText(text)
            }
        }
        assertNotNull(dir.listFiles())
    }
}
