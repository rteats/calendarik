package com.rteats.calendarik.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import java.time.LocalDate

/**
 * One record per person and calendar day. Each user can select only one heart:
 * none (0), red (1) or pink (2). Historical counters are retained so an
 * update never silently destroys old reaction data.
 *
 * dateEpochDay is an absolute calendar day independent of timezone or DST.
 */
@Entity(tableName = "daily_reactions", primaryKeys = ["dateEpochDay", "authorId"])
data class DailyReaction(
    val dateEpochDay: Long,
    val authorId: Int,
    val redHearts: Long = 0,
    val pinkHearts: Long = 0,
    @ColumnInfo(defaultValue = "0") val heartChoice: Int = 0,
    val dayEmoji: String? = null,
    val revision: Long = 0,
    val updatedAtMillis: Long = 0,
) {
    val day: LocalDate get() = LocalDate.ofEpochDay(dateEpochDay)
}

enum class HeartColor(val persisted: Int, val emoji: String) {
    RED(1, "❤️"),
    PINK(2, "🩷");

    companion object {
        fun fromPersisted(value: Int): HeartColor? = entries.firstOrNull { it.persisted == value }
    }
}

object DailyReactionRules {
    fun isValidHeart(value: Int): Boolean = value in 0..2
    fun legacyHeart(red: Long, pink: Long): Int = when {
        red <= 0 && pink <= 0 -> 0
        pink > red -> 2
        else -> 1 // Red wins historic ties; old counters are retained.
    }

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

