package com.motionlab.app.data

import android.content.Context
import com.motionlab.app.ui.design.ThemeId

/** The chosen theme is a tiny preference, so SharedPreferences rather than a table. */
class ThemeStore(context: Context) {
    private val prefs = context.getSharedPreferences("motion-lab-prefs", Context.MODE_PRIVATE)

    /** null until the user has picked one, so the system light/dark default can apply. */
    fun load(): ThemeId? = prefs.getString(KEY, null)?.let { name ->
        ThemeId.entries.firstOrNull { it.name == name }
    }

    fun save(id: ThemeId) {
        prefs.edit().putString(KEY, id.name).apply()
    }

    private companion object {
        const val KEY = "theme"
    }
}
