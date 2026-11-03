package com.rteats.calendarik.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "dirty_shared_events")
data class DirtySharedEvent(@PrimaryKey val eventId: String)

@Entity(tableName = "sync_conflicts", indices = [Index("eventId")])
data class SyncConflict(
    @PrimaryKey val eventId: String,
    val remoteJson: String,
    val detectedAtMillis: Long,
)

@Dao
interface SyncDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markDirty(value: DirtySharedEvent)

    @Query("SELECT EXISTS(SELECT 1 FROM dirty_shared_events WHERE eventId = :id)")
    suspend fun isDirty(id: String): Boolean

    @Query("DELETE FROM dirty_shared_events WHERE eventId = :id")
    suspend fun clearDirty(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveConflict(value: SyncConflict)

    @Query("SELECT * FROM sync_conflicts ORDER BY detectedAtMillis DESC")
    suspend fun conflicts(): List<SyncConflict>

    @Query("SELECT COUNT(*) FROM sync_conflicts")
    suspend fun conflictCount(): Int

    @Query("DELETE FROM sync_conflicts WHERE eventId = :id")
    suspend fun deleteConflict(id: String)
}
