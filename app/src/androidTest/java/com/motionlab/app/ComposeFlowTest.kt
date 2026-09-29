package com.motionlab.app

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.motionlab.app.ui.design.sliderTestTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end, on the real navigation + Room database MainActivity builds --
 * not a fake. These run against whatever the device's motion-lab.db already
 * has (other tests and hand-testing leave rows behind), so assertions target
 * what THIS test just did rather than exact counts: the most-recently-touched
 * row is always first, because [com.motionlab.app.data.ExperimentDao.observeAll]
 * orders by updatedAt.
 */
@RunWith(AndroidJUnit4::class)
class ComposeFlowTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun tuningASliderPersistsAcrossLeavingAndReopeningTheExperiment() {
        composeTestRule.onNodeWithText("New experiment").performClick()
        composeTestRule.onNodeWithText("Spring drag").performClick()
        composeTestRule.waitForIdle()

        val stiffnessTag = sliderTestTag("Stiffness")
        composeTestRule.onNodeWithTag(stiffnessTag)
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0.9f) }
        composeTestRule.waitForIdle()
        assertEquals(0.9f, progressOf(stiffnessTag), 0.02f)

        // Back to Home, the debounced save (onValueChangeFinished) has a moment to land.
        composeTestRule.onNodeWithTag("backButton").performClick()
        composeTestRule.waitForIdle()
        Thread.sleep(200)

        // This experiment is the one we just touched, so it's the first "Spring drag" row.
        composeTestRule.onAllNodesWithText("Spring drag", substring = true)[0].performClick()
        composeTestRule.waitForIdle()

        assertEquals(0.9f, progressOf(stiffnessTag), 0.02f)
    }

    @Test
    fun theOnScreenBackButtonAndTheHardwareBackGestureBothReturnHome() {
        composeTestRule.onNodeWithText("New experiment").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Magnetic snap").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("backButton").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("New experiment").assertExists()

        // The hardware/gesture back path (BackHandler), not the on-screen button this time.
        composeTestRule.onNodeWithText("New experiment").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Swipe and fling").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.runOnUiThread { composeTestRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("New experiment").assertExists()
    }

    @Test
    fun everyInteractionTypeOpensAndShowsItsExportSectionWithoutCrashing() {
        val types = listOf(
            "Spring drag", "Magnetic snap", "Swipe and fling", "Bottom sheet",
            "Pull to refresh", "Pinch to zoom", "Predictive back", "Drag to reorder",
            "Toggle", "Button press", "Tab indicator", "Staggered list", "Like burst",
        )
        for (type in types) {
            composeTestRule.onNodeWithText("New experiment").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText(type).performScrollTo().performClick()
            composeTestRule.waitForIdle()

            // The screen made it all the way to the bottom of its own composable tree:
            // real navigation, real Room load, real haptics engine init, no exception.
            composeTestRule.onNodeWithText("Compose code").assertExists()

            composeTestRule.onNodeWithTag("backButton").performClick()
            composeTestRule.waitForIdle()
        }
    }

    @Test
    fun pinningAnExperimentMovesItAboveAMoreRecentlyTouchedOne() {
        val older = "CF Pin Older"
        val newer = "CF Pin Newer"
        createAndRename("Spring drag", older)
        createAndRename("Magnetic snap", newer)
        // Freshly created, untouched since: newer sorts above older by recency alone.
        assertTrue(rowY(newer) < rowY(older))

        composeTestRule.onAllNodesWithText(older, substring = true)[0].performTouchInput { longClick() }
        composeTestRule.waitUntilTextExists("Pin to top")
        composeTestRule.onNodeWithText("Pin to top").performClick()
        composeTestRule.waitForIdle()

        // Pinned, so it now sorts above the merely-more-recent one.
        assertTrue(rowY(older) < rowY(newer))

        // Unpin: a pinned row sorts above everything indefinitely, which would
        // break other tests' "most recent = topmost" assumptions on this same
        // shared device database.
        composeTestRule.onAllNodesWithText(older, substring = true)[0].performTouchInput { longClick() }
        composeTestRule.waitUntilTextExists("Unpin from top")
        composeTestRule.onNodeWithText("Unpin from top").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun deletingAnExperimentShowsUndoAndUndoRestoresIt() {
        val name = "CF Undo Me"
        createAndRename("Swipe and fling", name)

        composeTestRule.onAllNodesWithText(name, substring = true)[0].performTouchInput { longClick() }
        composeTestRule.waitUntilTextExists("Delete")
        composeTestRule.onNodeWithText("Delete").performClick()
        composeTestRule.waitForIdle()

        // Instant, not a second confirm -- the row is already gone, with an undo notice in its place.
        composeTestRule.onNodeWithText(name, substring = false).assertDoesNotExist()
        composeTestRule.waitUntilTextExists("Undo")
        composeTestRule.onNodeWithText("Undo").performClick()

        composeTestRule.waitUntilTextExists(name)
    }

    private fun createAndRename(typeLabel: String, newName: String) {
        composeTestRule.onNodeWithText("New experiment").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(typeLabel).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("backButton").performClick()
        composeTestRule.waitForIdle()

        // The one just created is the most recently touched -- first under Recent.
        composeTestRule.onAllNodesWithText(typeLabel, substring = true)[0].performTouchInput { longClick() }
        composeTestRule.waitUntilTextExists("Rename experiment")
        composeTestRule.onNode(hasSetTextAction()).performTextClearance()
        composeTestRule.onNode(hasSetTextAction()).performTextInput(newName)
        composeTestRule.onNodeWithText("Save").performClick()
        composeTestRule.waitUntilTextExists(newName)
    }

    private fun rowY(text: String): Float =
        composeTestRule.onNodeWithText(text).fetchSemanticsNode().positionInRoot.y

    private fun progressOf(tag: String): Float {
        val node = composeTestRule.onNodeWithTag(tag).fetchSemanticsNode()
        return node.config[SemanticsProperties.ProgressBarRangeInfo].current
    }

    private fun AndroidComposeTestRule<*, *>.waitUntilTextExists(text: String, timeoutMillis: Long = 5_000) {
        waitUntil(timeoutMillis) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
}
