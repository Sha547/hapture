package com.klynstudios.hapture

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import com.klynstudios.hapture.data.IntroStore

/**
 * Most UI tests start from Home, so the first-launch intro is marked as seen before any of them run.
 * [IntroFlowTest] clears the flag itself to see the intro.
 */
class HaptureTestRunner : AndroidJUnitRunner() {
    override fun callApplicationOnCreate(app: Application) {
        app.getSharedPreferences("hapture-prefs", Context.MODE_PRIVATE).edit().putBoolean(IntroStore.KEY, true).commit()
        super.callApplicationOnCreate(app)
    }
}
