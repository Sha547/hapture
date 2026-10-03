package com.klynstudios.hapture.core

import com.klynstudios.hapture.core.sync.LiveSyncServer
import com.klynstudios.hapture.core.sync.isPrivateIpv4
import com.klynstudios.hapture.core.sync.rankAddresses
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.Socket
import java.net.URL

class LiveSyncServerTest {

    private fun open(port: Int, path: String, method: String = "GET", body: String? = null): HttpURLConnection {
        val c = URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
        c.requestMethod = method
        if (body != null) { c.doOutput = true; c.outputStream.write(body.toByteArray()) }
        return c
    }

    private fun get(port: Int, path: String): Pair<Int, String> {
        val c = open(port, path)
        val code = c.responseCode
        return code to (if (code < 400) c.inputStream else c.errorStream).bufferedReader().readText()
    }

    private fun pairOver(port: Int, guess: String) = open(port, "/pair", "POST", guess).let {
        val code = it.responseCode
        code to (if (code < 400) it.inputStream else it.errorStream).bufferedReader().readText()
    }

    private fun token(s: LiveSyncServer, port: Int): String {
        val (code, body) = pairOver(port, s.pairingCode)
        assertEquals(200, code)
        return Regex(""""token":"(\w+)"""").find(body)!!.groupValues[1]
    }

    @Test fun `spec endpoint serves the latest published spec once paired`() {
        val s = LiveSyncServer(0)
        val port = s.start()
        try {
            val t = token(s, port)
            assertEquals("{}", get(port, "/spec?t=$t").second)
            s.publish("""{"spring":{"stiffness":400}}""")
            val (code, body) = get(port, "/spec?t=$t")
            assertEquals(200, code)
            assertEquals("""{"spring":{"stiffness":400}}""", body)
        } finally { s.stop() }
    }

    @Test fun `data endpoints refuse a missing or wrong token`() {
        val s = LiveSyncServer(0)
        val port = s.start()
        try {
            for (p in listOf("/spec", "/exports", "/events")) {
                assertEquals(401, get(port, p).first)
                assertEquals(401, get(port, "$p?t=deadbeef").first)
            }
        } finally { s.stop() }
    }

    @Test fun `the bridge page loads without pairing and carries no spec`() {
        val s = LiveSyncServer(0, bridgeHtml = { "<html>bridge</html>" })
        val port = s.start()
        try {
            s.publish("""{"secret":1}""")
            val (code, body) = get(port, "/")
            assertEquals(200, code)
            assertEquals("<html>bridge</html>", body)
        } finally { s.stop() }
    }

    @Test fun `exports are served alongside the spec`() {
        val s = LiveSyncServer(0)
        val port = s.start()
        try {
            val t = token(s, port)
            s.publish("""{"n":1}""", """{"css":"a"}""")
            assertEquals("""{"css":"a"}""", get(port, "/exports?t=$t").second)
        } finally { s.stop() }
    }

    @Test fun `wrong code is refused and five of them lock pairing and change the code`() {
        var now = 1_000L
        val s = LiveSyncServer(0, clock = { now })
        val port = s.start()
        try {
            val first = s.pairingCode
            val wrong = if (first == "0000") "0001" else "0000"
            repeat(4) { assertEquals(403, pairOver(port, wrong).first) }
            assertEquals(429, pairOver(port, wrong).first)
            // Even the right code is refused while locked; and it has changed since.
            assertEquals(429, pairOver(port, s.pairingCode).first)
            now += LiveSyncServer.LOCK_MS + 1
            assertEquals(200, pairOver(port, s.pairingCode).first)
        } finally { s.stop() }
    }

    @Test fun `each start gets a code and tokens do not outlive stop or their lifetime`() {
        var now = 0L
        val s = LiveSyncServer(0, clock = { now })
        val port = s.start()
        val t = token(s, port)
        assertTrue(s.isValid(t))
        now += LiveSyncServer.TOKEN_TTL_MS + 1
        assertTrue(!s.isValid(t))
        val t2 = s.issueLinkToken()
        assertTrue(s.isValid(t2))
        s.stop()
        assertTrue(!s.isValid(t2))
        assertNotEquals("", s.pairingCode)
    }

    @Test fun `events stream pushes the current spec then every change`() {
        val s = LiveSyncServer(0)
        val port = s.start()
        try {
            val t = token(s, port)
            s.publish("""{"n":1}""")
            val c = URL("http://127.0.0.1:$port/events?t=$t").openConnection() as HttpURLConnection
            c.readTimeout = 4000
            val r = c.inputStream.bufferedReader()
            assertEquals("""data: {"n":1}""", r.readLine())
            r.readLine()
            Thread.sleep(150)
            s.publish("""{"n":2}""")
            assertEquals("""data: {"n":2}""", r.readLine())
        } finally { s.stop() }
    }

    @Test fun `stopping releases the port`() {
        val s = LiveSyncServer(0)
        s.start()
        assertTrue(s.running)
        s.stop()
        assertTrue(!s.running)
    }

    /** Writes [request] on a raw socket and returns everything the server sends before closing it. */
    private fun raw(port: Int, request: String, waitMs: Int = 3000): String = Socket("127.0.0.1", port).use { sock ->
        sock.soTimeout = waitMs
        sock.getOutputStream().apply { write(request.toByteArray()); flush() }
        sock.getInputStream().readBytes().decodeToString()
    }

    @Test fun `a client that never finishes its request is cut off after the header timeout`() {
        val s = LiveSyncServer(0, headerTimeoutMs = 200)
        val port = s.start()
        try {
            val started = System.nanoTime()
            // No blank line: the server is still waiting for headers when the timeout hits. readBytes() returning
            // (instead of this socket's own 3 s timeout throwing) means the server closed the connection.
            assertEquals("", raw(port, "GET /spec HTTP/1.1\r\nHost: x\r\n"))
            val tookMs = (System.nanoTime() - started) / 1_000_000
            assertTrue("closed after $tookMs ms", tookMs in 150..2500)
        } finally { s.stop() }
    }

    @Test fun `an event stream outlives the header timeout`() {
        val s = LiveSyncServer(0, headerTimeoutMs = 200, keepAliveMs = 60_000)
        val port = s.start()
        try {
            val t = token(s, port)
            val c = URL("http://127.0.0.1:$port/events?t=$t").openConnection() as HttpURLConnection
            c.readTimeout = 4000
            val r = c.inputStream.bufferedReader()
            assertEquals("data: {}", r.readLine())
            r.readLine()
            Thread.sleep(600) // three header timeouts with nothing sent either way
            s.publish("""{"n":3}""")
            assertEquals("""data: {"n":3}""", r.readLine())
        } finally { s.stop() }
    }

    @Test fun `an over-long request line or too many headers is refused`() {
        val s = LiveSyncServer(0)
        val port = s.start()
        try {
            val longLine = "GET /" + "a".repeat(LiveSyncServer.MAX_LINE) + " HTTP/1.1\r\n\r\n"
            assertTrue(raw(port, longLine).startsWith("HTTP/1.1 431"))
            val longHeader = "GET / HTTP/1.1\r\nX: " + "b".repeat(LiveSyncServer.MAX_LINE) + "\r\n\r\n"
            assertTrue(raw(port, longHeader).startsWith("HTTP/1.1 431"))
            val manyHeaders = "GET / HTTP/1.1\r\n" + (1..LiveSyncServer.MAX_HEADERS + 1).joinToString("") { "X-$it: y\r\n" } + "\r\n"
            assertTrue(raw(port, manyHeaders).startsWith("HTTP/1.1 431"))
            // Right at the limits is still fine.
            val atLimit = "GET / HTTP/1.1\r\n" + (1..LiveSyncServer.MAX_HEADERS).joinToString("") { "X-$it: y\r\n" } + "\r\n"
            assertTrue(raw(port, atLimit).startsWith("HTTP/1.1 200"))
        } finally { s.stop() }
    }

    @Test fun `streams get a keep-alive comment and close when their token expires`() {
        var now = 0L
        val s = LiveSyncServer(0, clock = { now }, keepAliveMs = 100)
        val port = s.start()
        try {
            val t = token(s, port)
            val c = URL("http://127.0.0.1:$port/events?t=$t").openConnection() as HttpURLConnection
            c.readTimeout = 4000
            val r = c.inputStream.bufferedReader()
            assertEquals("data: {}", r.readLine())
            assertEquals("", r.readLine())
            assertEquals(": keep-alive", r.readLine())
            assertEquals(1, s.streamCount)

            now += LiveSyncServer.TOKEN_TTL_MS + 1
            // The next keep-alive tick sees the expired token and closes the stream; any keep-alives already in
            // flight come first, then end of stream (not the reader's 4 s timeout).
            var line = r.readLine()
            while (line != null && (line == ": keep-alive" || line.isEmpty())) line = r.readLine()
            assertEquals(null, line)
            assertEquals(0, s.streamCount)
        } finally { s.stop() }
    }

    @Test fun `addresses prefer the local network and skip mobile data`() {
        assertTrue(isPrivateIpv4("192.168.1.20"))
        assertTrue(isPrivateIpv4("10.0.0.5"))
        assertTrue(isPrivateIpv4("172.16.0.1"))
        assertTrue(isPrivateIpv4("172.31.255.1"))
        assertFalse(isPrivateIpv4("172.32.0.1"))
        assertFalse(isPrivateIpv4("100.64.3.2"))
        assertFalse(isPrivateIpv4("not an ip"))

        val ranked = rankAddresses(
            listOf(
                "rmnet_data0" to "10.120.4.9",   // carrier NAT, private range but cellular
                "v4-rmnet_data0" to "192.0.0.4",
                "tun0" to "100.64.0.2",
                "dummy0" to "192.168.200.1",
                "p2p0" to "172.20.0.3",
                "wlan0" to "192.168.1.20",
            )
        )
        assertEquals(listOf("192.168.1.20", "172.20.0.3", "100.64.0.2"), ranked)
    }
}
