package com.rteats.calendarik.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InitialSchemaTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        CalendarDatabase::class.java,
    )

    @Test
    fun versionOneSchemaCanBeOpenedAndValidated() {
        helper.createDatabase("test-schema", 1).use { db ->
            db.execSQL(
                """INSERT INTO calendar_events
                (id, title, notes, startsAtMillis, endsAtMillis, authorMask,
                lastEditor, revision, createdAtMillis, updatedAtMillis, deleted, payloadVersion)
                VALUES ('uuid1', 'Original', '', 100, 200, 3, 1, 1, 50, 50, 0, 1)"""
            )
        }
        helper.runMigrationsAndValidate("test-schema", 1, true)
    }
}

