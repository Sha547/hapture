package com.motionlab.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackdropCropTest {
    // A 1080x2340 phone screenshot on a 360x200 stage: full width, a 1080x600 slice.
    @Test fun `a tall screenshot fills the stage width and slides vertically`() {
        val top = BackdropCrop.window(1080, 2340, 360, 200, 0f)
        val bottom = BackdropCrop.window(1080, 2340, 360, 200, 1f)
        assertEquals(1080, top.width)
        assertEquals(600, top.height)
        assertEquals(0, top.x)
        assertEquals(0, top.y)
        assertEquals(2340 - 600, bottom.y)
    }

    @Test fun `the slice keeps the stage's proportions so nothing is stretched`() {
        val w = BackdropCrop.window(1080, 2340, 360, 200, 0.4f)
        assertEquals(360f / 200f, w.width.toFloat() / w.height, 0.02f)
    }

    @Test fun `a wide screenshot is cropped at the sides instead`() {
        val w = BackdropCrop.window(2000, 400, 360, 360, 0.5f)
        assertEquals(400, w.height)
        assertEquals(400, w.width)
        assertEquals(800, w.x)
    }

    @Test fun `out of range focus and empty sizes never produce a window outside the image`() {
        val w = BackdropCrop.window(1000, 1000, 300, 100, 5f)
        assertTrue(w.y + w.height <= 1000 && w.x >= 0 && w.x + w.width <= 1000)
        val z = BackdropCrop.window(0, 0, 0, 0, 0.5f)
        assertTrue(z.width >= 1 && z.height >= 1)
    }
}
