package com.rteats.calendarik

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class DateWindowTest {
    @Test fun weekBeginsOnMonday() {
        val selected = LocalDate.of(2026, 11, 3)
        val window = dateWindow(selected, CalendarView.WEEK)
        assertEquals(LocalDate.of(2026, 11, 2), window.start)
        assertEquals(LocalDate.of(2026, 11, 9), window.endExclusive)
    }

    @Test fun monthContainsAllGridWeeks() {
        val selected = LocalDate.of(2026, 11, 3)
        val window = dateWindow(selected, CalendarView.MONTH)
        assertEquals(LocalDate.of(2026, 10, 26), window.start)
        assertEquals(LocalDate.of(2026, 12, 7), window.endExclusive)
    }

    @Test fun dayShowsOneCalendarDay() {
        val window = dateWindow(LocalDate.of(2026, 12, 31), CalendarView.DAY)
        assertEquals(LocalDate.of(2026, 12, 31), window.start)
        assertEquals(LocalDate.of(2027, 1, 1), window.endExclusive)
    }

    @Test fun agendaShowsContinuousThirtyOneDays() {
        val window = dateWindow(LocalDate.of(2026, 11, 3), CalendarView.AGENDA)
        assertEquals(LocalDate.of(2026, 12, 4), window.endExclusive)
    }
}
