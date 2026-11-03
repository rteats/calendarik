package com.rteats.calendarik.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Query("SELECT * FROM calendar_events ORDER BY id")
    suspend fun allIncludingDeleted(): List<CalendarEvent>

    @Query("SELECT * FROM calendar_events WHERE deleted = 0 AND startsAtMillis < :end AND endsAtMillis > :start ORDER BY startsAtMillis, id")
    fun observeRange(start: Long, end: Long): Flow<List<CalendarEvent>>

    @Query("SELECT * FROM calendar_events WHERE deleted = 0 AND startsAtMillis < :end AND endsAtMillis > :start ORDER BY startsAtMillis, id")
    suspend fun listForRange(start: Long, end: Long): List<CalendarEvent>

    @Query("SELECT * FROM calendar_events WHERE deleted = 0 AND (title LIKE '%' || :query || '%' OR notes LIKE '%' || :query || '%') ORDER BY startsAtMillis, id")
    fun observeSearch(query: String): Flow<List<CalendarEvent>>

    @Query("SELECT * FROM calendar_events WHERE id = :id")
    suspend fun byId(id: String): CalendarEvent?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(event: CalendarEvent)
}
