package com.motionlab.app

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Compare on the real navigation: race two springs, move the blend, then play a blind round. */
@RunWith(AndroidJUnit4::class)
class CompareFlowTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun raceBlendAndBlindRoundWork() {
        composeTestRule.onNodeWithText("Compare two springs, race or blind").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("raceButton").performScrollTo().performClick()
        composeTestRule.waitForIdle() // the race ends by itself, so the screen goes idle
        composeTestRule.onNodeWithTag("slider_mix").performScrollTo().performTouchInput {
            swipe(Offset(width * 0.5f, centerY), Offset(width * 0.9f, centerY), 200)
        }
        composeTestRule.onNodeWithText("Save the mix as a token").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag("modeBlind").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("B").performClick()
        composeTestRule.onAllNodesWithTag("guessChip")[0].performClick()
        composeTestRule.onNodeWithText("Next round").assertIsDisplayed().performClick()
        composeTestRule.onAllNodesWithTag("guessChip")[0].assertIsDisplayed()
    }
}
