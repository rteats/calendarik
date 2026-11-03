package com.rteats.calendarik.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Purely additive: events, reactions and tombstones are preserved. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS dirty_shared_events (eventId TEXT NOT NULL PRIMARY KEY)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS sync_conflicts (" +
                "eventId TEXT NOT NULL PRIMARY KEY, remoteJson TEXT NOT NULL, " +
                "detectedAtMillis INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_conflicts_eventId ON sync_conflicts (eventId)")
    }
}
