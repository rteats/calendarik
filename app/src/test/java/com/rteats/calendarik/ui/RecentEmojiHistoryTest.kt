package com.rteats.calendarik.ui

import org.junit.Assert.*
import org.junit.Test

class RecentEmojiHistoryTest {
    @Test fun recentFirstDeduplicatedAndCappedAt12() {
        val prior = (1..12).map { "emoji-$it" }
        val next = RecentEmojiHistory.withSelected(prior, "💩")
        assertEquals("💩", next.first())
        assertEquals(12, next.size)
        assertFalse(next.contains("emoji-12"))
        val again = RecentEmojiHistory.withSelected(next, "emoji-3")
        assertEquals("emoji-3", again.first())
        assertEquals(1, again.count { it == "emoji-3" })
    }
}
