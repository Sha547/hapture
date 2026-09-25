package com.motionlab.app.core.sync

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.net.Inet4Address
import java.net.NetworkInterface

/** App-wide live-sync switch: one server, off until someone turns it on. */
object LiveSync {
    @Volatile private var bridge = "<!doctype html><title>Motion Lab</title><p>Bridge page missing from this build."
    private val server = LiveSyncServer(bridgeHtml = { bridge }, onCodeChange = { code = it })
    var running by mutableStateOf(false)
        private set

    /** The 4-digit code to type into the Figma plugin or bridge page; changes if pairing gets locked. */
    var code by mutableStateOf("")
        private set

    fun start(context: Context): Boolean = try {
        bridge = context.assets.open("bridge.html").bufferedReader().use { it.readText() }
        server.start(); running = true; true
    } catch (_: Exception) { running = false; false }

    /** A link that opens the bridge already paired. Anyone holding it can read the spec until sync stops or 30 minutes pass. */
    fun bridgeLink(host: String): String = "http://$host:$port/?t=${server.issueLinkToken()}"

    fun stop() { server.stop(); running = false }

    /** Compacted to one line so a plain `data:` line reader gets a whole spec per event. */
    fun publish(specJson: String, exportsJson: String? = null) {
        if (!running) return
        val compact = try { org.json.JSONObject(specJson).toString() } catch (_: Exception) { specJson }
        server.publish(compact, exportsJson)
    }

    val port: Int get() = server.port

    /** This device's IPv4 addresses on non-loopback interfaces, as people would type them into a browser. */
    fun addresses(): List<String> = try {
        NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }.filterIsInstance<Inet4Address>().map { it.hostAddress ?: "" }.filter { it.isNotEmpty() }
    } catch (_: Exception) { emptyList() }

    fun clientSnippet(host: String): String = """
        |// Motion Lab live sync: apply the spring the moment it changes in the app.
        |// 1. Pair once with the 4-digit code shown in the app (every request after needs the token).
        |// 2. Follow the stream.
        |
        |// --- JavaScript / React / React Native (EventSource) ---
        |const { token } = await (await fetch("http://$host:$port/pair", { method: "POST", body: "<code shown in the app>" })).json();
        |const es = new EventSource("http://$host:$port/events?t=" + token);
        |es.onmessage = (e) => {
        |  const spec = JSON.parse(e.data);
        |  const s = spec.spring;              // { stiffness, damping, mass, dampingRatio, settleMs }
        |  applySpring({ stiffness: s.stiffness, damping: s.damping, mass: s.mass });
        |};
        |
        |// --- Kotlin ---
        |// POST the code to http://$host:$port/pair, then use ?t=<token> on /events.
        |// thread { URL("http://$host:$port/events?t=<token>").openStream().bufferedReader().forEachLine { line ->
        |//     if (line.startsWith("data: ")) apply(JSONObject(line.removePrefix("data: "))) } }
        |
        |// --- Swift ---
        |// POST the code to http://$host:$port/pair, then use ?t=<token> on /events.
        |// let (bytes, _) = try await URLSession.shared.bytes(from: URL(string: "http://$host:$port/events?t=\(token)")!)
        |// for try await line in bytes.lines where line.hasPrefix("data: ") { apply(String(line.dropFirst(6))) }
    """.trimMargin()
}
