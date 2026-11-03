package com.rteats.calendarik.ui

import org.junit.Assert.*
import org.junit.Test

class ReactionPickerCatalogTest {
    @Test fun curatedPickerHasOnlyApprovedIconsWithoutCategories() {
        val ids = MoodCatalog.choices.map { it.id }
        assertEquals(22, ids.size)
        assertEquals(ids.size, ids.distinct().size)
        assertTrue("fa:poop" in ids)
        assertTrue("fa:poo-storm" in ids)
        assertFalse("fa:poo" in ids)
        assertFalse("fa:ghost" in ids)
        assertFalse("fa:face-grin-wide" in ids)
    }

    @Test fun allSelectedIconsHaveMatchingDrawable() {
        MoodCatalog.choices.forEach { assertNotNull(it.id, MoodCatalog.resource(it.id)) }
    }
}
