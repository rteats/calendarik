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
            db.reactions().setHeart(42, Authors.SASHA, HeartColor.PINK, 12)
            assertEquals(2, db.reactions().get(42, Authors.SASHA)?.heartChoice)
        } finally { db.close() }
    }
}
