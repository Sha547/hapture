package com.klynstudios.hapture

import android.content.Context
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.klynstudios.hapture.data.IntroStore
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The first-launch intro: shown once, can be paged through or skipped, and reopens from Home. */
@RunWith(AndroidJUnit4::class)
class IntroFlowTest {

    @get:Rule
    val rule = createEmptyComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun setSeen(seen: Boolean) =
        context.getSharedPreferences("hapture-prefs", Context.MODE_PRIVATE).edit().putBoolean(IntroStore.KEY, seen).commit()

    @After
    fun leaveItSeen() {
        setSeen(true)
    }

    private fun waitForText(text: String) =
        rule.waitUntil(5_000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun aFirstLaunchShowsTheIntroAndNextWalksToHome() {
        setSeen(false)
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForText("Feel motion before you ship it")
            repeat(4) {
                rule.onNodeWithTag("introNext").performClick()
                rule.waitForIdle()
            }
            waitForText("Then take it anywhere")
            rule.onNodeWithText("Start tuning").performClick()
            waitForText("New experiment")
            assertTrue(IntroStore(context).seen())
        }
    }

    @Test
    fun skipGoesHomeAndTheIntroDoesNotComeBack() {
        setSeen(false)
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForText("Feel motion before you ship it")
            rule.onNodeWithTag("introSkip").performClick()
            waitForText("New experiment")
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForText("New experiment")
            assertTrue(rule.onAllNodesWithText("Feel motion before you ship it").fetchSemanticsNodes().isEmpty())
        }
    }

    @Test
    fun homeReopensTheIntro() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForText("New experiment")
            rule.onNodeWithTag("howItWorks").performScrollTo().performClick()
            waitForText("Feel motion before you ship it")
            rule.onNodeWithTag("introDemo").assertExists()
        }
    }
}
