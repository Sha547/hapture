package com.klynstudios.hapture.core.sync

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

/**
 * A tiny HTTP server that serves the motion spec being edited, so a running dev
 * build, the Figma plugin or the browser bridge follows changes without a re-export.
 *
 *  - `GET /`         the bridge page (no data in it, so no pairing needed to load it)
 *  - `POST /pair`    trade the 4-digit code shown on the phone for a session token
 *  - `GET /spec`     the latest spec as JSON                        (needs `?t=token`)
 *  - `GET /exports`  every platform's generated code as JSON         (needs `?t=token`)
 *  - `GET /events`   Server-Sent Events: one `data:` line per change (needs `?t=token`)
 *
 * The pairing code is not real security: it keeps someone else on the same wifi from
 * reading your spec by accident or by curiosity. Five wrong codes lock pairing for
 * 30 seconds and change the code, so it can't be guessed by counting up. Traffic is
 * still plain HTTP on the local network.
 *
 * A connection gets [headerTimeoutMs] to send its request, and the request line and headers are
 * capped, so a client that stalls or floods can't hold a thread. Event streams are long-lived and
 * have no read timeout; instead they get a comment line every [keepAliveMs], which is how a
 * client that went away gets noticed, and they're closed once the token they opened with expires.
 *
 * Plain sockets, no dependency; JVM-only so it is unit-testable.
 */
class LiveSyncServer(
    private val requestedPort: Int = 8787,
    private val bridgeHtml: () -> String = { "<!doctype html><title>Hapture</title><p>Hapture live sync." },
    private val clock: () -> Long = System::currentTimeMillis,
    private val onCodeChange: (String) -> Unit = {},
    private val headerTimeoutMs: Int = HEADER_TIMEOUT_MS,
    private val keepAliveMs: Long = KEEP_ALIVE_MS,
) {
    sealed interface Pairing {
        data class Paired(val token: String) : Pairing
        data object Wrong : Pairing
        data class Locked(val retryMs: Long) : Pairing
    }

    @Volatile private var server: ServerSocket? = null
    @Volatile private var latest: String = "{}"
    @Volatile private var latestExports: String = "{}"
    /** An open `/events` connection and the token it was opened with. Writes to it lock on the stream itself. */
    private class Stream(val socket: Socket, val out: OutputStream, val token: String)

    private val streams = CopyOnWriteArrayList<Stream>()
    private val random = SecureRandom()

    private val lock = Any()
    private var code = ""
    private var failures = 0
    private var lockedUntil = 0L
    private val tokens = HashMap<String, Long>() // token -> expiry

    val port: Int get() = server?.localPort ?: requestedPort
    val running: Boolean get() = server?.isClosed == false
    val pairingCode: String get() = synchronized(lock) { code }

    @Synchronized
    fun start(): Int {
        if (running) return port
        val s = ServerSocket(requestedPort)
        server = s
        newCode()
        synchronized(lock) { tokens.clear(); failures = 0; lockedUntil = 0 }
        thread(isDaemon = true, name = "live-sync-accept") {
            while (!s.isClosed) {
                val c = try { s.accept() } catch (_: Exception) { break }
                thread(isDaemon = true, name = "live-sync-conn") { handle(c) }
            }
        }
        thread(isDaemon = true, name = "live-sync-keepalive") {
            while (!s.isClosed) {
                try { Thread.sleep(keepAliveMs) } catch (_: InterruptedException) { break }
                if (!s.isClosed) keepAlive()
            }
        }
        return s.localPort
    }

    @Synchronized
    fun stop() {
        server?.close()
        server = null
        synchronized(lock) { tokens.clear() }
        streams.forEach { close(it) }
        streams.clear()
    }

    /** Stores [specJson] as the latest spec and pushes it to every open stream. */
    fun publish(specJson: String, exportsJson: String? = null) {
        if (exportsJson != null) latestExports = exportsJson
        if (specJson == latest) return
        latest = specJson
        val frame = sse(specJson).toByteArray()
        streams.forEach { send(it, frame) }
    }

    /**
     * Closes streams whose token has expired and sends the rest an SSE comment (ignored by EventSource),
     * so a client that vanished without closing fails the write and is dropped. Runs every [keepAliveMs].
     */
    internal fun keepAlive() {
        streams.forEach { if (isValid(it.token)) send(it, KEEP_ALIVE) else close(it) }
    }

    /** How many event streams are open right now. */
    internal val streamCount: Int get() = streams.size

    private fun send(stream: Stream, frame: ByteArray) {
        try {
            synchronized(stream) { stream.out.write(frame); stream.out.flush() }
        } catch (_: Exception) {
            close(stream)
        }
    }

    private fun close(stream: Stream) {
        streams.remove(stream)
        runCatching { stream.socket.close() }
    }

    /** Trades a code for a token. Five wrong guesses lock pairing for [LOCK_MS] and rotate the code. */
    fun pair(guess: String): Pairing = synchronized(lock) {
        val now = clock()
        if (now < lockedUntil) return Pairing.Locked(lockedUntil - now)
        if (code.isNotEmpty() && guess == code) {
            failures = 0
            return Pairing.Paired(issue(now))
        }
        failures++
        if (failures >= MAX_FAILURES) {
            failures = 0
            lockedUntil = now + LOCK_MS
            rotateCode()
            return Pairing.Locked(LOCK_MS)
        }
        Pairing.Wrong
    }

    /** A token that skips the code, for a link the person chooses to hand over from the phone. */
    fun issueLinkToken(): String = synchronized(lock) { issue(clock()) }

    fun isValid(token: String?): Boolean = synchronized(lock) {
        if (token == null) return false
        val exp = tokens[token] ?: return false
        if (clock() > exp) { tokens.remove(token); return false }
        true
    }

    private fun issue(now: Long): String {
        tokens.values.removeAll { it < now }
        val token = ByteArray(16).also { random.nextBytes(it) }.joinToString("") { "%02x".format(it) }
        tokens[token] = now + TOKEN_TTL_MS
        return token
    }

    private fun newCode() { synchronized(lock) { rotateCode() } }

    private fun rotateCode() {
        code = "%04d".format(random.nextInt(10_000))
        onCodeChange(code)
    }

    private fun sse(json: String) = json.lines().joinToString("\n", postfix = "\n\n") { "data: ${it.trim()}" }

    private class Request(val method: String, val path: String, val query: Map<String, String>, val body: String)

    /** The request line or headers went over [MAX_LINE] or [MAX_HEADERS]. */
    private class TooLarge : Exception()

    /** One CRLF- or LF-terminated line, without the terminator; null at end of stream. Throws [TooLarge] past [MAX_LINE]. */
    private fun readLine(input: InputStream): String? {
        val buf = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b == -1) return if (buf.size() == 0) null else buf.toString(Charsets.UTF_8.name())
            if (b == '\n'.code) break
            if (buf.size() >= MAX_LINE) throw TooLarge()
            buf.write(b)
        }
        return buf.toString(Charsets.UTF_8.name()).removeSuffix("\r")
    }

    private fun read(c: Socket): Request? {
        val input = BufferedInputStream(c.getInputStream())
        val line = readLine(input) ?: return null
        val parts = line.split(" ")
        var length = 0
        var headers = 0
        while (true) {
            val h = readLine(input) ?: break
            if (h.isEmpty()) break
            if (++headers > MAX_HEADERS) throw TooLarge()
            if (h.startsWith("content-length:", ignoreCase = true)) length = h.substringAfter(':').trim().toIntOrNull() ?: 0
        }
        val body = if (length in 1..MAX_BODY) {
            val bytes = ByteArray(length)
            var got = 0
            while (got < length) {
                val n = input.read(bytes, got, length - got)
                if (n == -1) break
                got += n
            }
            String(bytes, 0, got, Charsets.UTF_8)
        } else ""
        val target = parts.getOrNull(1) ?: "/"
        val query = target.substringAfter('?', "").split('&').filter { it.contains('=') }
            .associate { it.substringBefore('=') to java.net.URLDecoder.decode(it.substringAfter('='), "UTF-8") }
        return Request(parts[0], target.substringBefore('?'), query, body)
    }

    private fun respond(out: OutputStream, status: String, type: String, body: String, extra: String = "") {
        val bytes = body.toByteArray()
        out.write(
            ("HTTP/1.1 $status\r\nContent-Type: $type\r\nAccess-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\nAccess-Control-Allow-Headers: content-type\r\n" +
                "Cache-Control: no-store\r\n${extra}Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray()
        )
        out.write(bytes)
        out.flush()
    }

    private fun handle(c: Socket) {
        try {
            // Only for reading the request: a stream that's started clears it again below.
            c.soTimeout = headerTimeoutMs
            val req = try {
                read(c)
            } catch (_: TooLarge) {
                respond(c.getOutputStream(), "431 Request Header Fields Too Large", "text/plain", "Request too large.\n")
                c.close()
                return
            } ?: run { c.close(); return }
            val out = c.getOutputStream()
            val json = "application/json"
            when {
                req.method == "OPTIONS" -> { respond(out, "204 No Content", "text/plain", ""); c.close() }
                req.path == "/" -> { respond(out, "200 OK", "text/html; charset=utf-8", bridgeHtml()); c.close() }
                req.path == "/pair" && req.method == "POST" -> {
                    val guess = Regex("""\d{4}""").find(req.body)?.value ?: ""
                    when (val r = pair(guess)) {
                        is Pairing.Paired -> respond(out, "200 OK", json, """{"token":"${r.token}"}""")
                        Pairing.Wrong -> respond(out, "403 Forbidden", json, """{"error":"wrong code"}""")
                        is Pairing.Locked -> respond(out, "429 Too Many Requests", json, """{"error":"locked","retryMs":${r.retryMs}}""")
                    }
                    c.close()
                }
                req.path in PROTECTED && !isValid(req.query["t"]) -> {
                    respond(out, "401 Unauthorized", json, """{"error":"pair first"}""")
                    c.close()
                }
                req.path == "/spec" -> { respond(out, "200 OK", json, latest); c.close() }
                req.path == "/exports" -> { respond(out, "200 OK", json, latestExports); c.close() }
                req.path == "/events" -> {
                    c.soTimeout = 0
                    val stream = Stream(c, out, req.query["t"]!!)
                    synchronized(stream) {
                        out.write("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nCache-Control: no-cache\r\nAccess-Control-Allow-Origin: *\r\nConnection: keep-alive\r\n\r\n".toByteArray())
                        out.write(sse(latest).toByteArray())
                        out.flush()
                        streams += stream
                    }
                }
                else -> { respond(out, "404 Not Found", "text/plain", "Not found.\n"); c.close() }
            }
        } catch (_: Exception) {
            runCatching { c.close() }
        }
    }

    companion object {
        const val MAX_FAILURES = 5
        const val LOCK_MS = 30_000L
        const val TOKEN_TTL_MS = 30 * 60_000L
        const val HEADER_TIMEOUT_MS = 10_000
        const val KEEP_ALIVE_MS = 20_000L
        /** Longest request line or header line, in bytes. A bridge link's token query is well under this. */
        const val MAX_LINE = 4096
        const val MAX_HEADERS = 64
        private const val MAX_BODY = 512
        private val KEEP_ALIVE = ": keep-alive\n\n".toByteArray()
        private val PROTECTED = setOf("/spec", "/exports", "/events")
    }
}
