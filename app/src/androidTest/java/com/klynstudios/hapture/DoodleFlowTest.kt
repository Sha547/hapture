package com.klynstudios.hapture

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.geometry.Offset
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A doodle end to end on the real navigation and database: open a canvas, draw, use every control, leave and come back. */
@RunWith(AndroidJUnit4::class)
class DoodleFlowTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun drawingUndoingAndExportRowsWorkWithoutCrashing() {
        composeTestRule.onNodeWithText("New doodle").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("doodleCanvas").assertIsDisplayed()

        composeTestRule.onNodeWithTag("doodleCanvas").performTouchInput {
            swipe(Offset(width * 0.2f, height * 0.3f), Offset(width * 0.8f, height * 0.7f), 300)
        }
        composeTestRule.waitForIdle()

        for (brush in listOf("Marker", "Paint", "Spray", "Eraser", "Pen")) {
            composeTestRule.onNodeWithText(brush).performScrollTo().performClick()
            composeTestRule.onNodeWithTag("doodleCanvas").performScrollTo().performTouchInput {
                swipe(Offset(width * 0.2f, height * 0.7f), Offset(width * 0.8f, height * 0.3f), 200)
            }
            composeTestRule.waitForIdle()
        }

        composeTestRule.onNodeWithTag("doodleUndo").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Ink").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Copy SVG").performScrollTo().performClick()
        composeTestRule.onNodeWithText("SVG copied").assertExists()
        composeTestRule.onNodeWithText("Save PNG").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("backButton").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("New doodle").assertExists()
    }
}
