package com.rteats.calendarik.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Dedicated shared-heart records. Old per-author heartChoice columns are
 * retained for compatibility with earlier encrypted snapshots but ignored
 * by the new interface; per-author moods remain untouched.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS shared_hearts (
                dateEpochDay INTEGER NOT NULL PRIMARY KEY,
                heartChoice INTEGER NOT NULL,
                revision INTEGER NOT NULL,
                lastEditor INTEGER NOT NULL,
                updatedAtMillis INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}
