package com.klynstudios.hapture

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A timeline, end to end on the real navigation and Room database: build one
 * from two freshly created experiments, reorder its steps, adjust a gap,
 * remove a step, and confirm what's left survives leaving and reopening.
 *
 * The device's `hapture.db` accumulates rows across every test in a run,
 * so anything created here is renamed to a name unique to this test right
 * away (Home's long-press rename, same as a person would use) rather than
 * relying on auto-generated names like "Spring Drag 2" staying predictable.
 *
 * Updating a timeline must never go through `OnConflictStrategy.REPLACE`: SQLite
 * implements it as delete-then-insert, which cascade-deletes the steps. The
 * reorder and rename checks here would catch that.
 */
@RunWith(AndroidJUnit4::class)
class TimelineFlowTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private val springName = "TL Flow Spring"
    private val magnetName = "TL Flow Magnet"
    private val timelineName = "TL Flow Sequence"

    @Test
    fun buildingReorderingAndTrimmingASequencePersists() {
        createAndRename("Spring drag", springName)
        createAndRename("Magnetic snap", magnetName)
        createAndRenameTimeline(timelineName)

        openTimeline(timelineName)
        composeTestRule.onNodeWithText("Nothing here yet -- add a step below.").assertExists()

        addStep(springName)
        addStep(magnetName)
        composeTestRule.onNodeWithText(springName).assertExists()
        composeTestRule.onNodeWithText(magnetName).assertExists()
        composeTestRule.onNodeWithText("2").assertExists() // the Steps section's own count

        // Reorder: magnet starts second (added after spring); move it up and
        // confirm it now sits above spring on screen, not just "didn't crash".
        val yBefore = rowY(magnetName) - rowY(springName)
        assertTrue("magnet should start below spring", yBefore > 0)
        composeTestRule.onNodeWithTag("moveUp_$magnetName").performScrollTo().performClick()
        // The move is a database write on another thread, which waitForIdle() doesn't wait for.
        composeTestRule.waitUntil(5_000) { rowY(magnetName) < rowY(springName) }
        val yAfter = rowY(magnetName) - rowY(springName)
        assertTrue("magnet should now sit above spring", yAfter < 0)

        // Gap stepper: three taps of +100ms.
        repeat(3) {
            composeTestRule.onNodeWithTag("gapIncrease_$springName").performScrollTo().performClick()
            composeTestRule.waitForIdle()
        }
        composeTestRule.onNodeWithTag("gapValue_$springName").performScrollTo().assertHasText("300 ms")

        // The scrubber responds to a programmatic seek.
        val scrubber = composeTestRule.onNodeWithTag("timelineScrubber")
        scrubber.performScrollTo()
        val range = scrubber.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        val target = (range.range.endInclusive) / 2f
        scrubber.performSemanticsAction(SemanticsActions.SetProgress) { it(target) }
        composeTestRule.waitForIdle()
        val after = scrubber.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current
        assertEquals(target, after, 1f)

        // Play drives the scrubber to the very end. These steps settle in well
        // under a second, so the "Pause" label is often gone again before a
        // slow poll can ever observe it -- a real, correct, fast finish, not a
        // bug -- so the meaningful check is where the scrubber actually ends
        // up, not catching the button mid-toggle.
        val totalMsValue = scrubber.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].range.endInclusive
        composeTestRule.onNodeWithText("Play").performScrollTo().performClick()
        composeTestRule.waitUntil(5_000) {
            val current = composeTestRule.onNodeWithTag("timelineScrubber")
                .fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current
            current >= totalMsValue - 1f
        }
        composeTestRule.onNodeWithText("Play").assertExists() // toggled back once it finished

        // Remove the spring step; the magnet step survives.
        composeTestRule.onNodeWithText(springName).performScrollTo().performTouchInput { longClick() }
        composeTestRule.waitUntilTextExists("Remove step")
        composeTestRule.onNodeWithText("Remove").performClick()
        composeTestRule.waitUntilTextGone("Remove step")
        composeTestRule.onNodeWithText(springName).assertDoesNotExist()
        composeTestRule.onNodeWithText(magnetName).assertExists()

        // Leave, then reopen by name -- only shows the survivor if it actually persisted.
        composeTestRule.onNodeWithTag("backButton").performClick()
        composeTestRule.waitForIdle()
        openTimeline(timelineName)
        composeTestRule.onNodeWithText(magnetName).assertExists()
        composeTestRule.onNodeWithText(springName).assertDoesNotExist()
    }

    /** New Experiment of [typeLabel], then rename the freshly created (topmost, just-touched) row to [newName]. */
    private fun createAndRename(typeLabel: String, newName: String) {
        composeTestRule.onNodeWithText("New experiment").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(typeLabel).performClick()
        composeTestRule.waitUntil(5_000) { composeTestRule.onAllNodesWithTag("backButton").fetchSemanticsNodes().isNotEmpty() && composeTestRule.onAllNodesWithText("Compose code").fetchSemanticsNodes().isNotEmpty() }
        composeTestRule.onNodeWithTag("backButton").performClick()
        composeTestRule.waitForIdle()

        // The one just created is the most recently touched -- first under Recent.
        composeTestRule.onAllNodesWithText(typeLabel, substring = true)[0].performTouchInput { longClick() }
        composeTestRule.waitUntilTextExists("Rename experiment")
        renameInDialog(newName)
    }

    private fun createAndRenameTimeline(newName: String) {
        composeTestRule.onNodeWithText("New timeline").performScrollTo().performClick()
        // Creating the timeline is an async database write; wait for its editor rather than for Compose idle.
        composeTestRule.waitUntil(5_000) { composeTestRule.onAllNodesWithTag("backButton").fetchSemanticsNodes().isNotEmpty() }
        composeTestRule.onNodeWithTag("backButton").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onAllNodesWithText("Timeline", substring = true)[0].performScrollTo().performTouchInput { longClick() }
        composeTestRule.waitUntilTextExists("Rename timeline")
        renameInDialog(newName)
    }

    private fun renameInDialog(newName: String) {
        composeTestRule.onNode(hasSetTextAction()).performTextClearance()
        composeTestRule.onNode(hasSetTextAction()).performTextInput(newName)
        composeTestRule.onNodeWithText("Save").performClick()
        composeTestRule.waitUntilTextExists(newName)
    }

    private fun openTimeline(name: String) {
        composeTestRule.onAllNodesWithText(name, substring = true)[0].performScrollTo().performClick()
        composeTestRule.waitForIdle()
    }

    private fun addStep(experimentName: String) {
        composeTestRule.onNodeWithText("Add step").performScrollTo().performClick()
        composeTestRule.waitUntilTextExists("Add a step")
        composeTestRule.onNodeWithText(experimentName).performScrollTo().performClick()
        composeTestRule.waitUntilTextGone("Add a step")
    }

    private fun rowY(text: String): Float =
        composeTestRule.onNodeWithText(text).fetchSemanticsNode().positionInRoot.y

    private fun SemanticsNodeInteraction.assertHasText(expected: String) {
        val texts = fetchSemanticsNode().config[SemanticsProperties.Text].map { it.text }
        assertTrue("expected one of $texts to be \"$expected\"", texts.contains(expected))
    }

    private fun AndroidComposeTestRule<*, *>.waitUntilTextExists(text: String, timeoutMillis: Long = 5_000) {
        waitUntil(timeoutMillis) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun AndroidComposeTestRule<*, *>.waitUntilTextGone(text: String, timeoutMillis: Long = 5_000) {
        waitUntil(timeoutMillis) { onAllNodesWithText(text).fetchSemanticsNodes().isEmpty() }
    }
}
