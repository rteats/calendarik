package com.rteats.calendarik.widget

import org.junit.Assert.*
import org.junit.Test

class WidgetLayoutTest {
    @Test fun oneRowShowsDateNavigationWithoutOverflow() {
        assertEquals(WidgetLayout(false, false, 0, 0), widgetLayout(150, 60))
    }

    @Test fun contentAppearsAsSpaceGrows() {
        assertEquals(WidgetLayout(false, true, 0, 0), widgetLayout(170, 180))
        assertEquals(WidgetLayout(false, true, 1, 0), widgetLayout(110, 202))
        assertEquals(WidgetLayout(false, true, 1, 0), widgetLayout(170, 202))
        assertEquals(WidgetLayout(true, true, 1, 0), widgetLayout(200, 250))
        assertEquals(WidgetLayout(true, true, 1, 1), widgetLayout(300, 350))
        assertEquals(WidgetLayout(true, true, 3, 2), widgetLayout(300, 500))
    }
}
