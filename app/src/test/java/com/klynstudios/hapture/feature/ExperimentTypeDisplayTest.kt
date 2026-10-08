package com.klynstudios.hapture.feature

import com.klynstudios.hapture.core.spec.TriggerKind
import com.klynstudios.hapture.data.ExperimentType
import com.klynstudios.hapture.feature.common.label
import org.junit.Assert.assertEquals
import org.junit.Test

class ExperimentTypeDisplayTest {
    // TriggerScreen titles itself with ExperimentType.valueOf(kind.name).label.
    @Test fun `every trigger kind has an experiment type of the same name`() {
        for (k in TriggerKind.entries) assertEquals(k.name, ExperimentType.valueOf(k.name).name)
    }

    @Test fun `labels are sentence case`() {
        for (t in ExperimentType.entries) {
            val words = t.label.split(" ")
            assert(words.drop(1).none { it.first().isUpperCase() }) { "${t.name}: ${t.label}" }
        }
    }
}
