package com.rteats.calendarik.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import java.time.LocalDate

/**
 * One mood per person and calendar day. Old per-author heart counters and
 * choices remain in this schema for compatibility, but the active heart
 * is a separate SharedHeart record that both people edit.
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

    /** Either a compact Font Awesome icon ID or one legacy Unicode emoji. */
    fun sanitizeMood(emoji: String?): String? {
        val trimmed = emoji?.trim().orEmpty()
        if (trimmed.isBlank()) return null
        require(trimmed.length <= 32) { "Эмоция слишком длинная" }
        if (trimmed.startsWith("fa:")) {
            require(Regex("^fa:[a-z0-9-]+$").matches(trimmed)) { "Некорректный значок" }
        } else {
            require(trimmed.codePointCount(0, trimmed.length) <= 8) { "Выберите один смайлик" }
        }
        return trimmed
    }

    fun validateOwner(authorId: Int) {
        require(authorId == Authors.SASHA || authorId == Authors.NASTYA) { "Недопустимый автор" }
    }
}

