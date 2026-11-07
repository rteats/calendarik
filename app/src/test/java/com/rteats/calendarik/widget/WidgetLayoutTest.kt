package com.rteats.calendarik.widget

import com.rteats.calendarik.data.CalendarEvent
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class WidgetLayoutTest {
    @Test fun oneRowShowsDateNavigationWithoutOverflow() {
        assertEquals(WidgetLayout(false, false, false, 0), widgetLayout(150, 60))
    }

    @Test fun contentAppearsAsSpaceGrows() {
        assertEquals(WidgetLayout(false, true, false, 0), widgetLayout(170, 180))
        assertEquals(WidgetLayout(false, true, true, 0), widgetLayout(110, 202))
        assertEquals(WidgetLayout(false, true, true, 0), widgetLayout(170, 202))
        assertEquals(WidgetLayout(true, true, true, 0), widgetLayout(200, 250))
        assertEquals(WidgetLayout(true, true, true, 1), widgetLayout(300, 350))
        assertEquals(WidgetLayout(true, true, true, 2), widgetLayout(300, 500))
    }

    @Test fun upcomingDaysKeepEveryEventOnEachShownDate() {
        val zone = ZoneId.of("Europe/Moscow")
        val first = LocalDate.of(2026, 11, 8)
        fun event(id: String, day: LocalDate, hour: Int) = CalendarEvent(
            id = id, title = id, notes = "",
            startsAtMillis = day.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli(),
            endsAtMillis = day.atTime(hour + 1, 0).atZone(zone).toInstant().toEpochMilli(),
            authorMask = 1, lastEditor = 1, revision = 1, createdAtMillis = 0,
            updatedAtMillis = 0, deleted = false, payloadVersion = 1,
        )
        val events = listOf(
            event("later", first.plusDays(1), 10),
            event("third", first, 15),
            event("first", first, 9),
            event("second", first, 12),
            event("excluded", first.plusDays(2), 10),
        )

        val days = upcomingEventDays(events, zone, 2)

        assertEquals(listOf(first, first.plusDays(1)), days.map { it.first })
        assertEquals(listOf("first", "second", "third"), days[0].second.map { it.id })
        assertEquals(listOf("later"), days[1].second.map { it.id })
    }
}
