package com.rteats.calendarik.ui

import android.content.Context
import com.rteats.calendarik.data.DailyReactionRules
import org.json.JSONArray

/** Per-person local picker history; not a public calendar event or cloud payload. */
class RecentEmojiStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("recent_moods_v1", Context.MODE_PRIVATE)

    fun read(author: Int): List<String> {
        val raw = prefs.getString("author_$author", null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
                .distinct().take(RecentEmojiHistory.MAX_RECENT)
        }.getOrDefault(emptyList())
    }

    fun record(author: Int, emoji: String): List<String> {
        val safe = DailyReactionRules.sanitizeMood(emoji) ?: return read(author)
        val next = RecentEmojiHistory.withSelected(read(author), safe)
        val array = JSONArray().apply { next.forEach { put(it) } }
        prefs.edit().putString("author_$author", array.toString()).apply()
        return next
    }
}

object RecentEmojiHistory {
    const val MAX_RECENT = 12
    fun withSelected(previous: List<String>, selected: String): List<String> =
        (listOf(selected) + previous.filter { it != selected }).take(MAX_RECENT)
}

