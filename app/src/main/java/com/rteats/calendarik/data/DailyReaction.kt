package com.rteats.calendarik.data

import androidx.room.Entity
import java.time.LocalDate

/**
 * One record per person and calendar day. Counts are 64-bit and incremented
 * atomically in SQLite. Mood is replaced, never appended.
 *
 * dateEpochDay is an absolute calendar day independent of timezone or DST.
 */
@Entity(tableName = "daily_reactions", primaryKeys = ["dateEpochDay", "authorId"])
data class DailyReaction(
    val dateEpochDay: Long,
    val authorId: Int,
    val redHearts: Long = 0,
    val pinkHearts: Long = 0,
    val dayEmoji: String? = null,
    val revision: Long = 0,
    val updatedAtMillis: Long = 0,
) {
    val day: LocalDate get() = LocalDate.ofEpochDay(dateEpochDay)
}

enum class HeartColor { RED, PINK }

object DailyReactionRules {
    /** The Android UI can show only one nonblank emoji per owner/day. */
    fun sanitizeMood(emoji: String?): String? {
        val trimmed = emoji?.trim().orEmpty()
        if (trimmed.isBlank()) return null
        require(trimmed.length <= 32) { "Эмоция слишком длинная" }
        require(trimmed.codePointCount(0, trimmed.length) <= 8) { "Выберите один смайлик" }
        return trimmed
    }

    fun validateOwner(authorId: Int) {
        require(authorId == Authors.SASHA || authorId == Authors.NASTYA) { "Недопустимый автор" }
    }
}

