package com.rteats.calendarik.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real SQLite v2 file, containing events and daily reactions, upgraded to Room v3. */
@RunWith(AndroidJUnit4::class)
class UpgradeV2ToV3Test {
    @Test fun v2DataSurvivesSyncTablesAdded() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "calendarik-v2-upgrade-test.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            old.execSQL(
                """CREATE TABLE calendar_events (
                id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, notes TEXT NOT NULL,
                startsAtMillis INTEGER NOT NULL, endsAtMillis INTEGER NOT NULL,
                authorMask INTEGER NOT NULL, lastEditor INTEGER NOT NULL,
                revision INTEGER NOT NULL, createdAtMillis INTEGER NOT NULL,
                updatedAtMillis INTEGER NOT NULL, deleted INTEGER NOT NULL,
                payloadVersion INTEGER NOT NULL)"""
            )
            old.execSQL("CREATE INDEX index_calendar_events_startsAtMillis ON calendar_events (startsAtMillis)")
            old.execSQL("CREATE INDEX index_calendar_events_updatedAtMillis ON calendar_events (updatedAtMillis)")
            old.execSQL(
                """CREATE TABLE daily_reactions (
                dateEpochDay INTEGER NOT NULL, authorId INTEGER NOT NULL,
                redHearts INTEGER NOT NULL, pinkHearts INTEGER NOT NULL,
                dayEmoji TEXT, revision INTEGER NOT NULL, updatedAtMillis INTEGER NOT NULL,
                PRIMARY KEY(dateEpochDay, authorId))"""
            )
            old.execSQL(
                """INSERT INTO calendar_events VALUES
                ('keep', 'Пара', 'important', 100, 200, 3, 2, 9, 30, 40, 0, 1),
                ('deleted', 'gone', '', 200, 300, 2, 2, 5, 20, 70, 1, 1)"""
            )
            old.execSQL(
                """INSERT INTO daily_reactions VALUES
                (20395,1,123456,100000,'🥰',999,1234),
                (20395,2,4,5,'😴',10,1235)"""
            )
            old.version = 2
        }
        val db = Room.databaseBuilder(context, CalendarDatabase::class.java, name)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .build()
        try {
            assertEquals("important", db.events().byId("keep")?.notes)
            assertEquals(9L, db.events().byId("keep")?.revision)
            assertTrue(db.events().byId("deleted")!!.deleted)
            assertEquals(123456L, db.reactions().get(20395, Authors.SASHA)?.redHearts)
            assertEquals("🥰", db.reactions().get(20395, Authors.SASHA)?.dayEmoji)
            assertEquals("😴", db.reactions().get(20395, Authors.NASTYA)?.dayEmoji)
            assertEquals(1, db.reactions().get(20395, Authors.SASHA)?.heartChoice)
            assertEquals(2, db.reactions().get(20395, Authors.NASTYA)?.heartChoice)
            assertEquals(0, db.sync().conflictCount())
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
