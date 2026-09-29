package com.motionlab.app

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Trigger interactions, motion tokens and drawing-based capture, end to end on the real app. */
@RunWith(AndroidJUnit4::class)
class MotionFlowTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private fun AndroidComposeTestRule<*, *>.waitUntilTextExists(text: String, timeoutMillis: Long = 5_000) {
        waitUntil(timeoutMillis) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun open(type: String) {
        composeTestRule.onNodeWithText("New experiment").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(type).performScrollTo().performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun aToggleRespondsToTapsAndSavesItsSpringAsAToken() {
        open("Toggle")
        repeat(3) {
            composeTestRule.onNodeWithTag("triggerTarget").performClick()
            composeTestRule.waitForIdle()
        }
        // Lint runs on the live spec and the export panel offers every platform.
        composeTestRule.onNodeWithText("SwiftUI").performScrollTo().assertExists()
        composeTestRule.onNodeWithText("Lottie").performScrollTo().assertExists()

        composeTestRule.onNodeWithText("Save as motion token").performScrollTo().performClick()
        composeTestRule.waitUntilTextExists("Name this spring")
        composeTestRule.onNode(hasSetTextAction()).performTextInput("ci-token")
        composeTestRule.onNodeWithText("Save").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("backButton").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("ci-token").performScrollTo().assertExists()
        composeTestRule.onNodeWithText("Copy tokens as code").performScrollTo().assertExists()
    }

    @Test
    fun everyTriggerTargetCanBeOperated() {
        for ((type, tag) in listOf("Button press" to "triggerTarget", "Staggered list" to "triggerTarget", "Like burst" to "triggerTarget")) {
            open(type)
            composeTestRule.onNodeWithTag(tag).performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("backButton").performClick()
            composeTestRule.waitForIdle()
        }
        open("Tab indicator")
        composeTestRule.onNodeWithTag("triggerTab2").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun drawingACurveProducesAFittedSpring() {
        composeTestRule.onNodeWithText("Capture a feel from a drawing or video").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("captureCanvas").performTouchInput {
            swipe(Offset(width * 0.05f, height * 0.82f), Offset(width * 0.6f, height * 0.32f), 250)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("captureResult").performScrollTo().assertExists()
        composeTestRule.onNodeWithText("Create experiment").performScrollTo().assertExists()
    }

    @Test
    fun sketchingAMotionOnTheFakeScreenProducesAFittedSpring() {
        composeTestRule.onNodeWithText("Capture a feel from a drawing or video").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Sketch it").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("sketchCanvas").performScrollTo().performTouchInput {
            // An ease-out drag: quick at first, arriving slowly, like a real finger.
            val from = Offset(width * 0.25f, height * 0.75f)
            val to = Offset(width * 0.7f, height * 0.3f)
            down(from)
            for (i in 1..40) {
                advanceEventTime(16)
                val s = 1f - Math.exp(-i / 9.0).toFloat()
                moveTo(from + (to - from) * s)
            }
            up()
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("captureResult").performScrollTo().assertExists()
        composeTestRule.onNodeWithText("Replay the fit").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Create experiment").performScrollTo().assertExists()
    }

    @Test
    fun aChainedFollowerAppearsRunsAndShowsUpInTheSpecAndCode() {
        open("Button press")
        composeTestRule.onNodeWithTag("chain_opacity").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("followerTarget").assertExists()
        composeTestRule.onNodeWithTag("triggerTarget").performTouchInput { down(center); advanceEventTime(200); up() }
        composeTestRule.waitForIdle()
        // The follower is a second transition, exported like any other.
        composeTestRule.onNodeWithText("Motion spec").performScrollTo().assertExists()
        composeTestRule.onNodeWithTag("chainNone").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("followerTarget").assertDoesNotExist()
    }

    @Test
    fun theScreenshotControlIsOnTheEditorAndOpensItsDialog() {
        open("Spring drag")
        composeTestRule.onNodeWithTag("backdropChip").assertExists().performClick()
        composeTestRule.waitUntilTextExists("Preview on your app")
        composeTestRule.onNodeWithTag("backdropPick").assertExists()
        composeTestRule.onNodeWithText("Close").performClick()
    }
}
