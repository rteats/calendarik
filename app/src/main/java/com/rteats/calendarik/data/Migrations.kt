package com.rteats.calendarik.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Additive migration: v1 calendar_events and deletion tombstones stay untouched. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS daily_reactions (
                dateEpochDay INTEGER NOT NULL,
                authorId INTEGER NOT NULL,
                redHearts INTEGER NOT NULL,
                pinkHearts INTEGER NOT NULL,
                dayEmoji TEXT,
                revision INTEGER NOT NULL,
                updatedAtMillis INTEGER NOT NULL,
                PRIMARY KEY(dateEpochDay, authorId)
            )
            """.trimIndent()
        )
    }
}

