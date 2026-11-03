package com.rteats.calendarik.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class EventRulesTest {
    private val date = LocalDate.of(2026, 11, 3)
    private val zone = ZoneId.of("Europe/Moscow")

    @Test fun onlyAuthorMayChangePersonalEvent() {
        val event = createOrEditEvent(null, Authors.SASHA, "Учёба", "", date, LocalTime.of(9, 0),
            LocalTime.of(10, 0), Authors.SASHA, 100L, zone)
        assertTrue(Authors.hasPermission(event.authorMask, Authors.SASHA))
        assertFalse(Authors.hasPermission(event.authorMask, Authors.NASTYA))
        assertThrows(IllegalArgumentException::class.java) {
            deleteEvent(event, Authors.NASTYA)
        }
    }

    @Test fun bothCanEditSharedEventAndIdIsStable() {
        val original = createOrEditEvent(null, Authors.SASHA, "Встреча", "", date, LocalTime.NOON,
            LocalTime.of(13, 0), Authors.BOTH, 100L, zone)
        val modified = createOrEditEvent(original, Authors.NASTYA, "Поездка", "", date, LocalTime.NOON,
            LocalTime.of(14, 0), Authors.BOTH, 200L, zone)
        assertEquals(original.id, modified.id)
        assertEquals(2L, modified.revision)
        assertEquals(Authors.NASTYA, modified.lastEditor)
    }

    @Test fun deletionLeavesTombstoneToSynchronizeLater() {
        val original = createOrEditEvent(null, Authors.SASHA, "Встреча", "", date, LocalTime.NOON,
            LocalTime.of(13, 0), Authors.SASHA, 100L, zone)
        val deleted = deleteEvent(original, Authors.SASHA, 300L)
        assertTrue(deleted.deleted)
        assertEquals(original.id, deleted.id)
        assertEquals(original.revision + 1, deleted.revision)
    }

    @Test fun rejectsInvertedTimes() {
        assertThrows(IllegalArgumentException::class.java) {
            createOrEditEvent(null, Authors.SASHA, "Oops", "", date, LocalTime.of(15, 0),
                LocalTime.of(14, 0), Authors.SASHA, 100L, zone)
        }
    }

    @Test fun privateEventCannotSilentlyGainMoreAuthors() {
        val original = createOrEditEvent(null, Authors.SASHA, "Пара", "", date, LocalTime.NOON,
            LocalTime.of(13, 0), Authors.SASHA, 100L, zone)
        assertThrows(IllegalArgumentException::class.java) {
            createOrEditEvent(original, Authors.SASHA, "Пара", "", date, LocalTime.NOON,
                LocalTime.of(13, 0), Authors.BOTH, 200L, zone)
        }
    }
}

