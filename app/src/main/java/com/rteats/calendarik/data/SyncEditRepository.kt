package com.rteats.calendarik.data

import androidx.room.withTransaction

/** Local changes and dirty flags are saved atomically; network is optional. */
class SyncEditRepository(private val db: CalendarDatabase) {
    suspend fun save(event: CalendarEvent) {
        db.withTransaction {
            db.events().save(event)
            if (event.authorMask == Authors.BOTH) {
                db.sync().markDirty(DirtySharedEvent(event.id))
            }
        }
    }

    suspend fun heart(day: Long, author: Int, color: HeartColor?) {
        db.sharedHearts().setHeart(day, author, color)
    }

    suspend fun mood(day: Long, author: Int, emoji: String?) {
        db.reactions().setMood(day, author, emoji)
    }
}
