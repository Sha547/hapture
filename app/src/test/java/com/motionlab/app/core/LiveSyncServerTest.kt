package com.motionlab.app.core

import com.motionlab.app.core.sync.LiveSyncServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.HttpURLConnection
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
}
