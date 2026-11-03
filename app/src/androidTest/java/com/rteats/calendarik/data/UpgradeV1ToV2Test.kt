package com.rteats.calendarik.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Opens an actual SQLite v1 file with old data and checks a v2 Room upgrade. */
@RunWith(AndroidJUnit4::class)
class UpgradeV1ToV2Test {
    @Test fun v1CalendarAndTombstoneArePreserved() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "calendarik-migration-test.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            old.execSQL(
                """CREATE TABLE IF NOT EXISTS calendar_events (
                id TEXT NOT NULL PRIMARY KEY,
                title TEXT NOT NULL,
                notes TEXT NOT NULL,
                startsAtMillis INTEGER NOT NULL,
                endsAtMillis INTEGER NOT NULL,
                authorMask INTEGER NOT NULL,
                lastEditor INTEGER NOT NULL,
                revision INTEGER NOT NULL,
                createdAtMillis INTEGER NOT NULL,
                updatedAtMillis INTEGER NOT NULL,
                deleted INTEGER NOT NULL,
                payloadVersion INTEGER NOT NULL
                )"""
            )
            old.execSQL("CREATE INDEX index_calendar_events_startsAtMillis ON calendar_events (startsAtMillis)")
            old.execSQL("CREATE INDEX index_calendar_events_updatedAtMillis ON calendar_events (updatedAtMillis)")
            old.execSQL(
                """INSERT INTO calendar_events
                (id,title,notes,startsAtMillis,endsAtMillis,authorMask,lastEditor,
                 revision,createdAtMillis,updatedAtMillis,deleted,payloadVersion)
                VALUES ('old','Preserve me','notes',10,20,1,1,9,1,4,0,1),
                       ('gone','tombstone','',30,40,3,2,10,2,5,1,1)"""
            )
            old.version = 1
        }
        val db = Room.databaseBuilder(context, CalendarDatabase::class.java, name)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .build()
        try {
            assertEquals("Preserve me", db.events().byId("old")?.title)
            assertEquals(9L, db.events().byId("old")?.revision)
            assertTrue(db.events().byId("gone")!!.deleted)
            db.reactions().setHeart(20395, Authors.SASHA, HeartColor.RED, 100)
            assertEquals(1, db.reactions().get(20395, Authors.SASHA)?.heartChoice)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun heartIsExclusiveAndMoodReplacesInSameDay() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, CalendarDatabase::class.java).build()
        try {
            val dao = db.reactions()
            repeat(100) { dao.setHeart(123, Authors.SASHA, HeartColor.RED) }
            dao.setHeart(123, Authors.NASTYA, HeartColor.PINK)
            dao.setMood(123, Authors.SASHA, "😍")
            dao.setMood(123, Authors.SASHA, "😴")
            assertEquals(1, dao.get(123, Authors.SASHA)?.heartChoice)
            assertEquals("😴", dao.get(123, Authors.SASHA)?.dayEmoji)
            assertEquals(2, dao.get(123, Authors.NASTYA)?.heartChoice)
            assertEquals(0L, dao.get(123, Authors.NASTYA)?.redHearts)
        } finally { db.close() }
    }
}

