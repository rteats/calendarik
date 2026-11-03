package com.rteats.calendarik.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class DailyReactionRulesTest {
    @Test fun retainsEmojiSequences() {
        assertEquals("🥰", DailyReactionRules.sanitizeMood(" 🥰 "))
        assertEquals("👩‍❤️‍👨", DailyReactionRules.sanitizeMood("👩‍❤️‍👨"))
    }

    @Test fun supportsFontAwesomeIds() {
        assertEquals("fa:poo-storm", DailyReactionRules.sanitizeMood(" fa:poo-storm "))
        assertEquals("fa:face-grin-squint-tears",
            DailyReactionRules.sanitizeMood("fa:face-grin-squint-tears"))
        assertThrows(IllegalArgumentException::class.java) {
            DailyReactionRules.sanitizeMood("fa:invalid icon")
        }
    }

    @Test fun emptyMoodClearsTheSlot() {
        assertNull(DailyReactionRules.sanitizeMood("  "))
        assertNull(DailyReactionRules.sanitizeMood(null))
    }

    @Test fun rejectsLongMood() {
        assertThrows(IllegalArgumentException::class.java) {
            DailyReactionRules.sanitizeMood("а".repeat(40))
        }
    }

    @Test fun dayKeyIsStable() {
        val day = LocalDate.of(2026, 11, 3)
        val r = DailyReaction(day.toEpochDay(), Authors.SASHA, redHearts = 10_000L)
        assertEquals(day, r.day)
        assertEquals(10_000L, r.redHearts)
    }

    @Test fun legacyHeartKeepsDeterministicWinner() {
        assertEquals(0, DailyReactionRules.legacyHeart(0, 0))
        assertEquals(1, DailyReactionRules.legacyHeart(9, 3))
        assertEquals(2, DailyReactionRules.legacyHeart(5, 8))
        assertEquals(1, DailyReactionRules.legacyHeart(2, 2))
        assertFalse(DailyReactionRules.isValidHeart(3))
    }

    @Test fun rejectsInvalidOwner() {
        assertThrows(IllegalArgumentException::class.java) {
            DailyReactionRules.validateOwner(Authors.BOTH)
        }
    }
}

