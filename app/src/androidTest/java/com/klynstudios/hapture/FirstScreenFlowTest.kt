package com.klynstudios.hapture

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** What a newcomer sees first: a demo they can drag and feel, and plain words on the sliders. */
@RunWith(AndroidJUnit4::class)
class FirstScreenFlowTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeShowsALiveDemoThatRespondsToDragsAndToTheFeelButtons() {
        composeTestRule.onNodeWithText("Tune how things move in an app, by feel. Then copy the code into your project.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Drag the square and let go. The buttons change how it moves.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Bouncy").performClick()
        composeTestRule.onNodeWithTag("homeDemo").performTouchInput {
            down(center)
            repeat(6) { moveBy(Offset(30f, 0f)) }
            up()
        }
        composeTestRule.waitForIdle() // the spring settles by itself, so the screen goes idle
        composeTestRule.onNodeWithTag("homeDemo").assertIsDisplayed()
        composeTestRule.onNodeWithText("Gentle").performClick()
        composeTestRule.onNodeWithText("New experiment").assertIsDisplayed()
    }

    @Test
    fun theEditorExplainsStiffnessAndDampingInPlainWords() {
        composeTestRule.onNodeWithText("New experiment").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Spring drag").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("How fast it snaps back to rest.").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("How much it bounces. Low bounces a lot, high doesn't bounce.").performScrollTo().assertIsDisplayed()
        // The screenshot button now lives on the stage, so it scrolls with the page instead of floating over it.
        composeTestRule.onNodeWithTag("backdropChip").assertExists()
    }
}
