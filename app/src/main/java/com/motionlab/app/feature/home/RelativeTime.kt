package com.motionlab.app.feature.home

import java.text.DateFormat
import java.util.Date

/** "Just now", "5 min ago", "3 h ago", "Yesterday", "4 d ago", then a plain date. */
fun relativeTime(nowMs: Long, thenMs: Long): String {
    val minutes = (nowMs - thenMs).coerceAtLeast(0L) / 60_000L
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 60 * 24 -> "${minutes / 60} h ago"
        minutes < 60 * 24 * 2 -> "Yesterday"
        minutes < 60 * 24 * 7 -> "${minutes / (60 * 24)} d ago"
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(thenMs))
    }
}
