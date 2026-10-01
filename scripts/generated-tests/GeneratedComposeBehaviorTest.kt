package com.klynstudios.hapture

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * Runs the code the app GENERATES (compiled in from check.* packages; run it with scripts/check-generated-on-device.sh, not on its own)
 * with real touches on a real device, and measures what it does. The generated code is the product: it has
 * to behave, not just compile.
 */
@RunWith(AndroidJUnit4::class)
class GeneratedComposeBehaviorTest {
    @get:Rule
    val rule = createComposeRule()

    private class Blob(val cx: Float, val cy: Float, val count: Int, val top: Int)

    /** Where the red is inside the host: centre, pixel count and top row. Null if there is none. */
    private fun red(): Blob? {
        val img: ImageBitmap = rule.onNodeWithTag("host").captureToImage()
        val w = img.width
        val px = IntArray(w * img.height)
        img.readPixels(px)
        var n = 0; var sx = 0L; var sy = 0L; var top = Int.MAX_VALUE
        for (i in px.indices) {
            val c = px[i]
            if (((c shr 16) and 0xFF) > 200 && ((c shr 8) and 0xFF) < 60 && (c and 0xFF) < 60) {
                n++; sx += i % w; val y = i / w; sy += y; if (y < top) top = y
            }
        }
        return if (n == 0) null else Blob(sx.toFloat() / n, sy.toFloat() / n, n, top)
    }

    private fun host(content: @Composable () -> Unit) = rule.setContent {
        Box(Modifier.testTag("host").size(360.dp, 560.dp).background(Color.White)) { content() }
    }

    private fun square(): Modifier = Modifier.size(96.dp).background(Color.Red)

    // ---------- Spring drag ----------

    @Test fun springDragFollowsTheFingerThenSpringsHome() {
        host { check.spring_mid.HaptureRubberBandDrag(square()) }
        val start = red()!!
        rule.onNodeWithTag("host").performTouchInput { down(Offset(48.dp.toPx(), 48.dp.toPx())); moveBy(Offset(160.dp.toPx(), 0f)) }
        rule.waitForIdle()
        val held = red()
        assertNotNull("the red square vanished", held)
        assertTrue("the square must move with the drag: x ${start.cx} -> ${held!!.cx}", held.cx - start.cx > 60f)
        rule.onNodeWithTag("host").performTouchInput { up() }
        rule.waitForIdle()
        assertEquals("springs back to where it started", start.cx, red()!!.cx, 3f)

        // And vertically: it's a free drag, not a horizontal slider.
        rule.onNodeWithTag("host").performTouchInput { down(Offset(48.dp.toPx(), 48.dp.toPx())); moveBy(Offset(0f, 160.dp.toPx())) }
        rule.waitForIdle()
        assertTrue("the square must follow a vertical drag", red()!!.cy - start.cy > 60f)
        rule.onNodeWithTag("host").performTouchInput { up() }
        rule.waitForIdle()
        assertEquals("springs back vertically too", start.cy, red()!!.cy, 3f)
    }

    // ---------- Magnetic snap ----------

    @Test fun magneticSnapMovesAndSettlesOnATarget() {
        host { Box(Modifier.fillMaxSize()) { check.magnetic_mid.HaptureMagneticSnap(square()) } }
        val start = red()!!
        rule.onNodeWithTag("host").performTouchInput { down(Offset(48.dp.toPx(), 48.dp.toPx())); moveBy(Offset(110.dp.toPx(), 0f)) }
        rule.waitForIdle()
        assertTrue("moves with the drag", red()!!.cx - start.cx > 40f)
        rule.onNodeWithTag("host").performTouchInput { up() }
        rule.waitForIdle()
        val end = red()!!
        assertTrue("settled somewhere other than the start, on a snap point (${start.cx} -> ${end.cx})", abs(end.cx - start.cx) > 30f)
    }

    // ---------- Swipe to dismiss ----------

    private var dismissed = 0

    private fun swipeHost() = host { Column { check.swipe_mid.HaptureSwipeCard(onDismissed = { dismissed = it }, modifier = Modifier) { Box(square()) } } }

    @Test fun aLongSlowSwipeDismissesTheCard() {
        dismissed = 0; swipeHost()
        rule.onNodeWithTag("host").performTouchInput {
            down(Offset(48.dp.toPx(), 48.dp.toPx()))
            repeat(20) { advanceEventTime(30); moveBy(Offset(20.dp.toPx(), 0f)) } // 400dp over 600 ms: slow but far
            advanceEventTime(400); up()
        }
        rule.waitUntil(5_000) { dismissed != 0 }
        assertEquals(1, dismissed)
    }

    @Test fun aShortSlowDragSpringsBackWithoutDismissing() {
        dismissed = 0; swipeHost()
        val start = red()!!
        rule.onNodeWithTag("host").performTouchInput {
            down(Offset(48.dp.toPx(), 48.dp.toPx()))
            repeat(5) { advanceEventTime(40); moveBy(Offset(6.dp.toPx(), 0f)) } // 30dp, well under the distance
            advanceEventTime(300); up()
        }
        rule.waitForIdle()
        assertEquals(0, dismissed)
        assertEquals(start.cx, red()!!.cx, 3f)
    }

    @Test fun aQuickFlickDismissesEvenThoughItIsShort() {
        dismissed = 0; swipeHost()
        rule.onNodeWithTag("host").performTouchInput {
            down(Offset(48.dp.toPx(), 48.dp.toPx()))
            // A realistic flick: 12 samples over 60 ms, 84dp (7dp each, minus touch slop) = about 1400 dp/s.
            // Shorter than the dismiss distance (94dp), faster than the flick speed (825dp/s).
            // (moveBy adds its own delay per step, 16 ms by default, so set it explicitly.)
            repeat(12) { moveBy(Offset(7.dp.toPx(), 0f), delayMillis = 5) }
            up()
        }
        rule.waitUntil(5_000) { dismissed != 0 }
        assertEquals("a flick past the speed threshold must dismiss", 1, dismissed)
    }

    // ---------- Pull to refresh ----------

    private var refreshed = 0

    @Test fun pullToRefreshFiresAfterAFarPullAndReturns() {
        refreshed = 0
        host { check.pull_mid.HapturePullToRefresh(onRefresh = { refreshed++ }, modifier = Modifier) { Box(square()) } }
        val start = red()!!
        rule.onNodeWithTag("host").performTouchInput { down(Offset(48.dp.toPx(), 48.dp.toPx())); moveBy(Offset(0f, 400.dp.toPx())) }
        rule.waitForIdle()
        assertTrue("content follows the pull", red()!!.cy - start.cy > 30f)
        rule.onNodeWithTag("host").performTouchInput { up() }
        rule.waitUntil(6_000) { refreshed == 1 }
        rule.mainClock.advanceTimeBy(2_500)
        rule.waitForIdle()
        assertEquals("returns to rest", start.cy, red()!!.cy, 3f)
    }


    @Test fun pinchZoomGrowsWhileHeldAndReturnsToOneOnRelease() {
        host { check.zoom_mid.HapturePinchZoom(Modifier.size(200.dp)) { Box(Modifier.size(200.dp).background(Color.Red)) } }
        val before = red()!!.count
        rule.onNodeWithTag("host").performTouchInput {
            val c = Offset(100.dp.toPx(), 100.dp.toPx())
            down(0, c + Offset(-20.dp.toPx(), 0f)); down(1, c + Offset(20.dp.toPx(), 0f))
            repeat(8) { advanceEventTime(16); moveTo(0, c + Offset(-(20 + it * 6).dp.toPx(), 0f)); moveTo(1, c + Offset((20 + it * 6).dp.toPx(), 0f)) }
        }
        rule.waitForIdle()
        assertTrue("grows while pinched out", red()!!.count > before * 1.15f)
        rule.onNodeWithTag("host").performTouchInput { up(0); up(1) }
        rule.waitForIdle()
        assertEquals("releases back to 1x", before.toFloat(), red()!!.count.toFloat(), before * 0.03f)
    }

    private var moved: Pair<Int, Int>? = null

    @Test fun reorderReportsTheMoveWhenDraggedFarEnough() {
        moved = null
        host { check.reorder_mid.HaptureReorderRow(index = 1, itemCount = 3, onReorder = { f, t -> moved = f to t }, modifier = Modifier) { Box(square()) } }
        rule.onNodeWithTag("host").performTouchInput {
            down(Offset(48.dp.toPx(), 48.dp.toPx()))
            repeat(6) { advanceEventTime(20); moveBy(Offset(0f, 14.dp.toPx())) } // 84dp down: one slot (56dp) and past the threshold
            up()
        }
        rule.waitUntil(5_000) { moved != null }
        assertEquals(1 to 2, moved)
    }

    @Test fun reorderIgnoresASmallDrag() {
        moved = null
        host { check.reorder_mid.HaptureReorderRow(index = 1, itemCount = 3, onReorder = { f, t -> moved = f to t }, modifier = Modifier) { Box(square()) } }
        val start = red()!!
        rule.onNodeWithTag("host").performTouchInput {
            down(Offset(48.dp.toPx(), 48.dp.toPx()))
            repeat(2) { advanceEventTime(20); moveBy(Offset(0f, 5.dp.toPx())) }
            up()
        }
        rule.waitForIdle()
        assertEquals(null, moved)
        assertEquals(start.cy, red()!!.cy, 3f)
    }

    private var sheetDismissed = false

    @Test fun bottomSheetStartsAtItsPeekAndExpandsWhenDraggedUp() {
        sheetDismissed = false
        rule.setContent {
            Box(Modifier.testTag("host").size(360.dp, 560.dp).background(Color.White)) {
                check.sheet_mid.HaptureBottomSheet(onDismissed = { sheetDismissed = true }) { Box(Modifier.fillMaxSize().background(Color.Red)) }
            }
        }
        val peek = red()!!.top
        rule.onNodeWithTag("host").performTouchInput {
            down(Offset(180.dp.toPx(), (height - 40.dp.toPx())))
            repeat(10) { advanceEventTime(20); moveBy(Offset(0f, -20.dp.toPx())) }
            advanceEventTime(300); up()
        }
        rule.waitForIdle()
        assertTrue("the sheet expands to a taller detent: top $peek -> ${red()!!.top}", red()!!.top < peek - 40)
        assertFalse(sheetDismissed)
    }

    // ---------- Trigger demos (generic exports) ----------

    private fun changesOnClick(content: @Composable () -> Unit, minChange: Float = 0.004f) {
        rule.setContent { Box(Modifier.testTag("host").size(320.dp, 320.dp).background(Color.White)) { content() } }
        rule.waitForIdle()
        val a = snapshot()
        rule.onNodeWithTag("host").performTouchInput { click(Offset(60.dp.toPx(), 30.dp.toPx())) }
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(1500); rule.waitForIdle()
        val b = snapshot()
        val diff = a.indices.count { a[it] != b[it] } / a.size.toFloat()
        assertTrue("clicking must change what's drawn (changed ${"%.3f".format(diff)})", diff > minChange)
    }

    private fun snapshot(): IntArray {
        val img = rule.onNodeWithTag("host").captureToImage()
        return IntArray(img.width * img.height).also { img.readPixels(it) }
    }

    @Test fun toggleDemoAnimatesOnClick() = changesOnClick({ check.trigger_toggle_mid.TOGGLEDemo() })
    @Test fun buttonPressDemoAnimatesOnClick() = changesOnClick({ check.trigger_button_press_mid.BUTTONPRESSDemo() }, 0.0005f)
    @Test fun tabIndicatorDemoAnimatesOnClick() = changesOnClick({ check.trigger_tab_indicator_mid.TABINDICATORDemo() }, 0.0005f)
    @Test fun staggerListDemoAnimatesOnClick() = changesOnClick({ check.trigger_stagger_list_mid.STAGGERLISTDemo() }, 0.0005f)
    @Test fun likeBurstDemoAnimatesOnClick() = changesOnClick({ check.trigger_like_burst_mid.LIKEBURSTDemo() }, 0.0005f)
    @Test fun chainedDemoAnimatesOnClick() = changesOnClick({ check.chained_mid.ChainedDemo() }, 0.0005f)
}
