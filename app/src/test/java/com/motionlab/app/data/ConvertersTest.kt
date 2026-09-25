package com.motionlab.app.data

import com.motionlab.app.core.model.ObjectFill
import com.motionlab.app.core.model.ObjectShape
import org.junit.Assert.assertEquals
import org.junit.Test

class ConvertersTest {
    private val c = Converters()

    @Test fun `known values round trip`() {
        for (s in ObjectShape.entries) assertEquals(s, c.toObjectShape(c.fromObjectShape(s)))
        for (f in ObjectFill.entries) assertEquals(f, c.toObjectFill(c.fromObjectFill(f)))
    }

    @Test fun `shapes retired from the picker open as rounded instead of crashing`() {
        assertEquals(ObjectShape.ROUNDED, c.toObjectShape("BLOB"))
        assertEquals(ObjectShape.ROUNDED, c.toObjectShape("STAR"))
    }

    @Test fun `unknown fill falls back to solid`() {
        assertEquals(ObjectFill.INK, c.toObjectFill("NEON"))
    }
}
