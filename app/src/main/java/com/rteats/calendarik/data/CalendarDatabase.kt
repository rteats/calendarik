package com.rteats.calendarik.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Persistent offline-first storage.
 * For each version > 1, add an explicit Migration and a migration test before release.
 * NEVER add fallbackToDestructiveMigration: deleting events during updates is forbidden.
 */
@Database(entities = [CalendarEvent::class, DailyReaction::class, SharedHeart::class, DirtySharedEvent::class, SyncConflict::class], version = 5, exportSchema = true)
abstract class CalendarDatabase : RoomDatabase() {
    abstract fun events(): EventDao
    abstract fun reactions(): DailyReactionDao
    abstract fun sharedHearts(): SharedHeartDao
    abstract fun sync(): SyncDao

    companion object {
        @Volatile private var instance: CalendarDatabase? = null

        fun get(context: Context): CalendarDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CalendarDatabase::class.java,
                    "calendarik.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build().also { instance = it }
            }
    }
}

