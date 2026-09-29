package com.motionlab.app

import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The editor's one-tap tools, on the real app: Material 3 springs, fixing a
 * check (and undoing the fix), comparing from inside an editor, the haptic
 * support note, and the predictive back editor.
 */
@RunWith(AndroidJUnit4::class)
class EditorToolsFlowTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun open(type: String) {
        rule.onNodeWithText("New experiment").performClick()
        rule.waitForIdle()
        rule.onNodeWithText(type).performScrollTo().performClick()
        rule.waitForIdle()
    }

    @Test
    fun aMaterialChipSetsItsSpringAndTheReadoutNamesIt() {
        open("Toggle")
        rule.onNodeWithTag("m3_standard_default").performScrollTo().performClick()
        rule.waitForIdle()
        rule.waitUntilTextExists("M3 Standard default")
    }

    @Test
    fun fixingACheckClearsItAndUndoPutsItBack() {
        open("Toggle")
        rule.onNodeWithText("Elastic").performScrollTo().performClick()
        rule.waitForIdle()
        rule.waitUntilTextExists("31% over")

        rule.onNodeWithText("Cut to 15%").performScrollTo().performClick()
        rule.waitForIdle()
        rule.waitUntilTextExists("15% over")
        rule.onNodeWithText("all clear").assertExists()

        rule.onNodeWithTag("undoFix").performScrollTo().performClick()
        rule.waitForIdle()
        rule.waitUntilTextExists("31% over")
        rule.onNodeWithText("Cut to 15%").assertExists()
    }

    @Test
    fun compareOpensWithThisSpringAndBackReturnsToTheEditor() {
        open("Toggle")
        rule.onNodeWithTag("compareWith").performScrollTo().performClick()
        rule.waitForIdle()
        rule.waitUntilTextExists("Race")
        // Lane B starts on the Material spring nearest to this one.
        rule.waitUntil(5_000) { rule.onAllNodesWithText("M3 ", substring = true).fetchSemanticsNodes().isNotEmpty() }

        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        rule.waitUntilTextExists("Tap the switch.")
    }

    @Test
    fun theHapticsSectionSaysWhatThisPhoneCanPlay() {
        open("Spring drag")
        rule.onNodeWithTag("hapticSupport").performScrollTo().assertExists()
    }

    @Test
    fun predictiveBackSwipesAndExportsItsHandler() {
        open("Predictive back")
        rule.waitUntilTextExists("Message")
        rule.onNodeWithTag("backPage").performTouchInput { swipeRight(startX = 10f, endX = centerX, durationMillis = 400) }
        rule.waitForIdle()
        // Survives a swipe and still shows its own export.
        rule.onNodeWithText("Compose code").performScrollTo().assertExists()
    }

    private fun AndroidComposeTestRule<*, *>.waitUntilTextExists(text: String, timeoutMillis: Long = 5_000) {
        waitUntil(timeoutMillis) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
}
