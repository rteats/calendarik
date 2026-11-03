package com.rteats.calendarik.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyReactionDao {
    @Query("SELECT * FROM daily_reactions WHERE dateEpochDay = :dateEpochDay ORDER BY authorId")
    fun observeDay(dateEpochDay: Long): Flow<List<DailyReaction>>

    @Query("SELECT * FROM daily_reactions WHERE dateEpochDay = :dateEpochDay ORDER BY authorId")
    suspend fun forDay(dateEpochDay: Long): List<DailyReaction>

    @Query("SELECT * FROM daily_reactions WHERE dateEpochDay = :dateEpochDay AND authorId = :authorId")
    suspend fun get(dateEpochDay: Long, authorId: Int): DailyReaction?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfMissing(reaction: DailyReaction): Long

    @Query("UPDATE daily_reactions SET redHearts = redHearts + 1, revision = revision + 1, updatedAtMillis = :now WHERE dateEpochDay = :dateEpochDay AND authorId = :authorId AND redHearts < 9223372036854775807")
    suspend fun addRed(dateEpochDay: Long, authorId: Int, now: Long): Int

    @Query("UPDATE daily_reactions SET pinkHearts = pinkHearts + 1, revision = revision + 1, updatedAtMillis = :now WHERE dateEpochDay = :dateEpochDay AND authorId = :authorId AND pinkHearts < 9223372036854775807")
    suspend fun addPink(dateEpochDay: Long, authorId: Int, now: Long): Int

    @Query("UPDATE daily_reactions SET dayEmoji = :emoji, revision = revision + 1, updatedAtMillis = :now WHERE dateEpochDay = :dateEpochDay AND authorId = :authorId")
    suspend fun replaceMood(dateEpochDay: Long, authorId: Int, emoji: String?, now: Long): Int

    @Transaction
    suspend fun addHeart(dateEpochDay: Long, authorId: Int, color: HeartColor, now: Long = System.currentTimeMillis()) {
        DailyReactionRules.validateOwner(authorId)
        insertIfMissing(DailyReaction(dateEpochDay, authorId))
        val affected = when (color) {
            HeartColor.RED -> addRed(dateEpochDay, authorId, now)
            HeartColor.PINK -> addPink(dateEpochDay, authorId, now)
        }
        require(affected == 1) { "Максимум числа сердечек достигнут" }
    }

    @Transaction
    suspend fun setMood(dateEpochDay: Long, authorId: Int, emoji: String?, now: Long = System.currentTimeMillis()) {
        DailyReactionRules.validateOwner(authorId)
        val mood = DailyReactionRules.sanitizeMood(emoji)
        insertIfMissing(DailyReaction(dateEpochDay, authorId))
        check(replaceMood(dateEpochDay, authorId, mood, now) == 1)
    }
}

