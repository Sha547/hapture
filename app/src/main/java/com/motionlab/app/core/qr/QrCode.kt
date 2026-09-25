package com.motionlab.app.core.qr

/**
 * A small QR code encoder: byte mode, error correction L or M, versions 1 to 10 (up to 213 bytes at L),
 * which is plenty for a bridge link. No dependency, so it works offline and is unit-testable.
 * [dark] is the module grid, `dark[y][x]`, without the quiet zone.
 */
class QrCode private constructor(val version: Int, val dark: Array<BooleanArray>) {
    val size: Int get() = dark.size

    companion object {
        enum class Ecc(val formatBits: Int, val perBlock: IntArray, val blocks: IntArray) {
            LOW(1, intArrayOf(0, 7, 10, 15, 20, 26, 18, 20, 24, 30, 18), intArrayOf(0, 1, 1, 1, 1, 1, 2, 2, 2, 2, 4)),
            MEDIUM(0, intArrayOf(0, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26), intArrayOf(0, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5)),
        }

        const val MAX_VERSION = 10

        /** Null if [text] doesn't fit in version 10 at the chosen level. */
        fun encode(text: String, ecc: Ecc = Ecc.MEDIUM): QrCode? {
            val data = text.toByteArray(Charsets.UTF_8)
            for (v in 1..MAX_VERSION) {
                val capacity = dataCodewords(v, ecc)
                val countBits = if (v <= 9) 8 else 16
                if (4 + countBits + data.size * 8 <= capacity * 8) return build(v, ecc, data)
            }
            return null
        }

        private fun rawModules(v: Int): Int {
            var r = (16 * v + 128) * v + 64
            if (v >= 2) { val a = v / 7 + 2; r -= (25 * a - 10) * a - 55; if (v >= 7) r -= 36 }
            return r
        }

        private fun dataCodewords(v: Int, ecc: Ecc) = rawModules(v) / 8 - ecc.perBlock[v] * ecc.blocks[v]

        private fun build(v: Int, ecc: Ecc, data: ByteArray): QrCode {
            val bits = ArrayList<Int>()
            fun put(value: Int, n: Int) { for (i in n - 1 downTo 0) bits += (value shr i) and 1 }
            put(0b0100, 4)
            put(data.size, if (v <= 9) 8 else 16)
            data.forEach { put(it.toInt() and 0xFF, 8) }
            val capBits = dataCodewords(v, ecc) * 8
            put(0, minOf(4, capBits - bits.size))
            while (bits.size % 8 != 0) bits += 0
            var pad = 0xEC
            while (bits.size < capBits) { put(pad, 8); pad = pad xor (0xEC xor 0x11) }
            val codewords = ByteArray(bits.size / 8) { i -> (0 until 8).fold(0) { a, j -> (a shl 1) or bits[i * 8 + j] }.toByte() }

            val all = interleave(codewords, v, ecc)
            val size = 17 + 4 * v
            val modules = Array(size) { BooleanArray(size) }
            val isFunction = Array(size) { BooleanArray(size) }
            drawFunctionPatterns(modules, isFunction, v)
            placeData(modules, isFunction, all)

            var best = -1
            var bestScore = Int.MAX_VALUE
            for (m in 0..7) {
                applyMask(modules, isFunction, m)
                drawFormat(modules, isFunction, ecc, m)
                val s = penalty(modules)
                if (s < bestScore) { bestScore = s; best = m }
                applyMask(modules, isFunction, m) // undo
            }
            applyMask(modules, isFunction, best)
            drawFormat(modules, isFunction, ecc, best)
            return QrCode(v, modules)
        }

        // --- Reed-Solomon over GF(256), polynomial 0x11D ---

        private fun gfMul(x: Int, y: Int): Int {
            var z = 0
            for (i in 7 downTo 0) { z = (z shl 1) xor ((z ushr 7) * 0x11D); z = z xor (((y ushr i) and 1) * x) }
            return z
        }

        private fun divisor(degree: Int): IntArray {
            val r = IntArray(degree); r[degree - 1] = 1
            var root = 1
            repeat(degree) {
                for (j in r.indices) { r[j] = gfMul(r[j], root); if (j + 1 < r.size) r[j] = r[j] xor r[j + 1] }
                root = gfMul(root, 2)
            }
            return r
        }

        private fun remainder(data: ByteArray, div: IntArray): IntArray {
            val r = IntArray(div.size)
            for (b in data) {
                val f = (b.toInt() and 0xFF) xor r[0]
                for (i in 0 until r.size - 1) r[i] = r[i + 1]
                r[r.size - 1] = 0
                for (i in r.indices) r[i] = r[i] xor gfMul(div[i], f)
            }
            return r
        }

        private fun interleave(data: ByteArray, v: Int, ecc: Ecc): ByteArray {
            val nBlocks = ecc.blocks[v]
            val eccLen = ecc.perBlock[v]
            val raw = rawModules(v) / 8
            val shortLen = raw / nBlocks
            val numShort = nBlocks - raw % nBlocks
            val div = divisor(eccLen)
            val blocks = ArrayList<ByteArray>()
            var k = 0
            for (i in 0 until nBlocks) {
                val len = shortLen - eccLen + if (i < numShort) 0 else 1
                val dat = data.copyOfRange(k, k + len); k += len
                val e = remainder(dat, div)
                val block = ByteArray(shortLen + 1)
                dat.copyInto(block)
                // Short blocks keep a hole at index len so every block has the same length.
                val eccStart = shortLen + 1 - eccLen
                for (j in 0 until eccLen) block[eccStart + j] = e[j].toByte()
                blocks += block
            }
            val out = ArrayList<Byte>()
            for (i in 0 until shortLen + 1) for ((j, b) in blocks.withIndex()) {
                if (i != shortLen - eccLen || j >= numShort) out += b[i]
            }
            return out.toByteArray()
        }

        // --- Layout ---

        private fun set(m: Array<BooleanArray>, f: Array<BooleanArray>, x: Int, y: Int, dark: Boolean) { m[y][x] = dark; f[y][x] = true }

        private fun drawFunctionPatterns(m: Array<BooleanArray>, f: Array<BooleanArray>, v: Int) {
            val size = m.size
            for (i in 0 until size) { set(m, f, 6, i, i % 2 == 0); set(m, f, i, 6, i % 2 == 0) }
            fun finder(cx: Int, cy: Int) {
                for (dy in -4..4) for (dx in -4..4) {
                    val x = cx + dx; val y = cy + dy
                    if (x in 0 until size && y in 0 until size) set(m, f, x, y, maxOf(Math.abs(dx), Math.abs(dy)) !in intArrayOf(2, 4))
                }
            }
            finder(3, 3); finder(size - 4, 3); finder(3, size - 4)
            val pos = alignmentPositions(v)
            for (i in pos.indices) for (j in pos.indices) {
                if ((i == 0 && j == 0) || (i == 0 && j == pos.size - 1) || (i == pos.size - 1 && j == 0)) continue
                for (dy in -2..2) for (dx in -2..2) set(m, f, pos[i] + dx, pos[j] + dy, maxOf(Math.abs(dx), Math.abs(dy)) != 1)
            }
            // Reserve format areas now; real bits are drawn once the mask is known.
            drawFormat(m, f, Ecc.LOW, 0)
            if (v >= 7) {
                var rem = v
                repeat(12) { rem = (rem shl 1) xor ((rem ushr 11) * 0x1F25) }
                val bits = (v shl 12) or rem
                for (i in 0 until 18) {
                    val b = (bits ushr i) and 1 == 1
                    val a = size - 11 + i % 3; val c = i / 3
                    set(m, f, a, c, b); set(m, f, c, a, b)
                }
            }
        }

        private fun alignmentPositions(v: Int): IntArray {
            if (v == 1) return IntArray(0)
            val n = v / 7 + 2
            val size = 17 + 4 * v
            val step = if (v == 32) 26 else (v * 4 + n * 2 + 1) / (n * 2 - 2) * 2
            val r = IntArray(n)
            r[0] = 6
            var p = size - 7
            for (i in n - 1 downTo 1) { r[i] = p; p -= step }
            return r
        }

        private fun drawFormat(m: Array<BooleanArray>, f: Array<BooleanArray>, ecc: Ecc, mask: Int) {
            val size = m.size
            val data = (ecc.formatBits shl 3) or mask
            var rem = data
            repeat(10) { rem = (rem shl 1) xor ((rem ushr 9) * 0x537) }
            val bits = ((data shl 10) or rem) xor 0x5412
            fun bit(i: Int) = (bits ushr i) and 1 == 1
            for (i in 0..5) set(m, f, 8, i, bit(i))
            set(m, f, 8, 7, bit(6)); set(m, f, 8, 8, bit(7)); set(m, f, 7, 8, bit(8))
            for (i in 9..14) set(m, f, 14 - i, 8, bit(i))
            for (i in 0..7) set(m, f, size - 1 - i, 8, bit(i))
            for (i in 8..14) set(m, f, 8, size - 15 + i, bit(i))
            set(m, f, 8, size - 8, true)
        }

        private fun placeData(m: Array<BooleanArray>, f: Array<BooleanArray>, data: ByteArray) {
            val size = m.size
            var i = 0
            var right = size - 1
            while (right >= 1) {
                if (right == 6) right = 5
                for (vert in 0 until size) for (j in 0..1) {
                    val x = right - j
                    val upward = ((right + 1) and 2) == 0
                    val y = if (upward) size - 1 - vert else vert
                    if (!f[y][x] && i < data.size * 8) {
                        m[y][x] = ((data[i ushr 3].toInt() ushr (7 - (i and 7))) and 1) == 1
                        i++
                    }
                }
                right -= 2
            }
        }

        private fun applyMask(m: Array<BooleanArray>, f: Array<BooleanArray>, mask: Int) {
            for (y in m.indices) for (x in m.indices) {
                if (f[y][x]) continue
                val invert = when (mask) {
                    0 -> (x + y) % 2 == 0
                    1 -> y % 2 == 0
                    2 -> x % 3 == 0
                    3 -> (x + y) % 3 == 0
                    4 -> (x / 3 + y / 2) % 2 == 0
                    5 -> x * y % 2 + x * y % 3 == 0
                    6 -> (x * y % 2 + x * y % 3) % 2 == 0
                    else -> ((x + y) % 2 + x * y % 3) % 2 == 0
                }
                if (invert) m[y][x] = !m[y][x]
            }
        }

        private fun penalty(m: Array<BooleanArray>): Int {
            val n = m.size
            var score = 0
            fun runs(get: (Int, Int) -> Boolean) {
                for (a in 0 until n) {
                    var run = 1
                    for (b in 1 until n) {
                        if (get(a, b) == get(a, b - 1)) run++ else { if (run >= 5) score += 3 + run - 5; run = 1 }
                    }
                    if (run >= 5) score += 3 + run - 5
                }
            }
            runs { a, b -> m[a][b] }
            runs { a, b -> m[b][a] }
            for (y in 0 until n - 1) for (x in 0 until n - 1) {
                val c = m[y][x]
                if (c == m[y][x + 1] && c == m[y + 1][x] && c == m[y + 1][x + 1]) score += 3
            }
            val p1 = booleanArrayOf(true, false, true, true, true, false, true, false, false, false, false)
            val p2 = p1.reversedArray()
            fun finderLike(get: (Int, Int) -> Boolean) {
                for (a in 0 until n) for (b in 0..n - 11) {
                    var m1 = true; var m2 = true
                    for (k in 0 until 11) { val c = get(a, b + k); if (c != p1[k]) m1 = false; if (c != p2[k]) m2 = false }
                    if (m1) score += 40
                    if (m2) score += 40
                }
            }
            finderLike { a, b -> m[a][b] }
            finderLike { a, b -> m[b][a] }
            val darkCount = m.sumOf { row -> row.count { it } }
            val total = n * n
            val k = (Math.abs(darkCount * 20 - total * 10) + total - 1) / total - 1
            return score + maxOf(k, 0) * 10
        }
    }
}
