package com.klynstudios.hapture

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.center
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The freehand-shape flow, end to end on the real navigation and Room
 * database: open an experiment, draw a closed loop, use it, confirm the
 * Object section reflects it (a "Redraw" link only appears once a custom
 * shape actually exists), and that it's still there after leaving and
 * reopening the editor.
 *
 * [ShapeDrawer] shows as an `androidx.compose.ui.window.Dialog`, a separate
 * Android window; its content can compose one frame later than the click
 * that triggers it, so transitions into and out of it are awaited with
 * [waitUntilTextExists] / [waitUntilTextGone] rather than a single
 * `waitForIdle()` (which is enough for same-composition navigation
 * elsewhere, e.g. in [ComposeFlowTest], but proved flaky across a Dialog
 * boundary specifically).
 */
@RunWith(AndroidJUnit4::class)
class ShapeDrawerFlowTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun drawingAClosedShapeAndUsingItShowsTheRedrawLink() {
        composeTestRule.onNodeWithText("New experiment").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Spring drag").performClick()
        composeTestRule.waitForIdle()

        // The Object section is below the fold on a typical test viewport; performClick()
        // does not auto-scroll, so bring the chip into view first.
        composeTestRule.onNodeWithText("Custom").performScrollTo().performClick()
        composeTestRule.waitUntilTextExists("Draw a shape")

        traceRoughCircle(composeTestRule)
        composeTestRule.waitUntilTextExists("Use this shape")

        composeTestRule.onNodeWithText("Use this shape").performClick()
        composeTestRule.waitUntilTextGone("Draw a shape")

        // Back on the editor: drawing something real is the only way this link appears.
        composeTestRule.onNodeWithText("Redraw").assertExists()

        // Leave, then reopen the same experiment -- this only shows "Redraw"
        // again if the drawn shape actually made it through Room and back.
        composeTestRule.onNodeWithTag("backButton").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithText("Spring drag", substring = true)[0].performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Redraw").assertExists()
    }

    @Test
    fun cancellingTheDrawerLeavesTheShapeUnchanged() {
        composeTestRule.onNodeWithText("New experiment").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Magnetic snap").performClick()
        composeTestRule.waitForIdle()

        // The Object section is below the fold on a typical test viewport; performClick()
        // does not auto-scroll, so bring the chip into view first.
        composeTestRule.onNodeWithText("Custom").performScrollTo().performClick()
        composeTestRule.waitUntilTextExists("Draw a shape")

        composeTestRule.onNodeWithText("Cancel").performClick()
        composeTestRule.waitUntilTextGone("Draw a shape")

        // Back on the editor, nothing was drawn, so there's nothing to redraw.
        composeTestRule.onNodeWithText("Redraw").assertDoesNotExist()
        composeTestRule.onNodeWithText("Compose code").assertExists()
    }

    private fun AndroidComposeTestRule<*, *>.waitUntilTextExists(text: String, timeoutMillis: Long = 5_000) {
        waitUntil(timeoutMillis) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun AndroidComposeTestRule<*, *>.waitUntilTextGone(text: String, timeoutMillis: Long = 5_000) {
        waitUntil(timeoutMillis) { onAllNodesWithText(text).fetchSemanticsNodes().isEmpty() }
    }

    /** Drags a 12-point loop around the drawing canvas's own centre -- well past the minimum point and size thresholds. */
    private fun traceRoughCircle(rule: AndroidComposeTestRule<*, *>) {
        rule.onNodeWithTag("shapeDrawCanvas").performTouchInput {
            val c = center
            val r = c.y.coerceAtMost(c.x) * 0.6f
            down(c + Offset(r, 0f))
            for (i in 1..12) {
                val a = 2f * PI.toFloat() * i / 12
                moveTo(c + Offset(r * cos(a), r * sin(a)))
            }
            up()
        }
    }
}
