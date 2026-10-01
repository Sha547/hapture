package com.klynstudios.hapture.data

import android.content.Context

/** Whether the first-launch intro has been seen. A single flag, so SharedPreferences like [ThemeStore]. */
class IntroStore(context: Context) {
    private val prefs = context.getSharedPreferences("hapture-prefs", Context.MODE_PRIVATE)

    fun seen(): Boolean = prefs.getBoolean(KEY, false)

    fun markSeen() {
        prefs.edit().putBoolean(KEY, true).apply()
    }

    companion object {
        const val KEY = "intro_seen"
    }
}
