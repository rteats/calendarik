package com.rteats.calendarik.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpgradeV3ToV4Test {
    @Test fun preservesOldHeartsEmojisEventsAndConflicts() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "calendarik-v3-v4-test.db"
        context.deleteDatabase(name)
        val path = context.getDatabasePath(name)
        path.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            db.execSQL("""CREATE TABLE calendar_events (
                id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, notes TEXT NOT NULL,
                startsAtMillis INTEGER NOT NULL, endsAtMillis INTEGER NOT NULL,
                authorMask INTEGER NOT NULL, lastEditor INTEGER NOT NULL,
                revision INTEGER NOT NULL, createdAtMillis INTEGER NOT NULL,
                updatedAtMillis INTEGER NOT NULL, deleted INTEGER NOT NULL,
                payloadVersion INTEGER NOT NULL)""")
            db.execSQL("CREATE INDEX index_calendar_events_startsAtMillis ON calendar_events (startsAtMillis)")
            db.execSQL("CREATE INDEX index_calendar_events_updatedAtMillis ON calendar_events (updatedAtMillis)")
            db.execSQL("""CREATE TABLE daily_reactions (
                dateEpochDay INTEGER NOT NULL, authorId INTEGER NOT NULL,
                redHearts INTEGER NOT NULL, pinkHearts INTEGER NOT NULL,
                dayEmoji TEXT, revision INTEGER NOT NULL, updatedAtMillis INTEGER NOT NULL,
                PRIMARY KEY(dateEpochDay, authorId))""")
            db.execSQL("CREATE TABLE dirty_shared_events (eventId TEXT NOT NULL PRIMARY KEY)")
            db.execSQL("""CREATE TABLE sync_conflicts (
                eventId TEXT NOT NULL PRIMARY KEY, remoteJson TEXT NOT NULL,
                detectedAtMillis INTEGER NOT NULL)""")
            db.execSQL("CREATE INDEX index_sync_conflicts_eventId ON sync_conflicts (eventId)")
            db.execSQL("""INSERT INTO calendar_events VALUES
                ('deleted', 'old', 'notes', 10, 11, 1, 1, 8, 1, 2, 1, 1)""")
            db.execSQL("""INSERT INTO daily_reactions VALUES
                (20395,1,3,8,'💩',14,55),
                (20395,2,2,2,'🥰',16,67),
                (20396,1,0,0,NULL,17,68)""")
            db.execSQL("INSERT INTO dirty_shared_events VALUES ('old-shared')")
            db.execSQL("INSERT INTO sync_conflicts VALUES ('old-shared', '{}', 20)")
            db.version = 3
        }
        val database = Room.databaseBuilder(context, CalendarDatabase::class.java, name)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .build()
        try {
            assertTrue(database.events().byId("deleted")!!.deleted)
            assertEquals("notes", database.events().byId("deleted")?.notes)
            val sasha = database.reactions().get(20395, Authors.SASHA)!!
            assertEquals(3L, sasha.redHearts)
            assertEquals(8L, sasha.pinkHearts)
            assertEquals("💩", sasha.dayEmoji)
            assertEquals(2, sasha.heartChoice)
            assertEquals(14L, sasha.revision)
            assertEquals(1, database.reactions().get(20395, Authors.NASTYA)?.heartChoice)
            assertEquals(0, database.reactions().get(20396, Authors.SASHA)?.heartChoice)
            assertTrue(database.sync().isDirty("old-shared"))
            assertEquals(1, database.sync().conflictCount())
            database.reactions().setHeart(20395, Authors.SASHA, HeartColor.RED, 101)
            assertEquals(1, database.reactions().get(20395, Authors.SASHA)?.heartChoice)
            assertEquals(15L, database.reactions().get(20395, Authors.SASHA)?.revision)
            // Previous counters remain untouched after changing the new selection.
            assertEquals(8L, database.reactions().get(20395, Authors.SASHA)?.pinkHearts)
            database.reactions().setHeart(20395, Authors.SASHA, null, 102)
            assertEquals(0, database.reactions().get(20395, Authors.SASHA)?.heartChoice)
            assertEquals("💩", database.reactions().get(20395, Authors.SASHA)?.dayEmoji)
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun onlyOneSharedHeartPerDayAndIdempotentWrite() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, CalendarDatabase::class.java).build()
        try {
            val dao = database.sharedHearts()
            dao.setHeart(42, Authors.SASHA, HeartColor.RED, 100)
            dao.setHeart(42, Authors.NASTYA, HeartColor.PINK, 200)
            dao.setHeart(42, Authors.SASHA, HeartColor.PINK, 300)
            assertEquals(2, dao.get(42)?.heartChoice)
            assertEquals(2L, dao.get(42)?.revision)
            assertEquals(Authors.NASTYA, dao.get(42)?.lastEditor)
            dao.setHeart(42, Authors.SASHA, null, 400)
            assertEquals(0, dao.get(42)?.heartChoice)
            assertEquals(3L, dao.get(42)?.revision)
        } finally { database.close() }
    }
}
