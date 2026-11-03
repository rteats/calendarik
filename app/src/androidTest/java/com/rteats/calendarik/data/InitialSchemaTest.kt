package com.rteats.calendarik.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InitialSchemaTest {
    @Test fun freshSchemaSupportsPersonalAndSharedEvents() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, CalendarDatabase::class.java).build()
        try {
            db.sharedHearts().setHeart(42, Authors.SASHA, 5, 12)
            assertEquals(5, db.sharedHearts().get(42)?.heartChoice)
            db.sharedHearts().setHeart(42, Authors.NASTYA, 3, 14)
            assertEquals(3, db.sharedHearts().get(42)?.heartChoice)
            assertEquals(2, db.sharedHearts().get(42)?.lastEditor)
        } finally { db.close() }
    }
}
