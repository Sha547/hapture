package com.motionlab.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.motionlab.app.ui.design.Backdrop
import com.motionlab.app.ui.design.LocalBackdrop
import com.motionlab.app.ui.design.Stage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Pixels, not assumptions: the stage really paints the screenshot's part that the focus asks for. */
@RunWith(AndroidJUnit4::class)
class StageBackdropRenderTest {
    @get:Rule
    val rule = createComposeRule()

    /** A tall image: red on top, blue at the bottom. */
    private fun tall(): ImageBitmap = android.graphics.Bitmap.createBitmap(200, 800, android.graphics.Bitmap.Config.ARGB_8888).also { b ->
        val c = android.graphics.Canvas(b)
        c.drawColor(android.graphics.Color.RED)
        c.drawRect(0f, 400f, 200f, 800f, android.graphics.Paint().apply { color = android.graphics.Color.BLUE })
    }.asImageBitmap()

    private fun centerPixel(backdrop: Backdrop?): Int {
        rule.setContent {
            CompositionLocalProvider(LocalBackdrop provides backdrop) {
                Box(Modifier.testTag("stage").size(240.dp, 120.dp)) { Stage(height = 120.dp) {} }
            }
        }
        val px = rule.onNodeWithTag("stage").captureToImage().toPixelMap()
        return px[px.width / 2, px.height / 2].let { android.graphics.Color.argb((it.alpha * 255).toInt(), (it.red * 255).toInt(), (it.green * 255).toInt(), (it.blue * 255).toInt()) }
    }

    @Test fun focusTopShowsTheTopOfTheScreenshot() {
        assertEquals(android.graphics.Color.RED, centerPixel(Backdrop(tall(), 0f)))
    }

    @Test fun focusBottomShowsTheBottomOfTheScreenshot() {
        assertEquals(android.graphics.Color.BLUE, centerPixel(Backdrop(tall(), 1f)))
    }

    @Test fun withoutABackdropTheStageIsTheUsualPad() {
        val p = centerPixel(null)
        assertNotEquals(android.graphics.Color.RED, p)
        assertNotEquals(android.graphics.Color.BLUE, p)
    }
}
