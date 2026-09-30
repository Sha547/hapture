package com.klynstudios.hapture

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.klynstudios.hapture.core.sync.LiveSync
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Turns live sync on from the real Export panel, then talks to it over real HTTP like a laptop would. */
@RunWith(AndroidJUnit4::class)
class LiveSyncBridgeFlowTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    /** A raw socket: the app's own HttpURLConnection refuses cleartext, but a laptop's browser does not. */
    private fun call(path: String, method: String = "GET", body: String? = null): Pair<Int, String> =
        java.net.Socket("127.0.0.1", 8787).use { sock ->
            val payload = body ?: ""
            sock.getOutputStream().write(
                ("$method $path HTTP/1.1\r\nHost: x\r\nContent-Length: ${payload.length}\r\nConnection: close\r\n\r\n$payload").toByteArray()
            )
            val text = sock.getInputStream().bufferedReader().readText()
            text.lineSequence().first().split(" ")[1].toInt() to text.substringAfter("\r\n\r\n")
        }

    @Test
    fun pairingGatesTheSpecAndTheBridgeServesEveryExport() {
        composeTestRule.onNodeWithText("New experiment").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Toggle").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Start live sync").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        try {
            assertTrue(LiveSync.running)
            assertTrue(Regex("\\d{4}").matches(LiveSync.code))

            assertEquals(200, call("/").first)
            assertEquals(401, call("/spec").first)
            assertEquals(403, call("/pair", "POST", if (LiveSync.code == "0000") "0001" else "0000").first)

            val token = JSONObject(call("/pair", "POST", LiveSync.code).second).getString("token")
            // The panel publishes on its first snapshot; give it a moment.
            composeTestRule.waitUntil(5_000) { call("/exports?t=$token").second.contains("swift") }
            val exports = JSONObject(call("/exports?t=$token").second)
            assertTrue(exports.getJSONObject("swift").getString("code").isNotBlank())
            assertTrue(JSONObject(call("/spec?t=$token").second).has("spring"))

            // The QR sheet shows a scannable code for a fresh pre-paired link.
            composeTestRule.onNodeWithTag("showQr").performScrollTo().performClick()
            composeTestRule.waitUntil(5_000) { composeTestRule.onAllNodesWithTag("qrImage").fetchSemanticsNodes().isNotEmpty() }
            composeTestRule.onNodeWithTag("qrImage").assertIsDisplayed()
            // Keep the rendered pixels so an outside decoder can confirm it really scans.
            val shot = composeTestRule.onNodeWithTag("qrImage").captureToImage().asAndroidBitmap()
            val ctx = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
            java.io.File(ctx.getExternalFilesDir(null), "qr-on-screen.png").outputStream().use { shot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            LiveSync.stop()
        }
    }
}
