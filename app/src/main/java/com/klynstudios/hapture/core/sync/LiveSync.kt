package com.klynstudios.hapture.core.sync

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.net.Inet4Address
import java.net.NetworkInterface

/** Interface name prefixes of mobile data links; nothing on the local Wi-Fi can reach an address on them. */
private val CELLULAR = listOf("rmnet", "ccmni", "pdp", "v4-rmnet", "v4-ccmni", "dummy")

/** Interface name prefixes for Wi-Fi, the phone's own hotspot and wired Ethernet. */
private val LOCAL = listOf("wlan", "swlan", "ap", "eth")

/** 10/8, 172.16/12 and 192.168/16: where a home or office network puts the phone. */
internal fun isPrivateIpv4(ip: String): Boolean {
    val p = ip.split('.').mapNotNull { it.toIntOrNull() }
    if (p.size != 4) return false
    return p[0] == 10 || (p[0] == 172 && p[1] in 16..31) || (p[0] == 192 && p[1] == 168)
}

/**
 * Orders (interface name, IPv4) pairs for showing to a person: cellular links are dropped, then
 * private-range addresses on a Wi-Fi interface come first, then any other private address, then the rest.
 */
internal fun rankAddresses(candidates: List<Pair<String, String>>): List<String> =
    candidates.filter { (name, ip) -> ip.isNotEmpty() && CELLULAR.none { name.startsWith(it) } }
        .sortedWith(compareBy({ !isPrivateIpv4(it.second) }, { (name, _) -> LOCAL.none { name.startsWith(it) } }))
        .map { it.second }
        .distinct()

/** App-wide live-sync switch: one server, off until someone turns it on. */
object LiveSync {
    @Volatile private var bridge = "<!doctype html><title>Hapture</title><p>Bridge page missing from this build."
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

    /** This device's IPv4 addresses a computer on the same network could reach, best first (see [rankAddresses]). */
    fun addresses(): List<String> = try {
        rankAddresses(
            NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }.flatMap { nic ->
                nic.inetAddresses.toList().filterIsInstance<Inet4Address>().mapNotNull { a -> a.hostAddress?.let { nic.name to it } }
            }
        )
    } catch (_: Exception) { emptyList() }

    fun clientSnippet(host: String): String = """
        |// Hapture live sync: apply the spring the moment it changes in the app.
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
