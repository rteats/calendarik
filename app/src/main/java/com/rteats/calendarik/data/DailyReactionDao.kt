package com.rteats.calendarik.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyReactionDao {
    @Query("SELECT * FROM daily_reactions WHERE authorId = :authorId ORDER BY dateEpochDay")
    suspend fun allByAuthor(authorId: Int): List<DailyReaction>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(reaction: DailyReaction)

    @Query("SELECT * FROM daily_reactions WHERE dateEpochDay = :dateEpochDay ORDER BY authorId")
    fun observeDay(dateEpochDay: Long): Flow<List<DailyReaction>>

    @Query("SELECT * FROM daily_reactions WHERE dateEpochDay >= :start AND dateEpochDay < :end ORDER BY dateEpochDay, authorId")
    fun observeBetween(start: Long, end: Long): Flow<List<DailyReaction>>

    @Query("SELECT * FROM daily_reactions WHERE dateEpochDay = :dateEpochDay ORDER BY authorId")
    suspend fun forDay(dateEpochDay: Long): List<DailyReaction>

    @Query("SELECT * FROM daily_reactions WHERE dateEpochDay = :dateEpochDay AND authorId = :authorId")
    suspend fun get(dateEpochDay: Long, authorId: Int): DailyReaction?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfMissing(reaction: DailyReaction): Long

    @Query("UPDATE daily_reactions SET heartChoice = :choice, revision = revision + 1, updatedAtMillis = :now WHERE dateEpochDay = :dateEpochDay AND authorId = :authorId AND heartChoice != :choice")
    suspend fun updateHeartChoice(dateEpochDay: Long, authorId: Int, choice: Int, now: Long): Int

    @Query("UPDATE daily_reactions SET dayEmoji = :emoji, revision = revision + 1, updatedAtMillis = :now WHERE dateEpochDay = :dateEpochDay AND authorId = :authorId")
    suspend fun replaceMood(dateEpochDay: Long, authorId: Int, emoji: String?, now: Long): Int

    @Transaction
    suspend fun setHeart(
        dateEpochDay: Long,
        authorId: Int,
        color: HeartColor?,
        now: Long = System.currentTimeMillis(),
    ) {
        DailyReactionRules.validateOwner(authorId)
        insertIfMissing(DailyReaction(dateEpochDay, authorId))
        updateHeartChoice(dateEpochDay, authorId, color?.persisted ?: 0, now)
    }

    @Transaction
    suspend fun setMood(dateEpochDay: Long, authorId: Int, emoji: String?, now: Long = System.currentTimeMillis()) {
        DailyReactionRules.validateOwner(authorId)
        val mood = DailyReactionRules.sanitizeMood(emoji)
        insertIfMissing(DailyReaction(dateEpochDay, authorId))
        check(replaceMood(dateEpochDay, authorId, mood, now) == 1)
    }
}
