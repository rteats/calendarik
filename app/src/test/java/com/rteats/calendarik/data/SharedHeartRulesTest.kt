package com.rteats.calendarik.data

import org.junit.Assert.*
import org.junit.Test

class SharedHeartRulesTest {
    @Test fun higherRevisionWinsEvenWhenEditedByOtherPerson() {
        val current = SharedHeart(42, 1, 4, Authors.NASTYA, 100)
        val next = SharedHeart(42, 2, 5, Authors.SASHA, 90)
        assertTrue(SharedHeartRules.shouldAccept(current, next))
        assertFalse(SharedHeartRules.shouldAccept(next, current))
    }

    @Test fun concurrentOfflineChangesConvergeOnSameValue() {
        val sasha = SharedHeart(42, 1, 5, Authors.SASHA, 100)
        val nastya = SharedHeart(42, 2, 5, Authors.NASTYA, 99)
        assertTrue(SharedHeartRules.shouldAccept(sasha, nastya))
        assertFalse(SharedHeartRules.shouldAccept(nastya, sasha))
    }

    @Test fun clearingHeartIsARealChangeThatSynchronizes() {
        val current = SharedHeart(42, 1, 4, Authors.SASHA, 100)
        val cleared = SharedHeart(42, 0, 5, Authors.NASTYA, 200)
        assertTrue(SharedHeartRules.shouldAccept(current, cleared))
        assertFalse(SharedHeartRules.shouldAccept(cleared, current))
    }

    @Test fun replayIsIdempotent() {
        val same = SharedHeart(42, 2, 7, Authors.SASHA, 100)
        assertFalse(SharedHeartRules.shouldAccept(same, same))
        assertTrue(SharedHeartRules.shouldAccept(null, same))
    }
}
