package com.klynstudios.hapture

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.klynstudios.hapture.export.ExportStamp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Feel sets and shipped-code checking, on the real Home screen and database. */
@RunWith(AndroidJUnit4::class)
class FeelSetsFlowTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private fun waitForText(text: String) =
        composeTestRule.waitUntil(5_000) { composeTestRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun applyingASetAddsItsTokensAndShippedCodeIsCheckedAgainstThem() {
        composeTestRule.onNodeWithText("Fintech: restrained").performScrollTo().performClick()
        waitForText("Add to my tokens")
        composeTestRule.onNodeWithText("Add to my tokens").performClick()
        waitForText("Added")
        composeTestRule.onNodeWithText("Close").performClick()
        composeTestRule.waitForIdle()
        // The set's springs are now ordinary tokens on Home.
        composeTestRule.onNodeWithText("dismiss").performScrollTo().assertExistsSafe()

        val fresh = ExportStamp.wrap("val x = 1", ExportStamp.Style.SLASH, listOf(ExportStamp.Spring("t", "tap", 700f, 1f)))
        composeTestRule.onNodeWithTag("checkShipped").performScrollTo().performClick()
        waitForText("Check shipped code")
        composeTestRule.onNodeWithTag("checkInput").performTextInput(fresh)
        composeTestRule.onNodeWithText("Check").performClick()
        waitForText("Matches")

        // Same block with one line changed by hand.
        composeTestRule.onNodeWithTag("checkInput").performTextReplacement2(fresh.replace("val x = 1", "val x = 2"))
        composeTestRule.onNodeWithText("Check").performClick()
        waitForText("Edited since export")

        // Untouched, but the token was tuned since.
        val old = ExportStamp.wrap("val x = 1", ExportStamp.Style.SLASH, listOf(ExportStamp.Spring("t", "tap", 400f, 1f)))
        composeTestRule.onNodeWithTag("checkInput").performTextReplacement2(old)
        composeTestRule.onNodeWithText("Check").performClick()
        waitForText("out of date")
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertExistsSafe() = assertExists()
    private fun androidx.compose.ui.test.SemanticsNodeInteraction.performTextReplacement2(text: String) =
        performTextReplacement(text)
}
