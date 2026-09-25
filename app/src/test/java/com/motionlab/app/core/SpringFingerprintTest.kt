package com.motionlab.app.core

import com.motionlab.app.core.spec.SpringFingerprint
import com.motionlab.app.core.spec.SpringSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpringFingerprintTest {
    @Test fun `shape starts at rest and ends at target`() {
        val f = SpringFingerprint.of(SpringSpec(400f, 0.4f))
        assertEquals(24, f.shape.size)
        assertEquals(0f, f.shape.first(), 1e-4f)
        assertEquals(1f, f.shape.last(), 1e-4f)
    }

    @Test fun `bouncy springs show overshoot and damped ones do not`() {
        assertTrue(SpringFingerprint.of(SpringSpec(400f, 0.2f)).shape.max() > 1.2f)
        assertTrue(SpringFingerprint.of(SpringSpec(400f, 1.0f)).shape.max() <= 1.0f + 1e-3f)
    }

    @Test fun `slower springs get a longer speed bar`() {
        val quick = SpringFingerprint.of(SpringSpec(3000f, 0.9f)).speed
        val slow = SpringFingerprint.of(SpringSpec(60f, 0.9f)).speed
        assertTrue("$quick < $slow", quick < slow)
        assertTrue(quick in 0f..1f && slow in 0f..1f)
    }

    @Test fun `different springs get different fingerprints`() {
        assertTrue(SpringFingerprint.of(SpringSpec(400f, 0.3f)) != SpringFingerprint.of(SpringSpec(400f, 0.9f)))
    }
}
