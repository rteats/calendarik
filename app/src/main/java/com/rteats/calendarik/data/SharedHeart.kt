package com.rteats.calendarik.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * One shared heart per date, not one per author. Rating 0 is a tombstone for
 * removal; it must remain in snapshots so that clearing a heart syncs.
 */
@Entity(tableName = "shared_hearts")
data class SharedHeart(
    @PrimaryKey val dateEpochDay: Long,
    val heartChoice: Int,
    val revision: Long,
    val lastEditor: Int,
    val updatedAtMillis: Long,
)

object SharedHeartRules {
    fun validRating(rating: Int) = rating in 0..5

    /** Deterministic convergence, including simultaneous offline edits. */
    fun shouldAccept(local: SharedHeart?, remote: SharedHeart): Boolean =
        local == null || compareValuesBy(
            remote, local,
            SharedHeart::revision, SharedHeart::lastEditor, SharedHeart::heartChoice,
        ) > 0
}

@Dao
interface SharedHeartDao {
    @Query("SELECT * FROM shared_hearts WHERE dateEpochDay >= :start AND dateEpochDay < :end ORDER BY dateEpochDay")
    fun observeBetween(start: Long, end: Long): Flow<List<SharedHeart>>

    @Query("SELECT * FROM shared_hearts WHERE dateEpochDay = :day")
    suspend fun get(day: Long): SharedHeart?

    @Query("SELECT * FROM shared_hearts ORDER BY dateEpochDay")
    suspend fun all(): List<SharedHeart>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(heart: SharedHeart)

    @Transaction
    suspend fun setHeart(day: Long, editor: Int, rating: Int?, now: Long = System.currentTimeMillis()) {
        DailyReactionRules.validateOwner(editor)
        val nextChoice = rating ?: 0
        require(SharedHeartRules.validRating(nextChoice)) { "Оценка сердечка должна быть от 1 до 5" }
        val current = get(day)
        if ((current?.heartChoice ?: 0) == nextChoice) return
        upsert(SharedHeart(day, nextChoice, (current?.revision ?: 0) + 1, editor, now))
    }
}
