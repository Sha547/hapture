package com.motionlab.app.data

import android.content.Context

/** Best streak of the blind-comparison game: a tiny preference, so SharedPreferences like [ThemeStore]. */
class CompareStore(context: Context) {
    private val prefs = context.getSharedPreferences("motion-lab-prefs", Context.MODE_PRIVATE)

    fun bestStreak(): Int = prefs.getInt(KEY, 0)

    /** Records [streak] if it beats the best; returns the best now. */
    fun record(streak: Int): Int {
        val best = maxOf(bestStreak(), streak)
        if (best != bestStreak()) prefs.edit().putInt(KEY, best).apply()
        return best
    }

    private companion object {
        const val KEY = "compare_best_streak"
    }
}
