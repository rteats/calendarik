package com.rteats.calendarik.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Additive v3 -> v4 migration: keep every event, historical heart count,
 * emotion, revision, and synchronization conflict. Select one legacy heart.
 * If both colors existed, choose the more numerous color (red on ties).
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE daily_reactions ADD COLUMN heartChoice INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            """UPDATE daily_reactions SET heartChoice =
                CASE
                    WHEN redHearts <= 0 AND pinkHearts <= 0 THEN 0
                    WHEN pinkHearts > redHearts THEN 2
                    ELSE 1
                END""".trimIndent()
        )
    }
}
